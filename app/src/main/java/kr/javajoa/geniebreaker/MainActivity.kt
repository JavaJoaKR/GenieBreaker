package kr.javajoa.geniebreaker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.companion.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.widget.*

/** Leanback-style settings, implemented with platform views and no UI runtime. */
class MainActivity : Activity() {
    private var adapter: BluetoothAdapter? = null
    private lateinit var bluetoothPage: LinearLayout
    private lateinit var appsPage: AppManagementView
    private lateinit var bluetoothTab: Button
    private lateinit var appsTab: Button
    private var appTabSelected = false
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var scanExpired = false
    private val nameTick = Runnable { if (registered && !preview) renderDevices() }
    private val scanWatchdog = Runnable {
        scanExpired = true; searching = false
        try { adapter?.cancelDiscovery() } catch (error: SecurityException) { operationError("검색 중지", error) } catch (error: Exception) { operationError("검색 중지", error) }
        refresh()
    }
    private lateinit var status: TextView
    private lateinit var scan: Button
    private lateinit var power: Button
    private lateinit var permission: Button
    private lateinit var updateButton: Button
    private lateinit var updater: AppUpdater
    private lateinit var badge: TextView
    private lateinit var registeredList: LinearLayout
    private lateinit var nearbyList: LinearLayout
    private lateinit var registeredCount: TextView
    private lateinit var nearbyCount: TextView
    private lateinit var registeredEmpty: LinearLayout
    private lateinit var nearbyEmpty: LinearLayout
    private lateinit var nearbyHint: TextView
    private lateinit var progress: ProgressBar
    private val found = linkedMapOf<String, BluetoothDevice>()
    private val names = DeviceNames()
    private val rows = linkedMapOf<String, DeviceRow>()
    private lateinit var bluetoothActions: BluetoothActions
    private var pendingCompanionAddress: String? = null
    private val linked = mutableSetOf<String>()
    private var registered = false
    private var searching = false
    private var searched = false
    private var preview = false
    private val compact: Boolean get() = resources.configuration.screenWidthDp < 800
    private val backgroundColor = Color.rgb(20, 23, 29)
    private val surface = Color.rgb(29, 33, 41)
    private val muted = Color.rgb(166, 175, 190)
    private val accent = Color.rgb(151, 192, 255)
    private val white = Color.rgb(240, 243, 249)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!access()) { refresh(); return }
            try {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = deviceExtra(intent) ?: return
                        found[device.address] = device
                        val reportedName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        if (!reportedName.isNullOrBlank()) names.merge(device.address, reportedName) else names.seed(device.address, device.name)
                        renderDevices()
                    }
                    BluetoothDevice.ACTION_NAME_CHANGED -> {
                        val device = deviceExtra(intent) ?: return
                        // Name broadcasts may precede discovery, or arrive after scanning finishes.
                        val reportedName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        if (!reportedName.isNullOrBlank()) names.merge(device.address, reportedName) else names.seed(device.address, device.name)
                        if (found.containsKey(device.address)) found[device.address] = device
                        // Never add an unrelated device from a name-only broadcast.
                        renderDevices()
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        renderDevices()
                        message(when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                            BluetoothDevice.BOND_BONDED -> "페어링 완료. 기기에서 연결 상태를 확인하세요."
                            BluetoothDevice.BOND_BONDING -> "페어링 중입니다. TV에 표시된 확인 창을 확인하세요."
                            else -> "페어링이 취소되었거나 등록이 해제되었습니다."
                        })
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        deviceExtra(intent)?.let { device ->
                            if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) linked.add(device.address) else linked.remove(device.address)
                        }
                        renderDevices()
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> { searching = true; refresh() }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { searching = false; uiHandler.removeCallbacks(scanWatchdog); refresh() }
                    else -> refresh()
                }
            } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("블루투스 상태 갱신", error) }
        }
    }
    @Suppress("DEPRECATION")
    private fun deviceExtra(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.install(applicationContext)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        preview = BuildConfig.DEBUG && intent.getBooleanExtra("ui_preview", false)
        adapter = getSystemService(BluetoothManager::class.java)?.adapter
        savedInstanceState?.getBundle("names")?.let { bundle -> bundle.keySet().forEach { names.merge(it, bundle.getString(it)) } }
        pendingCompanionAddress = savedInstanceState?.getString("companion_address")
        bluetoothActions = BluetoothActions(this) { text ->
            message(text)
            if (text.contains("실패") && !isFinishing && !isDestroyed) AlertDialog.Builder(this).setTitle("블루투스 요청 결과").setMessage(text).setPositiveButton("닫기", null).show()
        }
        buildScreen()
        updater = AppUpdater(this) { text, enabled -> updateButton.text = text; updateButton.isEnabled = enabled }
        if (!preview) updater.checkForUpdate(automatic = true)
        scan.requestFocus()
        if (savedInstanceState?.getBoolean("apps_tab") == true) selectTab(true)
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("apps_tab", appTabSelected)
        out.putString("companion_address", pendingCompanionAddress)
        out.putBundle("names", Bundle().apply { names.snapshot().forEach { (key, value) -> putString(key, value) } })
        super.onSaveInstanceState(out)
    }
    private fun buildScreen() {
        val root = column().apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(if (compact) 18 else 32), dp(if (compact) 16 else 28), dp(if (compact) 18 else 32), dp(if (compact) 16 else 24)); setBackgroundColor(backgroundColor)
        }
        setContentView(root)
        val sidebar = column().apply { setPadding(0, dp(6), dp(24), 0) }
        root.addView(sidebar, LinearLayout.LayoutParams(dp(if (compact) 142 else 190), -1))
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(this).apply { setImageResource(R.drawable.icon) }, LinearLayout.LayoutParams(dp(34), dp(34)))
        brand.addView(label("GenieBreaker", if (compact) 13 else 17, white, true).apply { setPadding(dp(8), 0, 0, 0) })
        sidebar.addView(brand)
        sidebar.addView(Space(this), LinearLayout.LayoutParams(1, dp(if (compact) 18 else 34)))
        bluetoothTab = actionButton("블루투스", false) { selectTab(false) }.apply { isSelected = true }
        appsTab = actionButton("앱 관리", false) { selectTab(true) }
        sidebar.addView(bluetoothTab); sidebar.addView(appsTab)
        sidebar.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        permission = actionButton("권한 허용", false, ::requestAccess)
        power = actionButton("블루투스 켜기", false, ::enableBluetooth)
        sidebar.addView(permission); sidebar.addView(power)
        updateButton = actionButton("업데이트 확인", false) { updater.click() }
        sidebar.addView(updateButton)
        sidebar.addView(actionButton("오류 기록", false) { showDiagnostics() })
        sidebar.addView(label("GenieBreaker ${BuildConfig.VERSION_NAME}", 11).apply { setPadding(dp(4), dp(15), 0, 0) })
        root.addView(View(this).apply { setBackgroundColor(0xff353a44.toInt()) }, LinearLayout.LayoutParams(dp(1), -1))
        val content = column().apply { setPadding(dp(if (compact) 16 else 28), 0, 0, 0) }
        bluetoothPage = content
        val pageHost = FrameLayout(this)
        root.addView(pageHost, LinearLayout.LayoutParams(0, -1, 1f))
        pageHost.addView(content, FrameLayout.LayoutParams(-1, -1))
        appsPage = AppManagementView(this).apply { setPadding(dp(if (compact) 16 else 28), 0, 0, 0); visibility = View.GONE }
        pageHost.addView(appsPage, FrameLayout.LayoutParams(-1, -1))
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = column()
        titles.addView(label("블루투스", if (compact) 24 else 32, white, true))

        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        badge = label("확인 중", 12, accent).apply {
            gravity = Gravity.CENTER; setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(0xff253346.toInt(), 20)
        }
        heading.addView(badge)
        content.addView(heading)
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(if (compact) 12 else 20), 0, dp(if (compact) 10 else 16)) }
        status = label("", 13).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        toolbar.addView(status, LinearLayout.LayoutParams(0, dp(52), 1f))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accent); visibility = View.GONE
        }
        toolbar.addView(progress, LinearLayout.LayoutParams(dp(20), dp(20)).apply { setMargins(dp(8), 0, dp(12), 0) })
        scan = actionButton("기기 검색", true, ::discover).apply { minWidth = dp(122) }
        toolbar.addView(scan, LinearLayout.LayoutParams(dp(132), dp(52)).apply { setMargins(dp(12), 0, 0, 0) })
        content.addView(toolbar)
        val panels = LinearLayout(this).apply { orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        if (compact) content.addView(ScrollView(this).apply { addView(panels) }, LinearLayout.LayoutParams(-1, 0, 1f))
        else content.addView(panels, LinearLayout.LayoutParams(-1, 0, 1f))
        val left = devicePanel("등록된 기기")
        val right = devicePanel("주변 기기")
        if (compact) {
            panels.addView(right.first, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            panels.addView(left.first, LinearLayout.LayoutParams(-1, -2))
        } else {
            panels.addView(left.first, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(0, 0, dp(12), 0) })
            panels.addView(right.first, LinearLayout.LayoutParams(0, -1, 1.15f))
        }
        registeredList = left.second; nearbyList = right.second
        registeredCount = left.third; nearbyCount = right.third
        registeredEmpty = emptyState("bluetooth", "등록된 기기가 없습니다", "새 기기를 페어링하면\n여기에 표시됩니다.")
        nearbyEmpty = emptyState("search", "새 기기를 찾아보세요", "기기를 페어링 모드로 두고\n위의 ‘기기 검색’을 선택하세요.")
        nearbyHint = (nearbyEmpty.getChildAt(2) as TextView)
        registeredList.addView(registeredEmpty); nearbyList.addView(nearbyEmpty)
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(16), 0, 0) }
        footer.addView(label("방향키  이동     확인  선택", 11), LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(label("뒤로  나가기", 11))
        content.addView(footer)
    }
    private fun selectTab(apps: Boolean) {
        appTabSelected = apps
        bluetoothPage.visibility = if (apps) View.GONE else View.VISIBLE
        appsPage.visibility = if (apps) View.VISIBLE else View.GONE
        bluetoothTab.isSelected = !apps; appsTab.isSelected = apps
        for (tab in listOf(bluetoothTab, appsTab)) tab.background = rounded(if (tab.isSelected) 0xff253346.toInt() else 0xff222832.toInt(), 10, if (tab.hasFocus()) accent else Color.TRANSPARENT)
        if (apps) appsPage.refresh()
    }
    private fun devicePanel(title: String): Triple<LinearLayout, LinearLayout, TextView> {
        val panel = column().apply { background = rounded(surface, 16); setPadding(dp(18), dp(18), dp(18), dp(10)) }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label(title, 17, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        val count = label("0", 13, muted)
        heading.addView(count)
        panel.addView(heading)
        panel.addView(Space(this), LinearLayout.LayoutParams(1, dp(16)))
        val list = column()
        if (compact) panel.addView(list)
        else panel.addView(ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return Triple(panel, list, count)
    }
    private fun emptyState(symbol: String, title: String, hint: String) = column().apply {
        gravity = Gravity.CENTER; setPadding(dp(6), dp(20), dp(6), dp(20))
        addView(SymbolView(symbol, muted), LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(label(title, 14, white, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(9)) })
        addView(label(hint, 12).apply { gravity = Gravity.CENTER; setLineSpacing(dp(4).toFloat(), 1f) })
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun rounded(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(2), stroke)
    }
    private fun label(text: String, size: Int, color: Int = muted, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color); includeFontPadding = false
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun actionButton(text: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 13f; isAllCaps = false; isFocusable = true
        minHeight = dp(if (compact) 36 else 46); minimumHeight = dp(if (compact) 36 else 46); setPadding(dp(12), dp(8), dp(12), dp(8))
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(-1, dp(if (compact) 36 else 46)).apply { topMargin = dp(if (compact) 5 else 8) }
        fun restyle(focused: Boolean) {
            background = rounded(if (primary) accent else if (isSelected) 0xff253346.toInt() else 0xff222832.toInt(), 10, if (focused) accent else Color.TRANSPARENT)
            setTextColor(if (primary) backgroundColor else white)
            elevation = if (focused) dp(4).toFloat() else 0f
        }
        restyle(false); setOnFocusChangeListener { _, focus -> restyle(focus) }; setOnClickListener { action() }
    }
    private inner class DeviceRow(val address: String) : LinearLayout(this@MainActivity) {
        val title = label("", 15, white, true)
        val detail = label("", 11)
        val symbol = SymbolView("bluetooth", accent)
        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true; descendantFocusability = FOCUS_BLOCK_DESCENDANTS
            minimumHeight = dp(78); setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            addView(symbol, LinearLayout.LayoutParams(dp(30), dp(34)).apply { rightMargin = dp(12) })
            val text = column()
            title.maxLines = 2; title.ellipsize = TextUtils.TruncateAt.END
            text.addView(title); text.addView(detail.apply { setPadding(0, dp(6), 0, 0); maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            background = rounded(0xff252b35.toInt(), 12)
            setOnFocusChangeListener { _, focused ->
                background = rounded(if (focused) 0xff303e52.toInt() else 0xff252b35.toInt(), 12, if (focused) accent else Color.TRANSPARENT)
            }
            setOnClickListener { try { found[address]?.let(::pair) ?: bondedDevice(address)?.let(::pair) } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("기기 선택", error) } }
        }
        fun bind(name: String?, bondState: Int, kind: String) {
            val pending = name == null && names.isPending(address)
            title.text = name ?: if (pending) "이름 확인 중…" else "블루투스 기기 ${address.takeLast(5)}"
            val state = when (bondState) {
                BluetoothDevice.BOND_BONDED -> if (address in linked) "기기 링크 연결됨" else "등록됨"
                BluetoothDevice.BOND_BONDING -> "페어링 중"
                else -> if (pending) "잠시 기다려주세요" else "페어링 가능"
            }
            detail.text = if (name == null) "$state  ${address.takeLast(8)}" else state
            symbol.kind = kind; symbol.invalidate()
            contentDescription = "${title.text}, $state, $address"
        }
    }
    private inner class SymbolView(var kind: String, private val color: Int) : View(this@MainActivity) {
        private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas); pen.color = color
            canvas.save(); val scale = minOf(width, height) / 32f
            canvas.translate((width - 32 * scale) / 2, (height - 32 * scale) / 2); canvas.scale(scale, scale)
            when (kind) {
                "search" -> { canvas.drawCircle(13f, 13f, 8f, pen); canvas.drawLine(19f, 19f, 27f, 27f, pen) }
                "audio" -> {
                    canvas.drawArc(5f, 4f, 27f, 28f, 180f, 180f, false, pen)
                    canvas.drawRoundRect(4f, 15f, 10f, 26f, 2f, 2f, pen); canvas.drawRoundRect(22f, 15f, 28f, 26f, 2f, 2f, pen)
                }
                "input" -> { canvas.drawRoundRect(3f, 8f, 29f, 25f, 3f, 3f, pen)
                    for (y in listOf(13f, 17f)) for (x in listOf(8f, 13f, 18f, 23f)) canvas.drawPoint(x, y, pen)
                    canvas.drawLine(11f, 21f, 21f, 21f, pen)
                }
                else -> { val path = Path().apply { moveTo(10f, 8f); lineTo(23f, 22f); lineTo(16f, 28f); lineTo(16f, 4f); lineTo(23f, 10f); lineTo(10f, 24f) }; canvas.drawPath(path, pen) }
            }
            canvas.restore()
        }
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
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); refresh()
    }
    override fun onStart() {
        super.onStart()
        if (preview) { showPreview(); return }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED); addAction(BluetoothDevice.ACTION_CLASS_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED); addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // Bluetooth broadcasts may originate from a privileged process outside our UID.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, filter)
        registered = true; refresh()
    }
    override fun onResume() { super.onResume(); if (::status.isInitialized) refresh(); if (::updater.isInitialized && !preview) updater.resume(); if (::appsPage.isInitialized && appTabSelected) appsPage.resume() }
    override fun onDestroy() { if (::updater.isInitialized) updater.close(); if (::bluetoothActions.isInitialized) bluetoothActions.close(); if (::appsPage.isInitialized) appsPage.close(); uiHandler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onStop() {
        uiHandler.removeCallbacks(nameTick); uiHandler.removeCallbacks(scanWatchdog)
        if (registered) { unregisterReceiver(receiver); registered = false }
        try { if (access()) adapter?.cancelDiscovery() } catch (_: SecurityException) { }
        super.onStop()
    }
    private fun message(text: String) { status.text = text }
    private fun refresh() {
        if (preview) return
        permission.visibility = if (access()) View.GONE else View.VISIBLE
        val bt = adapter
        if (bt == null) { badge.text = "지원 안 됨"; message("이 기기는 블루투스를 지원하지 않습니다."); scan.isEnabled = false; power.visibility = View.GONE; return }
        if (!access()) { badge.text = "권한 필요"; message("왼쪽의 ‘권한 허용’을 선택해 시작하세요."); return }
        try {
            searching = bt.isDiscovering && !scanExpired
            power.visibility = if (bt.isEnabled) View.GONE else View.VISIBLE
            badge.text = if (bt.isEnabled) "● 켜짐" else "꺼짐"
            scan.text = if (searching) "검색 중지" else if (searched) "다시 검색" else "기기 검색"
            progress.visibility = if (searching) View.VISIBLE else View.GONE
            nearbyHint.text = when { searching -> "기기를 페어링 모드로 설정하세요.\n이름이 확인되면 자동으로 표시됩니다."
                searched -> "기기를 TV 가까이에 두고\n페어링 모드를 확인한 뒤 다시 검색하세요."
                else -> "기기를 페어링 모드로 두고\n위의 ‘기기 검색’을 선택하세요." }
            (nearbyEmpty.getChildAt(1) as TextView).text = when { searching -> "주변 기기를 찾고 있습니다"; searched -> "발견된 기기가 없습니다"; else -> "새 기기를 찾아보세요" }
            message(when { !bt.isEnabled -> "왼쪽에서 블루투스를 켜주세요."
                searching -> "주변 기기를 찾고 있습니다. 이름은 잠시 후 표시될 수 있습니다."
                searched -> "검색이 끝났습니다. 기기를 선택해 페어링하세요."
                else -> "기기를 페어링 모드로 설정한 뒤 검색하세요." })
            renderDevices()
        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("블루투스 상태", error) }
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
                uiHandler.removeCallbacks(scanWatchdog); uiHandler.postDelayed(scanWatchdog, 35_000)
                refresh()
            } else message("검색을 시작하지 못했습니다. 잠시 후 다시 시도하세요.")
        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("기기 검색", error) }
    }
    private fun pair(device: BluetoothDevice) {
        if (!access()) { requestAccess(); return }
        try {
            val title = names.get(device.address) ?: device.name ?: "이름 확인 중"
            val bonded = device.bondState == BluetoothDevice.BOND_BONDED
            val actions = if (bonded) arrayOf("연결 시도", "연결 해제 시도", "등록 삭제", "기기 정보")
                else arrayOf("페어링", "다른 페어링 방식", "기기 정보")
            AlertDialog.Builder(this).setTitle(title).setItems(actions) { _, index ->
                try {
                    if (bonded) when (index) {
                        0 -> bluetoothActions.connection(device, true)
                        1 -> bluetoothActions.connection(device, false)
                        2 -> AlertDialog.Builder(this).setTitle("등록을 삭제할까요?")
                            .setMessage("리모컨이나 키보드를 삭제하면 조작할 수 없게 될 수 있습니다. 다른 입력 수단을 준비하세요.")
                            .setPositiveButton("삭제") { _, _ -> bluetoothActions.forget(device) }.setNegativeButton("취소", null).show()
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
            }.setNegativeButton("닫기", null).show()
        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("기기 선택", error) }
    }
    private fun deviceDetails(device: BluetoothDevice) {
        try {
            val name = names.get(device.address) ?: device.name ?: "이름 확인 중"
            AlertDialog.Builder(this).setTitle(name).setMessage("${device.address}\n${if (device.bondState == BluetoothDevice.BOND_BONDED) "등록됨" else "등록되지 않음"}\n${if (device.address in linked) "기기 링크 연결됨" else "링크 연결 상태 미확인"}\n\n등록 상태와 오디오·입력 프로필의 실제 연결 상태는 다를 수 있습니다.")
                .setPositiveButton("닫기", null).show()
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
    @Deprecated("Platform result callback used for vendor compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 610) { appsPage.exportResult(resultCode, data); return }
        if (requestCode != 501) return
        val address = pendingCompanionAddress; pendingCompanionAddress = null
        if (resultCode != RESULT_OK || address == null) { message("대체 페어링이 취소되었습니다."); return }
        try {
            adapter?.getRemoteDevice(address)?.let { device -> found[address] = device; bluetoothActions.bond(device) }
        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("대체 페어링 완료", error) }
    }
    private fun operationError(operation: String, error: Exception) {
        Diagnostics.record(this, operation, error)
        message("$operation 실패: ${error.javaClass.simpleName}. 오류 기록을 확인하세요.")
    }
    private fun showDiagnostics() {
        val text = label(Diagnostics.read(this), 12).apply { setPadding(dp(16), dp(16), dp(16), dp(16)); setTextIsSelectable(true) }
        AlertDialog.Builder(this).setTitle("오류 기록")
            .setView(ScrollView(this).apply { addView(text) })
            .setNeutralButton("복사") { _, _ -> getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("GenieBreaker diagnostics", text.text)) }
            .setPositiveButton("닫기", null).show()
    }
    private fun bondedDevice(address: String): BluetoothDevice? {
        if (!access()) return null
        return try { adapter?.bondedDevices?.firstOrNull { it.address == address } }
        catch (_: SecurityException) { null }
    }
    private fun renderDevices() {
        val bt = adapter ?: return
        if (!access()) return
        try {
            val bonded = bt.bondedDevices
            val nearby = found.values.filter { it.bondState != BluetoothDevice.BOND_BONDED }
            val visible = (bonded + nearby).map { it.address }.toSet()
            val focusedAddress = (currentFocus as? DeviceRow)?.address
            rows.keys.filter { it !in visible }.toList().forEach { key ->
                rows.remove(key)?.let { (it.parent as? LinearLayout)?.removeView(it) }
            }
            fun reconcile(device: BluetoothDevice, parent: LinearLayout) {
                names.seed(device.address, device.name)
                val row = rows.getOrPut(device.address) { DeviceRow(device.address) }
                if (row.parent !== parent) {
                    (row.parent as? LinearLayout)?.removeView(row); parent.addView(row)
                }
                val kind = when (device.bluetoothClass?.majorDeviceClass) {
                    BluetoothClass.Device.Major.AUDIO_VIDEO -> "audio"
                    BluetoothClass.Device.Major.PERIPHERAL -> "input"
                    else -> "bluetooth"
                }
                row.bind(names.get(device.address), device.bondState, kind)
            }
            // Rows keep their identity and position when a late name arrives.
            bonded.forEach { reconcile(it, registeredList) }
            nearby.forEach { reconcile(it, nearbyList) }
            registeredCount.text = bonded.size.toString(); nearbyCount.text = nearby.size.toString()
            registeredEmpty.visibility = if (bonded.isEmpty()) View.VISIBLE else View.GONE
            nearbyEmpty.visibility = if (nearby.isEmpty()) View.VISIBLE else View.GONE
            if (focusedAddress != null && currentFocus !is DeviceRow) (rows[focusedAddress] ?: scan).requestFocus()
            uiHandler.removeCallbacks(nameTick)
            if (rows.keys.any { names.isPending(it) }) uiHandler.postDelayed(nameTick, 1_000)
        } catch (error: SecurityException) { operationError("블루투스 권한", error) } catch (error: Exception) { operationError("기기 목록", error) }
    }
    /** Debug-only fixtures for instrumentation screenshots, never shown in release builds. */
    private fun showPreview() {
        badge.text = "● 켜짐"; permission.visibility = View.GONE; power.visibility = View.GONE
        message("주변 기기를 찾고 있습니다. 이름은 잠시 후 표시될 수 있습니다.")
        scan.text = "검색 중지"; progress.visibility = View.VISIBLE
        registeredEmpty.visibility = View.GONE; nearbyEmpty.visibility = View.GONE
        registeredCount.text = "2"; nearbyCount.text = "3"
        fun sample(parent: LinearLayout, address: String, name: String?, bond: Int, kind: String) {
            val row = DeviceRow(address); names.merge(address, name); row.bind(name, bond, kind); rows[address] = row; parent.addView(row)
        }
        sample(registeredList, "00:11:22:33:44:01", "거실 헤드폰", BluetoothDevice.BOND_BONDED, "audio")
        sample(registeredList, "00:11:22:33:44:02", "무선 키보드", BluetoothDevice.BOND_BONDED, "input")
        sample(nearbyList, "00:11:22:33:44:03", "WH-1000XM5", BluetoothDevice.BOND_NONE, "audio")
        sample(nearbyList, "00:11:22:33:44:04", null, BluetoothDevice.BOND_NONE, "bluetooth")
        sample(nearbyList, "00:11:22:33:44:05", "Bluetooth Keyboard", BluetoothDevice.BOND_NONE, "input")
    }
    // An instrumentation hook also verifies that updating a title preserves view identity and focus.
    fun previewLateName(): Boolean {
        check(BuildConfig.DEBUG && preview)
        val row = rows.getValue("00:11:22:33:44:04")
        row.requestFocus()
        names.merge(row.address, "Galaxy Buds FE")
        row.bind(names.get(row.address), BluetoothDevice.BOND_NONE, "audio")
        return row.hasFocus() && rows[row.address] === row && row.title.text.toString() == "Galaxy Buds FE"
    }
    fun previewAppsTab() { check(BuildConfig.DEBUG && preview); selectTab(true); appsTab.requestFocus() }
}
