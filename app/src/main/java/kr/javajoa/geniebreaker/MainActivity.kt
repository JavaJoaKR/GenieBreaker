package kr.javajoa.geniebreaker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

class MainActivity : Activity() {
    private var adapter: BluetoothAdapter? = null
    private lateinit var status: TextView
    private lateinit var devices: LinearLayout
    private lateinit var scan: Button
    private val found = linkedMapOf<String, BluetoothDevice>()
    private var registered = false
    private val ink = Color.rgb(12, 20, 29)
    private val mint = Color.rgb(116, 237, 207)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          try {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) { found[device.address] = device; renderDevices() }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    renderDevices()
                    message(when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                        BluetoothDevice.BOND_BONDED -> "페어링 완료 · 연결은 기기와 시스템이 처리합니다"
                        BluetoothDevice.BOND_BONDING -> "페어링 중 · TV의 확인 창을 확인하세요"
                        else -> "페어링 해제 또는 취소됨"
                    })
                }
                else -> refresh()
            }
          } catch (_: SecurityException) { message("블루투스 권한이 필요합니다") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val root = column().apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(42), dp(32), dp(42), dp(28)); setBackgroundColor(ink)
        }
        setContentView(root)
        val sidebar = column()
        root.addView(sidebar, LinearLayout.LayoutParams(dp(240), -1))
        sidebar.addView(label("GENIE / BREAKER", 14, mint))
        sidebar.addView(label("Bluetooth", 34, Color.WHITE).apply { setPadding(0, dp(30), 0, dp(12)) })
        sidebar.addView(label("TV와 기기를\n더 간편하게 연결하세요.", 17))
        sidebar.addView(button("블루투스 켜기", "enable", ::enableBluetooth))
        sidebar.addView(button("권한 허용", "permission", ::requestAccess))
        val content = column().apply { setPadding(dp(36), 0, 0, 0) }
        root.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        status = label("", 16, mint).apply { minHeight = dp(50) }
        content.addView(status)
        scan = button("주변 기기 검색", "scan", ::discover)
        content.addView(scan)
        devices = column()
        content.addView(ScrollView(this).apply { isFillViewport = true; addView(devices) }, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(label("방향키로 이동  ·  확인 버튼으로 선택  ·  뒤로 가기로 돌아가기", 12))
        scan.requestFocus()
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun label(text: String, size: Int, color: Int = 0xff9eacbb.toInt()) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color); setPadding(0, dp(8), 0, dp(8))
    }
    private fun button(text: String, tag: String, action: () -> Unit) = Button(this).apply {
        this.text = text; this.tag = tag; textSize = 15f; isAllCaps = false
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(10))
        minHeight = dp(58); isFocusable = true; setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(3), dp(6), dp(3), dp(6)) }
        setOnFocusChangeListener { _, focused -> style(this, focused) }; style(this, false)
        setOnClickListener { action() }
    }
    private fun style(button: Button, focused: Boolean) {
        button.background = GradientDrawable().apply {
            setColor(if (focused) mint else 0xff1b2b3c.toInt()); cornerRadius = dp(12).toFloat()
            setStroke(dp(2), if (focused) Color.WHITE else 0xff2a3a4c.toInt())
        }
        button.setTextColor(if (focused) ink else Color.WHITE)
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
        val filter = IntentFilter().apply {
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
    private inline fun guarded(block: () -> Unit) {
        try { block() } catch (_: SecurityException) { message("블루투스 권한이 필요합니다. 권한 허용을 확인하세요.") }
    }
    private fun refresh() {
        val bt = adapter ?: run { message("이 기기는 블루투스를 지원하지 않습니다"); scan.isEnabled = false; return }
        if (!access()) { message("시작하려면 ‘권한 허용’을 선택하세요"); return }
        try {
            scan.text = if (bt.isDiscovering) "검색 중지" else "주변 기기 검색"
            message(when { !bt.isEnabled -> "블루투스가 꺼져 있습니다"
                bt.isDiscovering -> "주변 기기를 찾고 있습니다…"
                else -> "검색 준비 완료 · 기기를 페어링 모드로 설정하세요" })
            renderDevices()
        } catch (_: SecurityException) { message("블루투스 권한이 필요합니다") }
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
            found.clear(); renderDevices()
            if (bt.startDiscovery()) refresh() else message("검색을 시작하지 못했습니다. 잠시 후 다시 시도하세요.")
        } catch (_: SecurityException) { message("검색 권한이 없습니다") }
    }
    private fun renderDevices() {
        val bt = adapter ?: return
        if (!access()) return
        try {
            val focused = currentFocus
            val focusTag = focused?.tag
            val listFocus = focused?.parent === devices
            devices.removeAllViews()
            val bonded = bt.bondedDevices
            devices.addView(label("등록된 기기  /  ${bonded.size}", 16))
            bonded.forEach(::addDevice)
            if (bonded.isEmpty()) devices.addView(label("아직 등록된 기기가 없습니다", 15, Color.WHITE))
            devices.addView(label("주변 기기", 16))
            val nearby = found.values.filter { it.bondState != BluetoothDevice.BOND_BONDED }
            nearby.forEach(::addDevice)
            if (nearby.isEmpty()) devices.addView(label("검색을 눌러 새 기기를 찾아보세요", 15, Color.WHITE))
            if (listFocus) (devices.findViewWithTag<View>(focusTag) ?: scan).requestFocus()
        } catch (_: SecurityException) { message("기기 목록을 읽을 권한이 없습니다") }
    }
    private fun addDevice(device: BluetoothDevice) {
        if (!access()) return
        try {
            val name = device.name?.takeIf { it.isNotBlank() } ?: "이름 없는 기기"
            val state = when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> "등록됨"
                BluetoothDevice.BOND_BONDING -> "페어링 중"
                else -> "선택하여 페어링"
            }
            devices.addView(button("$name   ·   $state\n${device.address}", device.address) { pair(device) })
        } catch (_: SecurityException) { message("기기 정보를 읽을 권한이 없습니다") }
    }
    private fun pair(device: BluetoothDevice) {
        if (!access()) { requestAccess(); return }
        try {
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                AlertDialog.Builder(this).setTitle(device.name)
                    .setMessage("등록된 기기입니다. 페어링과 실제 연결은 별개입니다. 연결·해제·등록 삭제는 추후 권한 확장 기능으로 제공할 예정입니다.")
                    .setPositiveButton("닫기", null).show(); return
            }
            adapter?.cancelDiscovery()
            if (device.bondState == BluetoothDevice.BOND_BONDING) { message("페어링 진행 중입니다"); return }
            message(if (device.createBond()) "페어링 요청됨 · 시스템 확인 창을 확인하세요" else "페어링 요청 실패 · 기기의 페어링 모드를 확인하세요")
        } catch (_: SecurityException) { message("페어링 권한이 없습니다") }
    }
}
