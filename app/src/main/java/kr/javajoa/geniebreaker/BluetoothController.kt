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
import android.provider.Settings

internal class BluetoothController(
    private val activity: Activity,
    private val preview: Boolean,
    private val publish: () -> Unit
) {
    private val adapter = activity.getSystemService(BluetoothManager::class.java)?.adapter
    private val found = linkedMapOf<String, BluetoothDevice>()
    private val linked = mutableSetOf<String>()
    private val names = DeviceNames()
    private class CompanionRequest(val address: String, var chooserOpen: Boolean = false)

    private var companionRequest: CompanionRequest? = null
    private val bluetoothActions = BluetoothActions(activity) { text ->
        message(text)
        if (ErrorCodes.isError(text) || "[GB-" in text) TvMenus.notice(activity, text)
    }
    private var btMessage = "연결할 블루투스 기기를 선택하세요"
    private var registered = false
    private var scanExpired = false
    private var watchdogScheduled = false
    private var searched = false
    private var pendingNames = false
    private var presentedError: String? = null
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val companionChooserTimeout = Runnable {
        if (companionRequest?.chooserOpen == false && !activity.isFinishing && !activity.isDestroyed) {
            finishCompanionRequest()
            reportBlocker("보조 페어링 화면 응답 지연", DiagnosticStep.BLUETOOTH_COMPANION,
                DiagnosticCause.COMPANION_CHOOSER_TIMEOUT)
        }
    }
    private val scanWatchdog = Runnable {
        watchdogScheduled = false
        if (companionRequest != null) return@Runnable
        scanExpired = true
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
        companionRequest = saved?.getString("companion_address")?.let { CompanionRequest(it, chooserOpen = true) }
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
        val permission = preview || access()
        val devices = mutableListOf<Map<String, Any>>()
        pendingNames = false
        if (preview) {
            listOf("거실 헤드폰", "무선 키보드", "WH-1000XM5", "이름 확인 중", "Bluetooth Keyboard").forEachIndexed { index, name ->
                devices += mapOf("id" to "preview-$index", "name" to name, "bonded" to (index < 2),
                    "kind" to if (index == 1 || index == 4) "input" else "audio",
                    "detail" to if (index < 2) "등록됨" else "페어링 가능")
            }
        } else if (permission) {
            try {
                val bonded = adapter?.bondedDevices.orEmpty()
                (bonded + found.values).distinctBy { it.address }.forEach { device ->
                    val address = device.address
                    val bondState = device.bondState
                    if (names.get(address) == null) names.seed(address, device.name)
                    val waitingForName = names.isPending(address)
                    pendingNames = pendingNames || waitingForName
                    val kind = when (device.bluetoothClass?.majorDeviceClass) {
                        BluetoothClass.Device.Major.AUDIO_VIDEO -> "audio"
                        BluetoothClass.Device.Major.PERIPHERAL -> "input"
                        else -> "bluetooth"
                    }
                    val detail = when {
                        bondState == BluetoothDevice.BOND_BONDING -> "페어링 중"
                        address in linked -> "블루투스 링크 연결됨"
                        bondState == BluetoothDevice.BOND_BONDED -> "등록됨"
                        else -> "페어링 가능"
                    }
                    devices += mapOf(
                        "id" to address,
                        "name" to (names.get(address) ?: if (waitingForName) "이름 확인 중" else "이름을 확인할 수 없는 기기"),
                        "bonded" to (bondState == BluetoothDevice.BOND_BONDED),
                        "kind" to kind,
                        "detail" to detail
                    )
                }
            } catch (error: SecurityException) {
                Diagnostics.record(activity, "Bluetooth snapshot", error)
            } catch (error: Exception) {
                Diagnostics.record(activity, "Bluetooth snapshot", error)
            }
        }
        val enabled = preview || try { permission && adapter?.isEnabled == true } catch (_: SecurityException) { false }
        val searching = preview || try { permission && adapter?.isDiscovering == true && !scanExpired } catch (_: SecurityException) { false }
        return mapOf(
            "devices" to devices, "permission" to permission, "enabled" to enabled,
            "supported" to (preview || adapter != null), "searching" to searching,
            "searched" to searched, "status" to btMessage
        )
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
                        BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                            val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                            if (state == BluetoothDevice.BOND_NONE && previous == BluetoothDevice.BOND_BONDING) {
                                val step = DiagnosticStep.BLUETOOTH_PAIR
                                val cause = bluetoothBlocker(activity, adapter, step) ?: DiagnosticCause.PAIRING_ENDED
                                val code = Diagnostics.record(activity, "Bluetooth: 페어링 실패", step = step, cause = cause)
                                message("${cause.label} [$code]")
                            } else message(when (state) {
                                BluetoothDevice.BOND_BONDED -> "페어링 완료. 기기에서 연결 상태를 확인하세요."
                                BluetoothDevice.BOND_BONDING -> "페어링 중입니다. 확인 창이 표시되면 승인하세요."
                                else -> "기기 등록 상태가 변경되었습니다."
                            })
                        }
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
        // The system chooser owns discovery while a companion request is in progress.
        try {
            if (companionRequest == null && access()) adapter?.cancelDiscovery()
        } catch (_: SecurityException) { }
    }
    fun save(out: Bundle) {
        out.putString("companion_address", companionRequest?.takeIf { it.chooserOpen }?.address)
        out.putBundle("names", Bundle().apply { names.snapshot().forEach { (key, value) -> putString(key, value) } })
    }

    fun close() {
        bluetoothActions.close()
        finishCompanionRequest()
        uiHandler.removeCallbacksAndMessages(null)
    }

    fun result(requestCode: Int, resultCode: Int) {
        if (requestCode != 501) return
        val request = companionRequest?.takeIf { it.chooserOpen } ?: return
        finishCompanionRequest()
        val step = DiagnosticStep.BLUETOOTH_COMPANION
        when {
            resultCode == Activity.RESULT_OK -> bondCompanion(request.address)
            resultCode == Activity.RESULT_CANCELED ||
                (Build.VERSION.SDK_INT >= 34 && resultCode == CompanionDeviceManager.RESULT_USER_REJECTED) ->
                message("시스템 기기 선택이 취소되었습니다.")
            Build.VERSION.SDK_INT >= 34 && resultCode == CompanionDeviceManager.RESULT_DISCOVERY_TIMEOUT ->
                reportBlocker("보조 페어링 기기 검색 실패", step, DiagnosticCause.COMPANION_DISCOVERY_TIMEOUT)
            else -> reportBlocker("보조 페어링 실패", step, DiagnosticCause.COMPANION_REQUEST_FAILED)
        }
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

    private fun operationError(operation: String, error: Exception, step: DiagnosticStep? = null) {
        val cause = step?.let { bluetoothBlocker(activity, adapter, it, error) ?: DiagnosticCause.ERROR_UNSPECIFIED }
        val code = Diagnostics.record(activity, "Bluetooth: $operation", error, step, cause = cause)
        val detail = cause?.let { "\n${it.label}" }.orEmpty()
        val text = "$operation 실패 [$code]$detail"
        message(text)
        if (presentedError != code && !activity.isFinishing && !activity.isDestroyed) {
            presentedError = code
            TvMenus.notice(activity, text)
        }
    }

    private fun reportBlocker(operation: String, step: DiagnosticStep, cause: DiagnosticCause) {
        val code = Diagnostics.record(activity, "Bluetooth: $operation", step = step, cause = cause)
        val text = "${step.label} · ${cause.label} [$code]"
        message(text)
        if (cause == DiagnosticCause.COMPANION_LOCATION_DISABLED) {
            TvMenus.show(activity, "보조 페어링", listOf("TV 위치 설정 열기", "닫기"), text) { choice ->
                if (choice == 0 && !openSystemSettings(activity, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) {
                    TvMenus.notice(activity, "이 TV에서는 위치 설정 화면을 열 수 없습니다.")
                }
            }
        } else {
            TvMenus.notice(activity, text)
        }
    }

    fun openSettings() {
        val restriction = bluetoothSettingsRestriction(activity)
        if (restriction == null && openBluetoothScreen(activity)) return
        val cause = restriction ?: DiagnosticCause.SCREEN_UNAVAILABLE
        val operation = if (restriction == null) "Bluetooth settings failed" else "Bluetooth settings blocked"
        val code = Diagnostics.record(activity, operation, step = DiagnosticStep.SHORTCUT, cause = cause)
        if (restriction != null) {
            TvMenus.notice(activity, "${cause.label} [$code]")
            return
        }
        TvMenus.show(activity, "블루투스 설정", listOf("수동 바로가기", "TV 설정 열기", "닫기"),
            "${cause.label} [$code]\nGenieBreaker의 ‘블루투스’ 탭에서 기기 검색과 페어링을 시도할 수 있습니다.") { choice ->
            when (choice) {
                0 -> showManualSettings(activity)
                1 -> if (!openSystemSettings(activity, Intent(Settings.ACTION_SETTINGS))) {
                    TvMenus.notice(activity, "리모컨의 홈 메뉴에서 TV 설정을 여세요.")
                }
            }
        }
    }

    private fun granted(permission: String) =
        activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun access() = if (Build.VERSION.SDK_INT >= 31) {
        granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
    } else granted(Manifest.permission.ACCESS_FINE_LOCATION)

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
        } catch (error: ActivityNotFoundException) {
            operationError("블루투스 켜기 화면 열기", error, DiagnosticStep.BLUETOOTH_ENABLE)
        } catch (error: SecurityException) {
            operationError("블루투스 켜기 요청", error, DiagnosticStep.BLUETOOTH_ENABLE)
        }
    }
    fun discover() {
        val step = DiagnosticStep.BLUETOOTH_SCAN
        val blocker = bluetoothBlocker(activity, adapter, step)
        if (blocker in setOf(DiagnosticCause.SCAN_PERMISSION, DiagnosticCause.CONNECT_PERMISSION, DiagnosticCause.LOCATION_PERMISSION)) {
            Diagnostics.record(activity, "Bluetooth: 검색 권한 차단", step = step, cause = blocker)
            requestAccess()
            return
        }
        if (blocker != null && blocker != DiagnosticCause.BLUETOOTH_DISABLED) {
            reportBlocker("검색 차단", step, blocker)
            return
        }
        val bt = adapter ?: return
        try {
            if (!bt.isEnabled) { enableBluetooth(); return }
            if (bt.isDiscovering) { bt.cancelDiscovery(); return }
            if (Build.VERSION.SDK_INT < 31) {
                val location = activity.getSystemService(LocationManager::class.java)
                if (location == null || !(location.isProviderEnabled(LocationManager.GPS_PROVIDER) || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
                    reportBlocker("검색 차단", step, DiagnosticCause.LOCATION_DISABLED)
                    return
                }
            }
            found.clear(); searched = true; scanExpired = false; publish()
            if (bt.startDiscovery()) {
                uiHandler.removeCallbacks(scanWatchdog); watchdogScheduled = true; uiHandler.postDelayed(scanWatchdog, 35_000)
                refresh()
            } else {
                reportBlocker("검색 요청 거절", step,
                    bluetoothBlocker(activity, adapter, step) ?: DiagnosticCause.REASON_UNAVAILABLE)
            }
        } catch (error: SecurityException) {
            operationError("기기 검색 권한", error, DiagnosticStep.BLUETOOTH_SCAN)
        } catch (error: Exception) {
            operationError("기기 검색", error, DiagnosticStep.BLUETOOTH_SCAN)
        }
    }
    private fun pair(device: BluetoothDevice) {
        if (!access()) { requestAccess(); return }
        try {
            val title = names.get(device.address) ?: device.name ?: "이름 확인 중"
            val bonded = device.bondState == BluetoothDevice.BOND_BONDED
            val companion = supportsCompanionPairing()
            val actions = if (bonded) arrayOf("연결", "연결 해제", "기기 등록 삭제", "상세 정보")
                else if (companion) arrayOf("페어링", "다른 방식으로 페어링", "기기 정보")
                else arrayOf("페어링", "기기 정보")
            TvMenus.show(activity, title, actions.toList()) { index ->
                try {
                    if (bonded) when (index) {
                        0 -> bluetoothActions.connection(device, true)
                        1 -> bluetoothActions.connection(device, false)
                        2 -> TvMenus.show(activity, "기기 등록 삭제 확인", listOf("취소", "삭제"), "등록 삭제 후 리모컨이나 키보드가 연결 해제될 수 있습니다. 다른 입력 수단을 준비하세요.") { choice -> if (choice == 1) bluetoothActions.forget(device) }
                        else -> deviceDetails(device)
                    } else when (index) {
                        0 -> bluetoothActions.bond(device)
                        1 -> if (companion) companionPair(device) else deviceDetails(device)
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
    private fun finishCompanionRequest() {
        companionRequest = null
        uiHandler.removeCallbacks(companionChooserTimeout)
    }

    private fun supportsCompanionPairing() =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP) &&
            activity.getSystemService(CompanionDeviceManager::class.java) != null

    @Suppress("DEPRECATION")
    private fun companionPair(device: BluetoothDevice) {
        val step = DiagnosticStep.BLUETOOTH_COMPANION
        if (companionRequest != null) {
            message("이미 페어링 요청이 진행 중입니다.")
            return
        }
        val manager = activity.getSystemService(CompanionDeviceManager::class.java)
        if (manager == null || !supportsCompanionPairing()) {
            reportBlocker("보조 페어링 미지원", step, DiagnosticCause.COMPANION_UNAVAILABLE)
            return
        }
        val location = activity.getSystemService(LocationManager::class.java)
        if (Build.VERSION.SDK_INT >= 28 && location?.isLocationEnabled == false) {
            reportBlocker("보조 페어링 위치 서비스 꺼짐", step, DiagnosticCause.COMPANION_LOCATION_DISABLED)
            return
        }
        try {
            val pending = CompanionRequest(device.address)
            adapter?.cancelDiscovery()
            companionRequest = pending
            val request = AssociationRequest.Builder()
                .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(pending.address).build()).setSingleDevice(true).build()
            val callback = object : CompanionDeviceManager.Callback() {
                private fun isCurrent() =
                    companionRequest === pending && !pending.chooserOpen && !activity.isFinishing && !activity.isDestroyed

                private fun chooser(sender: IntentSender) {
                    activity.runOnUiThread {
                        if (!isCurrent()) return@runOnUiThread
                        uiHandler.removeCallbacks(companionChooserTimeout)
                        try {
                            pending.chooserOpen = true
                            activity.startIntentSenderForResult(sender, 501, null, 0, 0, 0)
                        } catch (error: SecurityException) {
                            finishCompanionRequest()
                            operationError("시스템 기기 선택창 권한", error, step)
                        } catch (_: IntentSender.SendIntentException) {
                            finishCompanionRequest()
                            reportBlocker("시스템 기기 선택창 실행 실패", step, DiagnosticCause.COMPANION_CHOOSER_UNAVAILABLE)
                        } catch (_: ActivityNotFoundException) {
                            finishCompanionRequest()
                            reportBlocker("시스템 기기 선택창 실행 실패", step, DiagnosticCause.COMPANION_CHOOSER_UNAVAILABLE)
                        } catch (error: Exception) {
                            finishCompanionRequest()
                            operationError("시스템 기기 선택창 실행", error, step)
                        }
                    }
                }

                override fun onDeviceFound(chooserLauncher: IntentSender) { chooser(chooserLauncher) }
                override fun onAssociationPending(intentSender: IntentSender) { chooser(intentSender) }
                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    activity.runOnUiThread {
                        if (!isCurrent()) return@runOnUiThread
                        finishCompanionRequest()
                        bondCompanion(pending.address)
                    }
                }

                override fun onFailure(error: CharSequence?) {
                    activity.runOnUiThread {
                        if (!isCurrent()) return@runOnUiThread
                        finishCompanionRequest()
                        reportBlocker("보조 페어링 실패", step,
                            bluetoothBlocker(activity, adapter, step) ?: DiagnosticCause.COMPANION_REQUEST_FAILED)
                    }
                }
            }
            message("시스템 기기 선택창을 기다리는 중입니다. 기기를 페어링 모드로 유지하세요.")
            uiHandler.postDelayed(companionChooserTimeout, 30_000)
            if (Build.VERSION.SDK_INT >= 33) manager.associate(request, activity.mainExecutor, callback)
            else manager.associate(request, callback, null)
        } catch (error: SecurityException) {
            finishCompanionRequest()
            operationError("보조 페어링 권한", error, step)
        } catch (error: Exception) {
            finishCompanionRequest()
            operationError("보조 페어링 요청", error, step)
        }
    }
}
