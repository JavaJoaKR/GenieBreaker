package kr.javajoa.geniebreaker

import android.app.Activity
import android.bluetooth.*
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executors

/** Bluetooth requests use the permissions available to this app on the current device. */
internal class BluetoothActions(private val activity: Activity, private val report: (String) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private val adapter = activity.getSystemService(BluetoothManager::class.java)?.adapter

    private fun result(
        step: DiagnosticStep,
        text: String,
        error: Throwable? = null,
        phase: DiagnosticStep? = null
    ) {
        val failure = error != null || ErrorCodes.isError(text)
        val cause = if (failure) bluetoothBlocker(activity, adapter, step, error) ?: when {
            phase == DiagnosticStep.BLUETOOTH_PROFILE && error == null -> DiagnosticCause.PROFILE_REJECTED
            error != null -> DiagnosticCause.ERROR_UNSPECIFIED
            else -> DiagnosticCause.REASON_UNAVAILABLE
        } else null
        val code = Diagnostics.record(activity, "Bluetooth: $text", error, step, phase, cause)
        val visible = if (cause != null) "${step.label} · ${cause.label} [$code]" else text
        activity.runOnUiThread { if (!activity.isDestroyed && !activity.isFinishing) report(visible) }
    }

    fun bond(device: BluetoothDevice) {
        val step = DiagnosticStep.BLUETOOTH_PAIR
        result(step, "페어링 요청 중…")
        worker.execute {
            try {
                val text = when (device.bondState) {
                    BluetoothDevice.BOND_BONDED -> "이미 등록된 기기입니다."
                    BluetoothDevice.BOND_BONDING -> "이 기기의 페어링이 진행 중입니다."
                    else -> if (device.createBond()) {
                        "페어링을 요청했습니다. 확인 창이 표시되면 화면 안내를 따르세요."
                    } else {
                        "TV가 요청을 거절했습니다."
                    }
                }
                result(step, text)
            } catch (error: SecurityException) {
                failure(step, error)
            } catch (error: Exception) {
                failure(step, error)
            }
        }
    }

    fun forget(device: BluetoothDevice) {
        val step = DiagnosticStep.BLUETOOTH_FORGET
        worker.execute {
            try {
                val accepted = device.javaClass.getMethod("removeBond").invoke(device) as? Boolean == true
                result(step, if (accepted) "기기 등록 삭제를 요청했습니다. 목록 갱신까지 잠시 기다리세요." else "TV가 요청을 거절했습니다.")
            } catch (error: Exception) {
                failure(step, error)
            }
        }
    }

    fun connection(device: BluetoothDevice, connect: Boolean) {
        val step = if (connect) DiagnosticStep.BLUETOOTH_CONNECT else DiagnosticStep.BLUETOOTH_DISCONNECT
        val phase = DiagnosticStep.BLUETOOTH_PROFILE
        try {
            if (adapter == null) {
                result(step, "블루투스 어댑터를 사용할 수 없습니다.", phase = phase)
                return
            }
            val profile = if (device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL) 4 else BluetoothProfile.A2DP
            result(step, "${step.label} 요청 중…")
            val requested = adapter.getProfileProxy(activity, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(id: Int, proxy: BluetoothProfile) {
                    if (worker.isShutdown) {
                        closeProxy(adapter, id, proxy)
                        return
                    }
                    worker.execute {
                        try {
                            val method = if (connect) "connect" else "disconnect"
                            val accepted = proxy.javaClass.getMethod(method, BluetoothDevice::class.java).invoke(proxy, device) as? Boolean == true
                            result(step, if (accepted) "${step.label} 요청을 전송했습니다. 결과는 기기 정보에서 확인하세요." else "TV가 요청을 거절했습니다.")
                        } catch (error: Exception) {
                            failure(step, error)
                        } finally {
                            closeProxy(adapter, id, proxy)
                        }
                    }
                }

                override fun onServiceDisconnected(id: Int) { }
            }, profile)
            if (!requested) result(step, "TV가 프로필 준비 요청을 거절했습니다.", phase = phase)
        } catch (error: SecurityException) {
            failure(step, error, phase)
        } catch (error: Exception) {
            failure(step, error, phase)
        }
    }

    private fun closeProxy(adapter: BluetoothAdapter, profile: Int, proxy: BluetoothProfile) {
        try {
            adapter.closeProfileProxy(profile, proxy)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun failure(step: DiagnosticStep, caught: Exception, phase: DiagnosticStep? = null) {
        val error = if (caught is InvocationTargetException) caught.targetException else caught
        result(step, "요청 실패", error, phase)
    }

    fun close() { worker.shutdownNow() }
}
