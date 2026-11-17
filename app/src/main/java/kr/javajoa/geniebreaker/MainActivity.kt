package kr.javajoa.geniebreaker

import android.content.*
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/** One Flutter surface owns all application UI and remote focus. Android owns device APIs only. */
class MainActivity : FlutterActivity() {
    private var channel: MethodChannel? = null
    private var menus: MenuController? = null
    private lateinit var bluetooth: BluetoothController
    private lateinit var apps: AppManagementController
    private lateinit var files: FileController
    private lateinit var controls: TvControls
    private val accessibilityListener = AccessibilityManager.AccessibilityStateChangeListener { controls.resume() }
    private lateinit var updater: AppUpdater
    private var updateLabel = "업데이트 확인"
    private var preview = false
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        preview = BuildConfig.DEBUG && intent.getBooleanExtra("ui_preview", false)
        Diagnostics.install(applicationContext)
        bluetooth = BluetoothController(this, preview, ::publish)
        bluetooth.restore(savedInstanceState)
        apps = AppManagementController(this) { publish() }
        files = FileController(this) { channel?.invokeMethod("filesChanged", null) }
        updater = AppUpdater(this) { text, _ -> updateLabel = text; publish() }
        controls = TvControls(this, { menus?.current()?.get("pageId") as? String }, ::publish)
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
                    "refreshShell" -> { bluetooth.refresh(); apps.refresh(); result.success(null) }
                    "scan" -> { bluetooth.discover(); result.success(null) }
                    "permission" -> { bluetooth.requestAccess(); result.success(null) }
                    "power" -> { bluetooth.enableBluetooth(); result.success(null) }
                    "device" -> { bluetooth.choose(call.arguments as String); result.success(null) }
                    "app" -> { apps.choose(call.arguments as String); result.success(null) }
                    "appFilter" -> { apps.toggleDuals(); result.success(null) }
                    "appView" -> { apps.viewOptions(); result.success(null) }
                    "update" -> { updater.click(); result.success(null) }
                    "diagnostics" -> { showDiagnostics(); result.success(null) }
                    "appSettings" -> { openAppSettings(); result.success(null) }
                    "accessibility" -> { controls.accessibility(); result.success(null) }
                    "chooseButton" -> { controls.chooseButton(); result.success(null) }
                    "editButton" -> { controls.edit((call.arguments as Number).toInt()); result.success(null) }
                    "chooseStartup" -> { controls.chooseStartup(); result.success(null) }
                    "startupPermission" -> { controls.startupPermission(); result.success(null) }
                    "toggleWake" -> { controls.toggleWake(); result.success(null) }
                    "testStartup" -> { controls.testStartup(); result.success(null) }
                    "clearStartup" -> { controls.clearStartup(); result.success(null) }
                    "exit" -> { finish(); result.success(null) }
                    else -> result.notImplemented()
                }
            } catch (error: Exception) {
                val code = Diagnostics.record(this, "UI request", error)
                result.error("request", "요청을 처리하지 못했습니다. [$code]", null)
            }
        }
    }
    private fun ensureFlutterFocus() {
        fun find(view: android.view.View): io.flutter.embedding.android.FlutterView? {
            if (view is io.flutter.embedding.android.FlutterView) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        find(window.decorView)?.let { view ->
            view.isFocusable = true; view.isFocusableInTouchMode = true
            if (!view.hasFocus()) view.requestFocus()
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { ensureFlutterFocus(); channel?.invokeMethod("enter", null) }
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (RemoteCapture.handle(event)) return true
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.keyCode in setOf(
                android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER)) ensureFlutterFocus()
        return super.dispatchKeyEvent(event)
    }

    private fun snapshot(): Map<String, Any> = bluetooth.snapshot() +
        mapOf("shell" to true, "version" to BuildConfig.VERSION_NAME,
            "apps" to apps.snapshot(), "update" to updateLabel, "controls" to controls.snapshot()) +
        (menus?.current()?.let { mapOf("menu" to it) } ?: emptyMap())

    private fun publish() { if (!isFinishing && !isDestroyed && ::apps.isInitialized) channel?.invokeMethod("shell", snapshot()) }

    override fun onStart() { super.onStart(); bluetooth.start() }
    override fun onResume() {
        super.onResume()
        getSystemService(AccessibilityManager::class.java).addAccessibilityStateChangeListener(accessibilityListener)
        controls.resume()
        if (!preview) updater.resume()
        apps.resume()
        bluetooth.refresh()
    }
    override fun onPause() {
        getSystemService(AccessibilityManager::class.java).removeAccessibilityStateChangeListener(accessibilityListener)
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
        if (!openAppsScreen(this)) TvMenus.notice(this, "이 TV에서는 앱 목록을 직접 열 수 없습니다. TV 설정에서 앱을 여세요.")
    }
    private fun showDiagnostics() {
        val text = Diagnostics.read(this)
        TvMenus.show(this, "문제 기록 · 기기 내부", listOf("기록 복사", "기록 지우기"), text) { which ->
            if (which == 0) getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("GenieBreaker local errors", text))
            else TvMenus.show(this, "문제 기록 지우기", listOf("취소", "기록 지우기"), "기기에 저장된 오류 코드를 지웁니다.") { if (it == 1) Diagnostics.clear(this) }
        }
    }
}
