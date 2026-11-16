package kr.javajoa.geniebreaker

import android.app.Activity
import android.bluetooth.*
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executors

/** Best-effort vendor compatibility. Reflection cannot grant a denied system permission. */
internal class BluetoothActions(private val activity: Activity, private val report: (String) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private fun result(text: String) = activity.runOnUiThread { if (!activity.isDestroyed && !activity.isFinishing) report(text) }
    fun bond(device: BluetoothDevice) {
        result("페어링 요청 중…")
        worker.execute {
            try {
                Diagnostics.record(activity, "createBond requested")
                val accepted = device.createBond()
                result(if (accepted) "페어링을 요청했습니다. PIN 또는 확인 창이 표시되면 승인하세요." else "페어링 요청이 거절됐습니다. 다른 페어링 방식을 시도할 수 있습니다.")
            } catch (error: Exception) { failure("페어링", error) }
        }
    }
    fun forget(device: BluetoothDevice) {
        worker.execute {
            try {
                val accepted = device.javaClass.getMethod("removeBond").invoke(device) as? Boolean == true
                result(if (accepted) "등록 삭제를 요청했습니다. 목록이 갱신될 때까지 기다려주세요." else "펌웨어에서 등록 삭제 요청을 거절했습니다.")
            } catch (error: Exception) { failure("등록 삭제", error) }
        }
    }
    fun connection(device: BluetoothDevice, connect: Boolean) {
        try {
            val adapter = activity.getSystemService(BluetoothManager::class.java)?.adapter ?: return
            val profile = if (device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL) 4 else BluetoothProfile.A2DP
            result(if (connect) "연결 요청 중…" else "연결 해제 요청 중…")
            val requested = adapter.getProfileProxy(activity, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(id: Int, proxy: BluetoothProfile) {
                    if (worker.isShutdown) { adapter.closeProfileProxy(id, proxy); return }
                    worker.execute {
                        try {
                            val method = if (connect) "connect" else "disconnect"
                            val accepted = proxy.javaClass.getMethod(method, BluetoothDevice::class.java).invoke(proxy, device) as? Boolean == true
                            result(if (accepted) "요청을 보냈습니다. 실제 연결 상태는 기기 정보에서 확인하세요." else "펌웨어에서 요청을 거절했습니다. 기기 전원을 다시 켜서 자동 연결도 확인해보세요.")
                        } catch (error: Exception) { failure(if (connect) "연결" else "연결 해제", error) }
                        finally { try { adapter.closeProfileProxy(id, proxy) } catch (_: Exception) { } }
                    }
                }
                override fun onServiceDisconnected(id: Int) { }
            }, profile)
            if (!requested) result("이 기기는 해당 블루투스 프로필을 제공하지 않습니다.")
        } catch (error: Exception) { failure("연결", error) }
    }
    private fun failure(operation: String, caught: Exception) {
        val error = if (caught is InvocationTargetException) caught.targetException else caught
        Diagnostics.record(activity, operation, error)
        val reason = when (error) {
            is SecurityException -> "시스템 권한이 필요하여 펌웨어에서 차단했습니다."
            is NoSuchMethodException, is IllegalAccessException -> "이 펌웨어는 앱의 직접 제어를 허용하지 않습니다."
            else -> "${error.javaClass.simpleName}: ${error.message?.take(140) ?: "응답 없음"}"
        }
        result("$operation 실패. $reason 오류 기록에 세부 내용을 저장했습니다.")
    }
    fun close() { worker.shutdownNow() }
}
