package kr.javajoa.geniebreaker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
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
    private lateinit var status: TextView
    private lateinit var scan: Button
    private lateinit var power: Button
    private lateinit var permission: Button
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
    private var registered = false
    private var searching = false
    private var searched = false
    private var preview = false
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
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> { searching = true; refresh() }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { searching = false; refresh() }
                    else -> refresh()
                }
            } catch (_: SecurityException) { message("근처 기기 권한이 필요합니다. 왼쪽에서 권한을 허용하세요.") }
        }
    }
    @Suppress("DEPRECATION")
    private fun deviceExtra(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        preview = BuildConfig.DEBUG && intent.getBooleanExtra("ui_preview", false)
        adapter = getSystemService(BluetoothManager::class.java)?.adapter
        savedInstanceState?.getBundle("names")?.let { bundle -> bundle.keySet().forEach { names.merge(it, bundle.getString(it)) } }
        buildScreen()
        scan.requestFocus()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putBundle("names", Bundle().apply { names.snapshot().forEach { (key, value) -> putString(key, value) } })
        super.onSaveInstanceState(out)
    }
    private fun buildScreen() {
        val root = column().apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(32), dp(28), dp(32), dp(24)); setBackgroundColor(backgroundColor)
        }
        setContentView(root)
        val sidebar = column().apply { setPadding(0, dp(6), dp(24), 0) }
        root.addView(sidebar, LinearLayout.LayoutParams(dp(190), -1))
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        brand.addView(SymbolView("bluetooth", accent), LinearLayout.LayoutParams(dp(25), dp(30)))
        brand.addView(label("GenieBreaker", 17, white, true).apply { setPadding(dp(8), 0, 0, 0) })
        sidebar.addView(brand)
        sidebar.addView(label("기기 설정", 12).apply { setPadding(0, dp(7), 0, dp(32)) })
        sidebar.addView(label("블루투스", 17, accent, true).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(0xff253346.toInt(), 10)
        })
        sidebar.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        permission = actionButton("권한 허용", false, ::requestAccess)
        power = actionButton("블루투스 켜기", false, ::enableBluetooth)
        sidebar.addView(permission); sidebar.addView(power)
        sidebar.addView(label("MA4000  /  Android 14", 11).apply { setPadding(dp(4), dp(15), 0, 0) })
        root.addView(View(this).apply { setBackgroundColor(0xff353a44.toInt()) }, LinearLayout.LayoutParams(dp(1), -1))
        val content = column().apply { setPadding(dp(28), 0, 0, 0) }
        root.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = column()
        titles.addView(label("블루투스", 32, white, true))
        titles.addView(label("헤드폰부터 키보드까지, TV에 새 기기를 등록하세요.", 13).apply { setPadding(0, dp(7), 0, 0) })
        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        badge = label("확인 중", 12, accent).apply {
            gravity = Gravity.CENTER; setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(0xff253346.toInt(), 20)
        }
        heading.addView(badge)
        content.addView(heading)
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(20), 0, dp(16)) }
        status = label("", 13).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        toolbar.addView(status, LinearLayout.LayoutParams(0, dp(52), 1f))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accent); visibility = View.GONE
        }
        toolbar.addView(progress, LinearLayout.LayoutParams(dp(20), dp(20)).apply { setMargins(dp(8), 0, dp(12), 0) })
        scan = actionButton("기기 검색", true, ::discover).apply { minWidth = dp(122) }
        toolbar.addView(scan, LinearLayout.LayoutParams(dp(132), dp(52)).apply { setMargins(dp(12), 0, 0, 0) })
        content.addView(toolbar)
        val panels = LinearLayout(this)
        content.addView(panels, LinearLayout.LayoutParams(-1, 0, 1f))
        val left = devicePanel("등록된 기기", "다시 사용할 기기를 확인하세요.")
        val right = devicePanel("주변 기기", "선택하면 페어링을 요청합니다.")
        panels.addView(left.first, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(0, 0, dp(12), 0) })
        panels.addView(right.first, LinearLayout.LayoutParams(0, -1, 1.15f))
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
    private fun devicePanel(title: String, subtitle: String): Triple<LinearLayout, LinearLayout, TextView> {
        val panel = column().apply { background = rounded(surface, 16); setPadding(dp(18), dp(18), dp(18), dp(10)) }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label(title, 17, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        val count = label("0", 13, muted)
        heading.addView(count)
        panel.addView(heading)
        panel.addView(label(subtitle, 11).apply { setPadding(0, dp(6), 0, dp(14)) })
        val list = column()
        panel.addView(ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
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
        minHeight = dp(46); minimumHeight = dp(46); setPadding(dp(12), dp(8), dp(12), dp(8))
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) }
        fun restyle(focused: Boolean) {
            background = rounded(if (primary) accent else 0xff292e38.toInt(), 10, if (focused) white else Color.TRANSPARENT)
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
            setOnClickListener { found[address]?.let(::pair) ?: bondedDevice(address)?.let(::pair) }
        }
        fun bind(name: String?, bondState: Int, kind: String) {
            title.text = name ?: "이름 확인 중…"
            val state = when (bondState) {
                BluetoothDevice.BOND_BONDED -> "등록됨"
                BluetoothDevice.BOND_BONDING -> "페어링 중"
                else -> if (name == null) "이름을 기다리는 중" else "페어링 가능"
            }
            detail.text = "$state  •  ${address.takeLast(8)}"
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
    private fun requestAccess() = requestPermissions(if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); refresh()
    }
    override fun onStart() {
        super.onStart()
        if (preview) { showPreview(); return }
        val filter = IntentFilter().apply {
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
    override fun onResume() { super.onResume(); if (::status.isInitialized) refresh() }
    override fun onStop() {
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
            searching = bt.isDiscovering
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
        } catch (_: SecurityException) { message("근처 기기 권한이 필요합니다.") }
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
            found.clear(); searched = true; renderDevices()
            if (bt.startDiscovery()) refresh() else message("검색을 시작하지 못했습니다. 잠시 후 다시 시도하세요.")
        } catch (_: SecurityException) { message("검색 권한이 없습니다") }
    }
    private fun pair(device: BluetoothDevice) {
        if (!access()) { requestAccess(); return }
        try {
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                AlertDialog.Builder(this).setTitle(names.get(device.address) ?: device.name ?: "등록된 기기")
                    .setMessage("등록된 기기입니다. 페어링과 실제 연결은 별개입니다. 연결·해제·등록 삭제는 추후 권한 확장 기능으로 제공할 예정입니다.")
                    .setPositiveButton("닫기", null).show(); return
            }
            adapter?.cancelDiscovery()
            if (device.bondState == BluetoothDevice.BOND_BONDING) { message("페어링 진행 중입니다"); return }
            message(if (device.createBond()) "페어링 요청됨 · 시스템 확인 창을 확인하세요" else "페어링 요청 실패 · 기기의 페어링 모드를 확인하세요")
        } catch (_: SecurityException) { message("페어링 권한이 없습니다") }
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
        } catch (_: SecurityException) { message("기기 목록을 읽을 권한이 없습니다.") }
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
        return row.hasFocus() && rows[row.address] === row && row.title.text == "Galaxy Buds FE"
    }
}
