package sa.databridge.oneplus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final String DATA = "/storage/emulated/0/Android/data";
    private static final String OBB = "/storage/emulated/0/Android/obb";
    private IFileAccess remote;
    private String currentPath = DATA;
    private TextView status;
    private TextView breadcrumb;
    private LinearLayout folderList;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        runOnUiThread(() -> {
            if (grantResult == PackageManager.PERMISSION_GRANTED) connect();
            else showStatus("لم يتم منح إذن Shizuku.");
        });
    };

    private final Shizuku.OnBinderReceivedListener receivedListener = () -> runOnUiThread(this::connect);
    private final Shizuku.OnBinderDeadListener deadListener = () -> runOnUiThread(() -> {
        remote = null;
        showStatus("توقف Shizuku. شغّله مجددًا ثم اضغط اتصال.");
    });
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            remote = IFileAccess.Stub.asInterface(binder);
            runOnUiThread(() -> { showStatus("متصل بـ Shizuku. يمكنك فتح المجلدات مباشرة."); load(currentPath); });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            remote = null;
            runOnUiThread(() -> showStatus("انقطع اتصال خدمة الملفات"));
        }
    };

    private TextView txt(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        t.setPadding(12, 13, 12, 13); t.setTextDirection(View.TEXT_DIRECTION_RTL);
        return t;
    }
    private Button button(String value, LinearLayout target, Runnable action) {
        Button b = new Button(this);
        b.setText(value); b.setAllCaps(false);
        target.addView(b, new LinearLayout.LayoutParams(-1, -2));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11, 17, 28));
        getWindow().setNavigationBarColor(Color.rgb(11, 17, 28));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(16, 25, 16, 12);
        root.setBackgroundColor(Color.rgb(11, 17, 28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView headline = txt("DataBridge", 28, Color.WHITE);
        headline.setGravity(Gravity.CENTER); root.addView(headline);
        TextView summary = txt("مستكشف Android/data بدون تنبيهات متكررة داخل هذا التطبيق — يتطلب Shizuku", 14, 0xFFAAB8CD);
        summary.setGravity(Gravity.CENTER); root.addView(summary);
        status = txt("بدء الاتصال...", 14, 0xFF86EFAC); root.addView(status);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        root.addView(controls);
        button("اتصال / منح إذن Shizuku", controls, this::connect);
        button("📁 Android/data", controls, () -> load(DATA));
        button("📂 Android/obb", controls, () -> load(OBB));
        button("رجوع للمجلد السابق", controls, this::goBack);
        breadcrumb = txt(DATA, 13, 0xFFE5E7EB);
        breadcrumb.setTextDirection(View.TEXT_DIRECTION_LTR); root.addView(breadcrumb);
        ScrollView scroll = new ScrollView(this);
        folderList = new LinearLayout(this); folderList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(folderList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = txt("وضع آمن: نسخ الملفات إلى Downloads فقط، ولا يحذف الملفات الأصلية. لا يغير صلاحيات النظام أو تنبيهات التطبيقات الأخرى.", 12, 0xFF8E9AAF);
        root.addView(footer);
        setContentView(root);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(receivedListener);
        Shizuku.addBinderDeadListener(deadListener);
    }

    private void showStatus(String message) { status.setText(message); }
    private void connect() {
        try {
            if (!Shizuku.pingBinder()) {
                showStatus("Shizuku غير نشط. افتح Shizuku ثم فعّل Wireless debugging وشغّل الخدمة.");
                return;
            }
            if (Shizuku.isPreV11()) { showStatus("يلزم Shizuku v11 أو أحدث."); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                showStatus("وافق على طلب صلاحية Shizuku.");
                Shizuku.requestPermission(7); return;
            }
            showStatus("جاري بدء خدمة الملفات...");
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(getPackageName(), RemoteFilesService.class.getName()))
                    .daemon(false).processNameSuffix("file_bridge").debuggable(false).version(1);
            Shizuku.bindUserService(args, serviceConnection);
        } catch (Exception ex) { showStatus("فشل الاتصال: " + ex.getMessage()); }
    }

    private void load(String path) {
        if (remote == null) { showStatus("يلزم الاتصال بـ Shizuku أولًا."); return; }
        currentPath = path;
        breadcrumb.setText(path);
        folderList.removeAllViews(); folderList.addView(txt("جاري قراءة المجلد...", 15, Color.LTGRAY));
        executor.execute(() -> {
            String[] names;
            try { names = remote.list(path); }
            catch (Exception e) { names = new String[]{"E\t" + e.getMessage()}; }
            final String[] rows = names;
            runOnUiThread(() -> {
                if (!path.equals(currentPath)) return;
                folderList.removeAllViews();
                if (rows.length == 0) { folderList.addView(txt("مجلد فارغ، أو لا توجد عناصر ظاهرة.", 16, Color.WHITE)); return; }
                for (String row : rows) {
                    String[] parts = row.split("\t", 2);
                    if (parts.length != 2) continue;
                    String kind = parts[0]; String filename = parts[1];
                    if ("E".equals(kind)) { folderList.addView(txt("تعذّر الوصول: " + filename, 15, 0xFFFDA4AF)); continue; }
                    boolean directory = "D".equals(kind);
                    TextView entry = txt((directory ? "📁 " : "📄 ") + filename, 15, Color.WHITE);
                    entry.setBackgroundColor(0xFF182336);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
                    lp.setMargins(0, 0, 0, 6); folderList.addView(entry, lp);
                    String target = path + "/" + filename;
                    entry.setOnClickListener(v -> {
                        if (directory) load(target);
                        else confirmCopy(target);
                    });
                }
            });
        });
    }
    private void confirmCopy(String fullPath) {
        new AlertDialog.Builder(this)
                .setTitle("نسخ الملف")
                .setMessage("نسخ الملف إلى مجلد Downloads بدون تغيير النسخة الأصلية؟\n" + fullPath)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("نسخ", (dialog, which) -> {
                    if (remote == null) return;
                    showStatus("جاري النسخ إلى Downloads...");
                    executor.execute(() -> {
                        String result;
                        try { result = remote.copyToDownloads(fullPath); }
                        catch (Exception e) { result = "ERROR: " + e.getMessage(); }
                        final String msg = result;
                        runOnUiThread(() -> showStatus(msg));
                    });
                }).show();
    }
    private void goBack() {
        if (currentPath.equals(DATA) || currentPath.equals(OBB)) return;
        int end = currentPath.lastIndexOf('/');
        if (end > 0) load(currentPath.substring(0, end));
    }
    @Override public void onBackPressed() {
        if (!currentPath.equals(DATA) && !currentPath.equals(OBB)) goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(receivedListener);
        Shizuku.removeBinderDeadListener(deadListener);
        executor.shutdownNow();
        super.onDestroy();
    }
}
