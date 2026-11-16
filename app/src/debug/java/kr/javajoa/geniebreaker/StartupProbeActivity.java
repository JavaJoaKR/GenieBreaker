package kr.javajoa.geniebreaker;
public class StartupProbeActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        int id = getResources().getIdentifier("probe_ready", "string", getPackageName());
        if (id == 0) throw new IllegalStateException("Cloned resource identity was lost");
        android.widget.TextView ready = new android.widget.TextView(this);
        ready.setText(getResources().getString(id)); ready.setTextSize(26);
        setContentView(ready);
    }
}
