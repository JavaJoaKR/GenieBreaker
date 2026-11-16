package kr.javajoa.geniebreaker;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** TV-first classic Bluetooth discovery and public-API pairing. */
public final class MainActivity extends Activity {
    private BluetoothAdapter adapter;
    private TextView status;
    private LinearLayout devices;
    private Button scan;
    private final Map<String, BluetoothDevice> found = new LinkedHashMap<>();
    private boolean registered;
    private static final int INK = Color.rgb(12, 20, 29), MINT = Color.rgb(116, 237, 207);
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (d != null) { found.put(d.getAddress(), d); renderDevices(); }
                } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                    renderDevices();
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                    message(state == BluetoothDevice.BOND_BONDED ? "페어링 완료 · 연결은 기기와 시스템이 처리합니다" : state == BluetoothDevice.BOND_BONDING ? "페어링 중 · TV의 확인 창을 확인하세요" : "페어링 해제 또는 취소됨");
                } else refresh();
            } catch (SecurityException e) { message("블루투스 권한이 필요합니다. 권한 허용을 선택하세요."); }
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(42), dp(32), dp(42), dp(28)); root.setBackgroundColor(INK);
        setContentView(root);
        LinearLayout sidebar = new LinearLayout(this); sidebar.setOrientation(LinearLayout.VERTICAL);
        root.addView(sidebar, new LinearLayout.LayoutParams(dp(240), -1));
        sidebar.addView(label("GENIE / BREAKER", 14, MINT));
        TextView title = label("Bluetooth", 34, Color.WHITE); title.setPadding(0, dp(30), 0, dp(12)); sidebar.addView(title);
        sidebar.addView(label("TV와 기기를\n더 간편하게 연결하세요.", 17, 0xff9eacbb));
        sidebar.addView(button("블루투스 켜기", () -> enableBluetooth(), "enable"));
        sidebar.addView(button("권한 허용", () -> requestAccess(), "permission"));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(36), 0, 0, 0);
        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        status = label("", 16, MINT); status.setMinHeight(dp(50)); content.addView(status);
        scan = button("주변 기기 검색", () -> discover(), "scan"); content.addView(scan);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        devices = new LinearLayout(this); devices.setOrientation(LinearLayout.VERTICAL); scroll.addView(devices);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(label("방향키로 이동  ·  확인 버튼으로 선택  ·  뒤로 가기로 돌아가기", 12, 0xff9eacbb));
        scan.requestFocus();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, int color) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); t.setPadding(0, dp(8), 0, dp(8)); return t;
    }
    private Button button(String text, Runnable action, String tag) {
        Button b = new Button(this); b.setText(text); b.setTextSize(15); b.setAllCaps(false); b.setGravity(Gravity.CENTER_VERTICAL); b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setTag(tag); b.setMinHeight(dp(58)); b.setFocusable(true); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(dp(3), dp(6), dp(3), dp(6)); b.setLayoutParams(lp);
        b.setOnFocusChangeListener((v, focus) -> style(b, focus)); style(b, false);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void style(Button b, boolean focus) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(focus ? MINT : 0xff1b2b3c); bg.setCornerRadius(dp(12)); bg.setStroke(dp(2), focus ? Color.WHITE : 0xff2a3a4c);
        b.setBackground(bg); b.setTextColor(focus ? INK : Color.WHITE);
    }
    private boolean access() {
        if (Build.VERSION.SDK_INT >= 31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestAccess() {
        requestPermissions(Build.VERSION.SDK_INT >= 31 ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT} : new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) { super.onRequestPermissionsResult(request, permissions, results); refresh(); }
    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(); f.addAction(BluetoothDevice.ACTION_FOUND); f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED); f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED); f.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED); f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        // Bluetooth broadcasts originate from a privileged Bluetooth process, not our UID.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED); else registerReceiver(receiver, f);
        registered = true; refresh();
    }
    @Override protected void onResume() { super.onResume(); if (status != null) refresh(); }
    @Override protected void onStop() {
        if (registered) { unregisterReceiver(receiver); registered = false; }
        try { if (adapter != null && access()) adapter.cancelDiscovery(); } catch (SecurityException ignored) { }
        super.onStop();
    }
    private void message(String text) { status.setText(text); }
    private void refresh() {
        if (adapter == null) { message("이 기기는 블루투스를 지원하지 않습니다"); scan.setEnabled(false); return; }
        if (!access()) { message("시작하려면 ‘권한 허용’을 선택하세요"); return; }
        try {
            boolean enabled = adapter.isEnabled();
            scan.setText(adapter.isDiscovering() ? "검색 중지" : "주변 기기 검색");
            message(!enabled ? "블루투스가 꺼져 있습니다" : adapter.isDiscovering() ? "주변 기기를 찾고 있습니다…" : "검색 준비 완료 · 기기를 페어링 모드로 설정하세요");
            renderDevices();
        } catch (SecurityException e) { message("블루투스 권한이 필요합니다"); }
    }
    private void enableBluetooth() {
        if (adapter == null) { message("블루투스를 지원하지 않습니다"); return; }
        if (!access()) { requestAccess(); return; }
        try { if (adapter.isEnabled()) { message("이미 켜져 있습니다"); return; } startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); }
        catch (ActivityNotFoundException | SecurityException e) { message("기기에서 켜기 요청을 차단했습니다. 이 펌웨어에서는 앱의 켜기 요청을 사용할 수 없습니다."); }
    }
    private void discover() {
        if (adapter == null) return;
        if (!access()) { requestAccess(); return; }
        try {
            if (!adapter.isEnabled()) { enableBluetooth(); return; }
            if (adapter.isDiscovering()) { adapter.cancelDiscovery(); return; }
            if (Build.VERSION.SDK_INT < 31) {
                LocationManager l = getSystemService(LocationManager.class);
                if (l == null || !(l.isProviderEnabled(LocationManager.GPS_PROVIDER) || l.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) { message("이 Android 버전은 검색에 위치 서비스가 필요합니다. 위치 서비스가 꺼져 있어 검색할 수 없습니다."); return; }
            }
            found.clear(); renderDevices();
            if (!adapter.startDiscovery()) message("검색을 시작하지 못했습니다. 잠시 후 다시 시도하세요."); else refresh();
        } catch (SecurityException e) { message("권한이 거부되었습니다. 권한 허용을 확인하세요."); }
    }
    private void renderDevices() {
        if (!access() || adapter == null) return;
        try {
        View focused = getCurrentFocus(); Object tag = focused == null ? null : focused.getTag();
        boolean listFocus = focused != null && focused.getParent() == devices;
        devices.removeAllViews();
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        devices.addView(label("등록된 기기  /  " + bonded.size(), 16, 0xff9eacbb));
        for (BluetoothDevice d : bonded) addDevice(d);
        if (bonded.isEmpty()) devices.addView(label("아직 등록된 기기가 없습니다", 15, Color.WHITE));
        devices.addView(label("주변 기기", 16, 0xff9eacbb));
        int count = 0;
        for (BluetoothDevice d : found.values()) if (d.getBondState() != BluetoothDevice.BOND_BONDED) { addDevice(d); count++; }
        if (count == 0) devices.addView(label("검색을 눌러 새 기기를 찾아보세요", 15, Color.WHITE));
        if (listFocus) { View replacement = tag == null ? null : devices.findViewWithTag(tag); if (replacement != null) replacement.requestFocus(); else scan.requestFocus(); }
        } catch (SecurityException e) { message("권한이 변경되었습니다. 근처 기기 권한을 확인하세요."); }
    }
    private void addDevice(BluetoothDevice d) {
        if (!access()) return;
        try {
        String name = d.getName(); if (name == null || name.trim().isEmpty()) name = "이름 없는 기기";
        String state = d.getBondState() == BluetoothDevice.BOND_BONDED ? "등록됨" : d.getBondState() == BluetoothDevice.BOND_BONDING ? "페어링 중" : "선택하여 페어링";
        devices.addView(button(name + "   ·   " + state + "\n" + d.getAddress(), () -> pair(d), d.getAddress()));
        } catch (SecurityException e) { message("기기 정보를 읽을 권한이 없습니다."); }
    }
    private void pair(BluetoothDevice d) {
        try {
            if (d.getBondState() == BluetoothDevice.BOND_BONDED) {
                new AlertDialog.Builder(this).setTitle(d.getName()).setMessage("등록된 기기입니다. 페어링과 실제 연결은 별개입니다. 현재 버전은 검색·페어링을 지원하며, 연결·해제·등록 삭제는 추후 권한 확장 기능으로 제공할 예정입니다.").setPositiveButton("닫기", null).show(); return;
            }
            adapter.cancelDiscovery();
            if (d.getBondState() == BluetoothDevice.BOND_BONDING) { message("페어링 진행 중입니다"); return; }
            message(d.createBond() ? "페어링 요청됨 · 시스템 확인 창을 확인하세요" : "페어링 요청 실패 · 기기의 페어링 모드를 확인하세요");
        } catch (SecurityException e) { message("페어링 권한이 없습니다"); }
    }
}
