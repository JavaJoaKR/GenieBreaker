package kr.javajoa.geniebreaker

import android.app.Activity
import android.Manifest
import android.bluetooth.*
import android.companion.*
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle

internal class BluetoothController(
    private val activity: Activity,
    private val preview: Boolean,
    private val publish: () -> Unit
) {
    private val adapter = activity.getSystemService(BluetoothManager::class.java)?.adapter
    private val found = linkedMapOf<String, BluetoothDevice>()
    private val linked = mutableSetOf<String>()
    private val names = DeviceNames()
    private var pendingCompanionAddress: String? = null
    private var companionChooserOpen = false
    private val bluetoothActions = BluetoothActions(activity) { text ->
        message(text)
        if (ErrorCodes.isError(text)) TvMenus.notice(activity, text)
    }
    private var btMessage = "연결할 블루투스 기기를 선택하세요"
    private var registered = false
    private var scanExpired = false
    private var watchdogScheduled = false
    private var searched = false
    private var pendingNames = false
    private var presentedError: String? = null
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scanWatchdog = Runnable {
        watchdogScheduled = false; scanExpired = true
        try { if (access()) adapter?.cancelDiscovery() } catch (error: SecurityException) { operationError("검색 중지", error) } catch (error: Exception) { operationError("검색 중지", error) }
        message("검색을 완료했습니다. 목록에서 기기를 선택하세요.")
    }
    private val nameTick = object : Runnable {
        override fun run() {
            if (!registered || preview) return
            publish()
            if (pendingNames) uiHandler.postDelayed(this, 1000)
        }
    }

    fun restore(saved: Bundle?) {
        pendingCompanionAddress = saved?.getString("companion_address")
        companionChooserOpen = pendingCompanionAddress != null
        saved?.getBundle("names")?.let { bundle ->
            bundle.keySet().forEach { names.merge(it, bundle.getString(it)) }
        }
    }

    fun choose(address: String) { device(address)?.let(::pair) }

    private fun device(address: String): BluetoothDevice? {
        if (!access()) return null
        return try { found[address] ?: adapter?.bondedDevices?.firstOrNull { it.address == address } }
        catch (error: SecurityException) { operationError("기기 접근 권한", error); null }
    }
    fun snapshot(): Map<String, Any> {
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
                devices += mapOf("id" to device.address, "name" to (names.get(device.address) ?: if (names.isPending(device.address)) "이름 확인 중" else "이름을 확인할 수 없는 기기"),
                    "bonded" to (device.bondState == BluetoothDevice.BOND_BONDED),
                    "kind" to when (device.bluetoothClass?.majorDeviceClass) { BluetoothClass.Device.Major.AUDIO_VIDEO -> "audio"; BluetoothClass.Device.Major.PERIPHERAL -> "input"; else -> "bluetooth" },
                    "detail" to when { device.bondState == BluetoothDevice.BOND_BONDING -> "페어링 중"; device.address in linked -> "블루투스 링크 연결됨"; device.bondState == BluetoothDevice.BOND_BONDED -> "등록됨"; else -> "페어링 가능" })
            }
        } catch (error: SecurityException) { Diagnostics.record(activity, "Bluetooth snapshot", error) }
        catch (error: Exception) { Diagnostics.record(activity, "Bluetooth snapshot", error) }
        val enabled = preview || try { access() && adapter?.isEnabled == true } catch (_: SecurityException) { false }
        val searching = preview || try { access() && adapter?.isDiscovering == true && !scanExpired } catch (_: SecurityException) { false }
        return mapOf("devices" to devices,
            "permission" to (preview || access()), "enabled" to enabled, "supported" to (preview || adapter != null),
            "searching" to searching, "searched" to searched, "status" to btMessage)
    }
    private fun message(text: String) {
        btMessage = if (ErrorCodes.isError(text) && !text.contains("[GB-")) "$text [${Diagnostics.record(activity, "Bluetooth: $text")}]" else text
        publish()
    }
    fun refresh() {
        if (!preview && access()) try {
            val searching = adapter?.isDiscovering == true && !scanExpired
            if (searching && !watchdogScheduled) { watchdogScheduled = true; uiHandler.postDelayed(scanWatchdog, 35000) }
            if (!searching) { uiHandler.removeCallbacks(scanWatchdog); watchdogScheduled = false }
        } catch (error: SecurityException) { Diagnostics.record(activity, "Bluetooth refresh", error) }
        catch (error: Exception) { Diagnostics.record(activity, "Bluetooth refresh", error) }
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
                        BluetoothDevice.ACTION_BOND_STATE_CHANGED -> message(when (it.bondState) { BluetoothDevice.BOND_BONDED -> "페어링 완료. 기기에서 연결 상태를 확인하세요."; BluetoothDevice.BOND_BONDING -> "페어링 중입니다. 확인 창이 표시되면 승인하세요."; else -> "페어링이 취소되었거나 등록이 해제되었습니다." })
                    }
                }
                refresh()
                uiHandler.removeCallbacks(nameTick); uiHandler.postDelayed(nameTick, 1000)
            } catch (error: SecurityException) { operationError("기기 목록 갱신", error) }
            catch (error: Exception) { operationError("기기 목록 갱신", error) }
        }
    }
    fun start() {
        if (preview) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothDevice.ACTION_CLASS_CHANGED); addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED); addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else activity.registerReceiver(receiver, filter)
        registered = true
    }
    fun stop() {
        if (registered) { activity.unregisterReceiver(receiver); registered = false }
        uiHandler.removeCallbacks(nameTick); uiHandler.removeCallbacks(scanWatchdog); watchdogScheduled = false
        try { if (access()) adapter?.cancelDiscovery() } catch (_: SecurityException) { }
    }
    fun save(out: Bundle) {
        out.putString("companion_address", pendingCompanionAddress.takeIf { companionChooserOpen })
        out.putBundle("names", Bundle().apply { names.snapshot().forEach { (key, value) -> putString(key, value) } })
    }

    fun close() {
        bluetoothActions.close()
        uiHandler.removeCallbacksAndMessages(null)
    }

    fun result(requestCode: Int, resultCode: Int) {
        if (requestCode != 501) return
        val address = pendingCompanionAddress
        pendingCompanionAddress = null
        companionChooserOpen = false
        if (resultCode != Activity.RESULT_OK || address == null) { message("페어링 요청이 취소되었습니다."); return }
        bondCompanion(address)
    }

    private fun bondCompanion(address: String) {
        try {
            val target = adapter?.getRemoteDevice(address) ?: error("블루투스를 사용할 수 없습니다")
            found[address] = target
            if (target.bondState != BluetoothDevice.BOND_NONE) refresh()
            else bluetoothActions.bond(target)
        } catch (error: SecurityException) {
            operationError("블루투스 권한", error)
        } catch (error: Exception) { operationError("다른 방식으로 페어링 완료", error) }
    }

    private fun operationError(operation: String, error: Exception) {
        val code = Diagnostics.record(activity, operation, error)
        message("$operation 실패 [$code]")
        if (presentedError != code && !activity.isFinishing && !activity.isDestroyed) { presentedError = code; TvMenus.notice(activity, "$operation 실패 [$code]") }
    }
    private fun access() = if (Build.VERSION.SDK_INT >= 31) {
        activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun requestAccess() {
        try { activity.requestPermissions(if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("권한 요청", error) }
    }
    fun enableBluetooth() {
        val bt = adapter ?: run { message("블루투스를 지원하지 않습니다"); return }
        if (!access()) { requestAccess(); return }
        try {
            if (bt.isEnabled) { message("이미 켜져 있습니다"); return }
            activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: ActivityNotFoundException) { message("TV 설정에서 블루투스를 켜세요") }
        catch (_: SecurityException) { message("TV가 블루투스 활성화 요청을 거부했습니다") }
    }
    fun discover() {
        val bt = adapter ?: return
        if (!access()) { requestAccess(); return }
        try {
            if (!bt.isEnabled) { enableBluetooth(); return }
            if (bt.isDiscovering) { bt.cancelDiscovery(); return }
            if (Build.VERSION.SDK_INT < 31) {
                val location = activity.getSystemService(LocationManager::class.java)
                if (location == null || !(location.isProviderEnabled(LocationManager.GPS_PROVIDER) || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
                    message("이 Android 버전에서 블루투스 기기를 검색하려면 위치 서비스를 켜세요"); return
                }
            }
            found.clear(); searched = true; scanExpired = false; publish()
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
            val actions = if (bonded) arrayOf("연결", "연결 해제", "기기 등록 삭제", "상세 정보")
                else arrayOf("페어링", "다른 방식으로 페어링", "기기 정보")
            TvMenus.show(activity, title, actions.toList()) { index ->
                try {
                    if (bonded) when (index) {
                        0 -> bluetoothActions.connection(device, true)
                        1 -> bluetoothActions.connection(device, false)
                        2 -> TvMenus.show(activity, "기기 등록 삭제 확인", listOf("취소", "삭제"), "등록 삭제 후 리모컨이나 키보드가 연결 해제될 수 있습니다. 다른 입력 수단을 준비하세요.") { choice -> if (choice == 1) bluetoothActions.forget(device) }
                        else -> deviceDetails(device)
                    } else when (index) {
                        0 -> {
                            adapter?.cancelDiscovery()
                            if (device.bondState == BluetoothDevice.BOND_BONDING) message("이 기기의 페어링이 진행 중입니다.") else bluetoothActions.bond(device)
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
            TvMenus.show(activity, name, listOf("닫기"), "${device.address}\n${if (device.bondState == BluetoothDevice.BOND_BONDED) "등록됨" else "등록되지 않음"}\n${if (device.address in linked) "블루투스 링크 연결됨" else "블루투스 링크 연결 미확인"}\n\n등록 여부와 오디오·입력 프로필의 연결 상태는 별도로 확인하세요.")

        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("기기 정보", error) }
    }
    @Suppress("DEPRECATION")
    private fun companionPair(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < 26 || !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            message("이 TV에서는 다른 페어링 방식을 사용할 수 없습니다."); return
        }
        if (pendingCompanionAddress != null) {
            message("이미 페어링 요청이 진행 중입니다.")
            return
        }
        val address = device.address
        try {
            val manager = activity.getSystemService(CompanionDeviceManager::class.java)
            if (manager == null) {
                message("이 TV에서는 다른 페어링 방식을 사용할 수 없습니다.")
                return
            }
            adapter?.cancelDiscovery()
            pendingCompanionAddress = address
            val request = AssociationRequest.Builder()
                .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build()).setSingleDevice(true).build()
            val callback = object : CompanionDeviceManager.Callback() {
                private fun chooser(sender: IntentSender) {
                    activity.runOnUiThread {
                        if (pendingCompanionAddress != address || companionChooserOpen || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        try {
                            activity.startIntentSenderForResult(sender, 501, null, 0, 0, 0)
                            companionChooserOpen = true
                        } catch (error: Exception) {
                            pendingCompanionAddress = null
                            operationError("다른 방식으로 페어링 화면", error)
                        }
                    }
                }
                override fun onDeviceFound(chooserLauncher: IntentSender) { chooser(chooserLauncher) }
                override fun onAssociationPending(intentSender: IntentSender) { chooser(intentSender) }
                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    activity.runOnUiThread {
                        if (pendingCompanionAddress != address || companionChooserOpen || activity.isDestroyed) return@runOnUiThread
                        pendingCompanionAddress = null
                        bondCompanion(address)
                    }
                }
                override fun onFailure(error: CharSequence?) {
                    activity.runOnUiThread {
                        if (pendingCompanionAddress != address || companionChooserOpen || activity.isDestroyed) return@runOnUiThread
                        pendingCompanionAddress = null
                        message("페어링 실패 · ${error ?: "기기를 찾지 못했습니다"}")
                    }
                }
            }
            message("페어링을 준비하고 있습니다. 기기를 페어링 모드로 유지하세요.")
            if (Build.VERSION.SDK_INT >= 33) manager.associate(request, activity.mainExecutor, callback)
            else manager.associate(request, callback, null)
        } catch (error: SecurityException) {
            pendingCompanionAddress = null
            operationError("블루투스 권한", error)
        } catch (error: Exception) {
            pendingCompanionAddress = null
            operationError("다른 방식으로 페어링", error)
        }
    }
}
