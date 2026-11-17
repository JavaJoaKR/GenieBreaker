package kr.javajoa.geniebreaker

import android.Manifest
import android.bluetooth.*
import android.companion.*
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/** One Flutter surface owns all application UI and remote focus. Android owns device APIs only. */
class MainActivity : FlutterActivity() {
    private var channel: MethodChannel? = null
    private var menus: MenuController? = null
    private var adapter: BluetoothAdapter? = null
    private val found = linkedMapOf<String, BluetoothDevice>()
    private val linked = mutableSetOf<String>()
    private val names = DeviceNames()
    private var pendingCompanionAddress: String? = null
    private lateinit var bluetoothActions: BluetoothActions
    private lateinit var apps: AppManagementController
    private lateinit var files: FileController
    private lateinit var updater: AppUpdater
    private var updateLabel = "업데이트 확인"
    private var btMessage = "연결할 기기를 선택하세요"
    private var registered = false
    private var preview = false
    private var scanExpired = false
    private var watchdogScheduled = false
    private var searched = false
    private var pendingNames = false
    private var presentedError: String? = null
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scanWatchdog = Runnable {
        watchdogScheduled = false; scanExpired = true
        try { adapter?.cancelDiscovery() } catch (error: Exception) { operationError("검색 중지", error) }
        message("검색을 마쳤습니다. 기기를 선택하거나 다시 검색하세요.")
    }
    private val nameTick = object : Runnable {
        override fun run() {
            if (!registered || preview) return
            renderDevices()
            if (pendingNames) uiHandler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        preview = BuildConfig.DEBUG && intent.getBooleanExtra("ui_preview", false)
        Diagnostics.install(applicationContext)
        adapter = getSystemService(BluetoothManager::class.java)?.adapter
        bluetoothActions = BluetoothActions(this) { text ->
            message(text)
            if (ErrorCodes.isError(text)) TvMenus.notice(this, text)
        }
        apps = AppManagementController(this) { publish() }
        files = FileController(this) { channel?.invokeMethod("filesChanged", null) }
        updater = AppUpdater(this) { text, _ -> updateLabel = text; publish() }
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        fun disableNativeFocusOverlay(view: android.view.View) {
            view.setDefaultFocusHighlightEnabled(false)
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) disableNativeFocusOverlay(view.getChildAt(index))
        }
        disableNativeFocusOverlay(window.decorView)
        pendingCompanionAddress = savedInstanceState?.getString("companion_address")
        savedInstanceState?.getBundle("names")?.let { bundle -> bundle.keySet().forEach { names.merge(it, bundle.getString(it)) } }
        if (!preview) updater.checkForUpdate(automatic = true)
        val prefs = getSharedPreferences("local_diagnostics", MODE_PRIVATE)
        prefs.getString("last_crash", null)?.let { code ->
            prefs.edit().remove("last_crash").apply()
            uiHandler.post { TvMenus.notice(this, "이전 실행이 종료됐습니다. 오류 코드 [$code]") }
        }
    }
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "genie/ui")
        menus = MenuController(this, { page -> channel?.invokeMethod("page", page) }, { channel?.invokeMethod("dismiss", null) })
        TvMenus.attach(this, menus!!)
        channel!!.setMethodCallHandler { call, result ->
            try {
                if (call.method != "ready" && menus?.handle(call, result) == true) return@setMethodCallHandler
                if (files.handle(call, result)) return@setMethodCallHandler
                when (call.method) {
                    "ready" -> { result.success(snapshot()); apps.refresh() }
                    "refreshShell" -> { refresh(); apps.refresh(); result.success(null) }
                    "scan" -> { discover(); result.success(null) }
                    "permission" -> { requestAccess(); result.success(null) }
                    "power" -> { enableBluetooth(); result.success(null) }
                    "device" -> { device(call.arguments as String)?.let(::pair); result.success(null) }
                    "app" -> { apps.choose(call.arguments as String); result.success(null) }
                    "appFilter" -> { apps.toggleDuals(); result.success(null) }
                    "appView" -> { apps.viewOptions(); result.success(null) }
                    "update" -> { updater.click(); result.success(null) }
                    "diagnostics" -> { showDiagnostics(); result.success(null) }
                    "appSettings" -> { openAppSettings(); result.success(null) }
                    "exit" -> { finish(); result.success(null) }
                    else -> result.notImplemented()
                }
            } catch (error: Exception) {
                val code = Diagnostics.record(this, "UI request", error)
                result.error("request", "요청을 처리하지 못했습니다. [$code]", null)
            }
        }
    }
    private fun device(address: String) = found[address] ?: if (access()) adapter?.bondedDevices?.firstOrNull { it.address == address } else null
    private fun snapshot(): Map<String, Any> {
        val devices = mutableListOf<Map<String, Any>>()
        pendingNames = false
        if (preview) {
            listOf("거실 헤드폰", "무선 키보드", "WH-1000XM5", "이름 확인 중", "Bluetooth Keyboard").forEachIndexed { index, name ->
                devices += mapOf("id" to "preview-$index", "name" to name, "bonded" to (index < 2), "kind" to if (index == 1 || index == 4) "input" else "audio", "detail" to if (index < 2) "등록됨" else "페어링 가능")
            }
        } else if (access()) try {
            val bonded = adapter?.bondedDevices.orEmpty()
            (bonded + found.values).distinctBy { it.address }.forEach { device ->
                names.seed(device.address, device.name)
                pendingNames = pendingNames || names.isPending(device.address)
                devices += mapOf("id" to device.address, "name" to (names.get(device.address) ?: if (names.isPending(device.address)) "이름 확인 중" else "이름 없는 기기"),
                    "bonded" to (device.bondState == BluetoothDevice.BOND_BONDED),
                    "kind" to when (device.bluetoothClass?.majorDeviceClass) { BluetoothClass.Device.Major.AUDIO_VIDEO -> "audio"; BluetoothClass.Device.Major.PERIPHERAL -> "input"; else -> "bluetooth" },
                    "detail" to when { device.bondState == BluetoothDevice.BOND_BONDING -> "페어링 중"; device.address in linked -> "기기 링크 연결됨"; device.bondState == BluetoothDevice.BOND_BONDED -> "등록됨"; else -> "페어링 가능" })
            }
        } catch (error: Exception) { Diagnostics.record(this, "Bluetooth snapshot", error) }
        val enabled = preview || try { access() && adapter?.isEnabled == true } catch (_: SecurityException) { false }
        val searching = preview || try { access() && adapter?.isDiscovering == true && !scanExpired } catch (_: SecurityException) { false }
        return mapOf("shell" to true, "version" to BuildConfig.VERSION_NAME, "devices" to devices,
            "permission" to (preview || access()), "enabled" to enabled, "supported" to (preview || adapter != null),
            "searching" to searching, "searched" to searched, "status" to btMessage, "apps" to apps.snapshot(), "update" to updateLabel) + (menus?.current()?.let { mapOf("menu" to it) } ?: emptyMap())
    }
    private fun publish() { if (!isFinishing && !isDestroyed && ::apps.isInitialized) channel?.invokeMethod("shell", snapshot()) }
    private fun message(text: String) {
        btMessage = if (ErrorCodes.isError(text) && !text.contains("[GB-")) "$text [${Diagnostics.record(this, "Bluetooth: $text")}]" else text
        publish()
    }
    private fun renderDevices() = publish()
    private fun refresh() {
        if (!preview && access()) try {
            val searching = adapter?.isDiscovering == true && !scanExpired
            if (searching && !watchdogScheduled) { watchdogScheduled = true; uiHandler.postDelayed(scanWatchdog, 35000) }
            if (!searching) { uiHandler.removeCallbacks(scanWatchdog); watchdogScheduled = false }
        } catch (error: Exception) { Diagnostics.record(this, "Bluetooth refresh", error) }
        publish()
        uiHandler.removeCallbacks(nameTick); if (pendingNames) uiHandler.postDelayed(nameTick, 1000)
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!access()) { refresh(); return }
            try {
                @Suppress("DEPRECATION")
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (intent.action == BluetoothDevice.ACTION_FOUND) found[it.address] = it
                    names.merge(it.address, intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: it.name)
                    when (intent.action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> linked.add(it.address)
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> linked.remove(it.address)
                        BluetoothDevice.ACTION_BOND_STATE_CHANGED -> message(when (it.bondState) { BluetoothDevice.BOND_BONDED -> "페어링 완료. 기기에서 연결 상태를 확인하세요."; BluetoothDevice.BOND_BONDING -> "페어링 중입니다. 확인 창을 확인하세요."; else -> "페어링이 취소되었거나 등록이 해제되었습니다." })
                    }
                }
                refresh()
                uiHandler.removeCallbacks(nameTick); uiHandler.postDelayed(nameTick, 1000)
            } catch (error: Exception) { operationError("기기 목록 갱신", error) }
        }
    }
    override fun onStart() {
        super.onStart()
        if (preview) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothDevice.ACTION_CLASS_CHANGED); addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED); addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else registerReceiver(receiver, filter)
        registered = true
    }
    override fun onResume() { super.onResume(); if (::updater.isInitialized) { if (!preview) updater.resume(); apps.resume(); refresh() } }
    override fun onStop() {
        if (registered) { unregisterReceiver(receiver); registered = false }
        uiHandler.removeCallbacks(nameTick); uiHandler.removeCallbacks(scanWatchdog); watchdogScheduled = false
        try { if (access()) adapter?.cancelDiscovery() } catch (_: SecurityException) { }
        super.onStop()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("companion_address", pendingCompanionAddress)
        out.putBundle("names", Bundle().apply { names.snapshot().forEach { (key, value) -> putString(key, value) } })
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() {
        TvMenus.detach(this); menus?.clear(); channel?.setMethodCallHandler(null)
        if (::updater.isInitialized) updater.close()
        if (::apps.isInitialized) apps.close()
        if (::files.isInitialized) files.close()
        if (::bluetoothActions.isInitialized) bluetoothActions.close()
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); refresh()
    }
    @Deprecated("Platform result callback for TV firmware compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (files.result(requestCode, resultCode, data)) return
        if (requestCode == 610) { apps.exportResult(resultCode, data); return }
        if (requestCode != 501) return
        val address = pendingCompanionAddress; pendingCompanionAddress = null
        if (resultCode != RESULT_OK || address == null) { message("대체 페어링이 취소되었습니다."); return }
        try { adapter?.getRemoteDevice(address)?.let { found[address] = it; bluetoothActions.bond(it) } }
        catch (error: Exception) { operationError("대체 페어링 완료", error) }
    }
    private fun openAppSettings() {
        for (action in listOf(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)) {
            try { startActivity(Intent(action)); return } catch (_: ActivityNotFoundException) { }
        }
        TvMenus.notice(this, "이 TV 펌웨어는 앱 목록 설정 화면을 제공하지 않습니다. [GB-UI-0001]")
    }
    private fun showDiagnostics() {
        val text = Diagnostics.read(this)
        TvMenus.show(this, "문제 기록 · 기기 내부", listOf("기록 복사", "기록 지우기"), text) { which ->
            if (which == 0) getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("GenieBreaker local errors", text))
            else TvMenus.show(this, "문제 기록 지우기", listOf("취소", "기록 지우기"), "기기에 저장된 오류 코드를 지웁니다.") { if (it == 1) Diagnostics.clear(this) }
        }
    }
    private fun operationError(operation: String, error: Exception) {
        val code = Diagnostics.record(this, operation, error)
        message("$operation 실패 [$code]")
        if (presentedError != code && !isFinishing && !isDestroyed) { presentedError = code; TvMenus.notice(this, "$operation 실패 [$code]") }
    }
    private fun access() = if (Build.VERSION.SDK_INT >= 31) {
        checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun requestAccess() {
        try { requestPermissions(if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("권한 요청", error) }
    }
    private fun enableBluetooth() {
        val bt = adapter ?: run { message("블루투스를 지원하지 않습니다"); return }
        if (!access()) { requestAccess(); return }
        try {
            if (bt.isEnabled) { message("이미 켜져 있습니다"); return }
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: ActivityNotFoundException) { message("펌웨어에서 블루투스 켜기 요청을 지원하지 않습니다") }
        catch (_: SecurityException) { message("블루투스 켜기 요청이 거부되었습니다") }
    }
    private fun discover() {
        val bt = adapter ?: return
        if (!access()) { requestAccess(); return }
        try {
            if (!bt.isEnabled) { enableBluetooth(); return }
            if (bt.isDiscovering) { bt.cancelDiscovery(); return }
            if (Build.VERSION.SDK_INT < 31) {
                val location = getSystemService(LocationManager::class.java)
                if (location == null || !(location.isProviderEnabled(LocationManager.GPS_PROVIDER) || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
                    message("위치 서비스가 꺼져 있어 이 Android 버전에서 검색할 수 없습니다"); return
                }
            }
            found.clear(); searched = true; scanExpired = false; renderDevices()
            if (bt.startDiscovery()) {
                uiHandler.removeCallbacks(scanWatchdog); watchdogScheduled = true; uiHandler.postDelayed(scanWatchdog, 35_000)
                refresh()
            } else message("검색을 시작하지 못했습니다. 잠시 후 다시 시도하세요.")
        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("기기 검색", error) }
    }
    private fun pair(device: BluetoothDevice) {
        if (!access()) { requestAccess(); return }
        try {
            val title = names.get(device.address) ?: device.name ?: "이름 확인 중"
            val bonded = device.bondState == BluetoothDevice.BOND_BONDED
            val actions = if (bonded) arrayOf("연결", "연결 해제", "기기 지우기", "상세 정보")
                else arrayOf("페어링", "다른 페어링 방식", "기기 정보")
            TvMenus.show(this, title, actions.toList()) { index ->
                try {
                    if (bonded) when (index) {
                        0 -> bluetoothActions.connection(device, true)
                        1 -> bluetoothActions.connection(device, false)
                        2 -> TvMenus.show(this, "등록을 삭제할까요?", listOf("취소", "삭제"), "리모컨이나 키보드를 삭제하면 조작할 수 없게 될 수 있습니다. 다른 입력 수단을 준비하세요.") { choice -> if (choice == 1) bluetoothActions.forget(device) }
                        else -> deviceDetails(device)
                    } else when (index) {
                        0 -> {
                            adapter?.cancelDiscovery()
                            if (device.bondState == BluetoothDevice.BOND_BONDING) message("이미 페어링 중입니다.") else bluetoothActions.bond(device)
                        }
                        1 -> companionPair(device)
                        else -> deviceDetails(device)
                    }
                } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("기기 선택", error) }
            }
        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("기기 선택", error) }
    }
    private fun deviceDetails(device: BluetoothDevice) {
        try {
            val name = names.get(device.address) ?: device.name ?: "이름 확인 중"
            TvMenus.show(this, name, listOf("닫기"), "${device.address}\n${if (device.bondState == BluetoothDevice.BOND_BONDED) "등록됨" else "등록되지 않음"}\n${if (device.address in linked) "기기 링크 연결됨" else "링크 연결 상태 미확인"}\n\n등록 상태와 오디오·입력 프로필의 실제 연결 상태는 다를 수 있습니다.")

        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("기기 정보", error) }
    }
    @Suppress("DEPRECATION")
    private fun companionPair(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            message("이 펌웨어는 대체 페어링 서비스를 제공하지 않습니다."); return
        }
        try {
            adapter?.cancelDiscovery()
            pendingCompanionAddress = device.address
            val manager = getSystemService(CompanionDeviceManager::class.java)
            val request = AssociationRequest.Builder().addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(device.address).build()).setSingleDevice(true).build()
            val callback = object : CompanionDeviceManager.Callback() {
                private fun chooser(sender: IntentSender) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) try { startIntentSenderForResult(sender, 501, null, 0, 0, 0) }
                        catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("대체 페어링 화면", error) }
                    }
                }
                override fun onDeviceFound(chooserLauncher: IntentSender) { chooser(chooserLauncher) }
                override fun onAssociationPending(intentSender: IntentSender) { chooser(intentSender) }
                override fun onFailure(error: CharSequence?) { runOnUiThread { if (!isDestroyed) message("대체 페어링 실패: ${error ?: "기기를 찾지 못했습니다"}") } }
            }
            if (Build.VERSION.SDK_INT >= 33) manager.associate(request, mainExecutor, callback)
            else manager.associate(request, callback, null)
            message("대체 페어링 준비 중입니다. 기기를 페어링 모드로 유지하세요.")
        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("대체 페어링", error) }
    }
}
