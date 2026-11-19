package kr.javajoa.geniebreaker

import android.content.*
import android.os.Bundle
import android.view.KeyEvent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/** One Flutter surface owns all application UI and remote focus. Android owns device APIs only. */
class MainActivity : FlutterActivity() {
    private var channel: MethodChannel? = null
    private var menus: MenuController? = null
    private lateinit var bluetooth: BluetoothController
    private lateinit var apps: AppManagementController
    private lateinit var files: FileController
    private lateinit var controls: TvControls
    private lateinit var updater: AppUpdater
    private lateinit var appLock: AppLock
    private var updateLabel = "업데이트 확인"
    private var preview = false
    private var uiReady = false
    private var resumed = false
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        preview = BuildConfig.DEBUG && intent.getBooleanExtra("ui_preview", false)
        Diagnostics.install(applicationContext)
        appLock = AppLock(this)
        bluetooth = BluetoothController(this, preview, ::publish)
        bluetooth.restore(savedInstanceState)
        apps = AppManagementController(this, { pkg, label -> controls.selectStartup(pkg, label) }, ::publish)
        files = FileController(this) { channel?.invokeMethod("filesChanged", null) }
        updater = AppUpdater(this) { text, _ -> updateLabel = text; publish() }
        controls = TvControls(this, { menus?.current()?.get("pageId") as? String }, { menus?.back() }, ::publish)
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        fun disableNativeFocusOverlay(view: android.view.View) {
            view.setDefaultFocusHighlightEnabled(false)
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) disableNativeFocusOverlay(view.getChildAt(index))
        }
        disableNativeFocusOverlay(window.decorView)
        if (!preview) updater.checkForUpdate(automatic = true)
        val prefs = getSharedPreferences("local_diagnostics", MODE_PRIVATE)
        prefs.getString("last_crash", null)?.let { code ->
            prefs.edit().remove("last_crash").apply()
            uiHandler.post { TvMenus.notice(this, "이전 실행이 예기치 않게 종료되었습니다. 오류 코드 [$code]") }
        }
    }
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "genie/ui")
        menus = MenuController(this, { page -> if (!appLock.locked) channel?.invokeMethod("page", page) },
            { if (!appLock.locked) channel?.invokeMethod("dismiss", null) })
        TvMenus.attach(this, menus!!)
        channel!!.setMethodCallHandler(::handleBridgeRequest)
    }

    private fun handleBridgeRequest(call: MethodCall, result: MethodChannel.Result) {
        try {
            if (call.method == "ready") {
                uiReady = true
                result.success(snapshot())
                if (!appLock.locked) apps.refresh()
                return
            }
            if (call.method == "unlockApp") {
                val unlocked = resumed && hasWindowFocus() && appLock.unlock(call.arguments as? String ?: "")
                result.success(unlocked)
                if (unlocked) {
                    menus?.clear()
                    publishSession()
                    apps.refresh()
                }
                return
            }
            if (appLock.locked) {
                result.error("locked", "GenieBreaker가 잠겨 있습니다.", null)
                return
            }
            if (menus?.handle(call, result) == true || files.handle(call, result)) return

            when (call.method) {
                "enableLock", "disableLock" -> {
                    val saved = if (call.method == "enableLock") appLock.enable() else appLock.disable()
                    result.success(saved)
                    publish()
                }
                else -> if (handleShellAction(call)) result.success(null) else result.notImplemented()
            }
        } catch (error: Exception) {
            val code = Diagnostics.record(this, "UI request", error)
            result.error("request", "요청을 처리하지 못했습니다. [$code]", null)
        }
    }

    private fun handleShellAction(call: MethodCall): Boolean {
        when (call.method) {
            "refreshShell" -> {
                bluetooth.refresh()
                apps.refresh()
                controls.refresh()
            }
            "scan" -> bluetooth.discover()
            "permission" -> bluetooth.requestAccess()
            "power" -> bluetooth.enableBluetooth()
            "device" -> bluetooth.choose(call.arguments as String)
            "app" -> apps.choose(call.arguments as String)
            "appFilter" -> apps.toggleDuals()
            "appView" -> apps.viewOptions()
            "update" -> updater.click()
            "diagnostics" -> showDiagnostics()
            "appSettings" -> openAppSettings()
            "accountSettings" -> showAccountSettings()
            "tvUpdateSettings" -> {
                if (!openTvUpdateSettings(this)) {
                    TvMenus.notice(this, "TV 설정의 ‘정보’에서 시스템 업데이트를 확인하세요.")
                }
            }
            "homeSettings" -> controls.homeSettings()
            "accessibility" -> controls.accessibility()
            "mappingSettings" -> controls.mappingSettings()
            "chooseButton" -> controls.chooseButton()
            "editButton" -> controls.edit((call.arguments as Number).toInt())
            "chooseStartup" -> controls.chooseStartup()
            "startupPermission" -> controls.startupPermission()
            "toggleWake" -> controls.toggleWake()
            "testStartup" -> controls.testStartup()
            "clearStartup" -> controls.clearStartup()
            "exit" -> if (!isDefaultHome(this)) finish()
            else -> return false
        }
        return true
    }

    private fun ensureFlutterFocus() {
        fun find(view: android.view.View): io.flutter.embedding.android.FlutterView? {
            if (view is io.flutter.embedding.android.FlutterView) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        find(window.decorView)?.let { view ->
            view.isFocusable = true; view.isFocusableInTouchMode = true
            if (!view.isFocused) view.requestFocus()
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (resumed) controls.refresh()
            ensureFlutterFocus()
            channel?.invokeMethod("enter", null)
            if (!preview && intent.hasCategory(Intent.CATEGORY_HOME)) StartupApps.launchFromHome(this)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!preview && hasWindowFocus() && intent.hasCategory(Intent.CATEGORY_HOME)) StartupApps.launchFromHome(this)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!appLock.locked && RemoteCapture.handle(event)) return true
        // Handle remote navigation at the window before Flutter's keyboard
        // embedder can drop or misclassify a firmware key event.
        val bridge = channel
        val command = remoteNavigationCommand(event)
        if (uiReady && bridge != null && command != null && hasWindowFocus()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (!event.isCanceled && (command != "activate" || event.repeatCount == 0)) {
                    bridge.invokeMethod("remoteNavigation", command)
                }
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun snapshot(): Map<String, Any> = if (appLock.locked) {
        mapOf("shell" to true, "locked" to true, "version" to BuildConfig.VERSION_NAME)
    } else bluetooth.snapshot() +
        mapOf("shell" to true, "version" to BuildConfig.VERSION_NAME,
            "apps" to apps.snapshot(), "update" to updateLabel, "controls" to controls.snapshot(), "lockEnabled" to appLock.enabled) +
        (menus?.current()?.let { mapOf("menu" to it) } ?: emptyMap())

    private fun publish() { if (!isFinishing && !isDestroyed && ::apps.isInitialized) channel?.invokeMethod("shell", snapshot()) }
    private fun publishSession() { channel?.invokeMethod("session", snapshot()) }

    override fun onStart() { super.onStart(); bluetooth.start() }
    override fun onResume() {
        super.onResume()
        resumed = true
        controls.resume()
        if (!preview) updater.resume()
        apps.resume()
        bluetooth.refresh()
    }
    override fun onPause() {
        resumed = false
        if (appLock.relock()) {
            menus?.clear()
            publishSession()
        }
        controls.pause()
        super.onPause()
    }
    override fun onStop() { bluetooth.stop(); super.onStop() }
    override fun onSaveInstanceState(out: Bundle) { bluetooth.save(out); super.onSaveInstanceState(out) }
    override fun onDestroy() {
        TvMenus.detach(this); menus?.clear(); channel?.setMethodCallHandler(null)
        if (::updater.isInitialized) updater.close()
        if (::apps.isInitialized) apps.close()
        if (::files.isInitialized) files.close()
        bluetooth.close()
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); bluetooth.refresh()
    }
    @Deprecated("Platform result callback for TV firmware compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (files.result(requestCode, resultCode, data)) return
        if (requestCode == 610) { apps.exportResult(resultCode, data); return }
        bluetooth.result(requestCode, resultCode)
    }
    private fun openAppSettings() {
        if (!openAppsScreen(this)) TvMenus.notice(this, "TV 설정에서 ‘앱’을 열어 설치 목록을 확인하세요.")
    }
    private fun showAccountSettings() {
        if (!openAccountSettings(this)) {
            TvMenus.notice(this, "TV 설정의 ‘계정’에서 기기 계정을 확인하세요.")
        }
    }
    private fun showDiagnostics() {
        val text = Diagnostics.read(this)
        TvMenus.show(this, "문제 기록 · 이 기기에 저장됨", listOf("기록 복사", "기록 지우기"), text) { which ->
            if (which == 0) getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("GenieBreaker local errors", text))
            else TvMenus.show(this, "문제 기록 지우기", listOf("취소", "기록 지우기"), "기기에 저장된 오류 기록을 삭제합니다.") { if (it == 1) Diagnostics.clear(this) }
        }
    }
}
