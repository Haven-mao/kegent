package com.haven.kegent.update;

import android.app.Application;
import android.os.*;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Downloads survive rotation. Leaving the page pauses safely and retains a resumable partial file. */
public final class UpdateViewModel extends AndroidViewModel {
    public enum Phase { IDLE, CHECKING, CURRENT, UNPUBLISHED, AVAILABLE, INCOMPATIBLE, DOWNLOADING, VERIFYING, READY, ERROR }
    public static final class State {
        public final Phase phase; public final UpdateManifest offer; public final String message; public final long done;
        State(Phase phase, UpdateManifest offer, String message, long done) { this.phase = phase; this.offer = offer; this.message = message; this.done = done; }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(Phase.IDLE, null, "", 0));
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger revision = new AtomicInteger();
    private volatile UpdateTransfer transfer;
    private Future<?> task;
    private boolean started;
    public UpdateViewModel(@NonNull Application app) { super(app); }
    public LiveData<State> state() { return state; }
    public State current() { return state.getValue(); }
    public File directory() { return new File(getApplication().getFilesDir(), "app-updates"); }
    public String source() { return getApplication().getSharedPreferences("app_update", 0).getString("source", UpdateTransfer.DEFAULT_SOURCE); }
    public void setSource(String source) throws Exception {
        UpdateManifest.https(source); cancel(); getApplication().getSharedPreferences("app_update", 0).edit().putString("source", source).apply();
    }
    private void publish(int expected, Phase phase, UpdateManifest offer, String message, long done) {
        main.post(() -> { if (revision.get() == expected) state.setValue(new State(phase, offer, message, done)); });
    }
    private int begin(Phase phase, UpdateManifest offer, String message) {
        int expected = revision.incrementAndGet(); if (task != null) task.cancel(true); if (transfer != null) transfer.cancel();
        transfer = new UpdateTransfer(); state.setValue(new State(phase, offer, message, 0)); return expected;
    }
    private void error(int expected, UpdateManifest offer, Exception error) {
        if (error instanceof CancellationException || error instanceof InterruptedException || Thread.currentThread().isInterrupted()) return;
        String text = error instanceof java.net.SocketTimeoutException ? "连接超时，请检查网络后重试"
                : error instanceof java.net.UnknownHostException ? "无法连接更新服务，请检查网络或稍后重试"
                : error instanceof java.net.ConnectException || error instanceof javax.net.ssl.SSLException ? "无法建立安全的更新连接，请稍后重试"
                : error.getMessage() == null ? "更新未完成，请稍后重试" : error.getMessage();
        publish(expected, error instanceof UpdateTransfer.NoRelease ? Phase.UNPUBLISHED : Phase.ERROR, offer, text, 0);
    }
    public void start() {
        if (started) return; started = true;
        int expected = begin(Phase.CHECKING, null, "正在读取更新状态…");
        task = worker.submit(() -> {
            try {
                File metadata = new File(directory(), "offer.json");
                if (metadata.isFile() && metadata.length() <= 128 * 1024) {
                    UpdateManifest saved = new UpdateManifest(new JSONObject(new String(Files.readAllBytes(metadata.toPath()), StandardCharsets.UTF_8)));
                    saved.requirePackage(getApplication().getPackageName());
                    if (saved.versionCode > ApkVerifier.version(ApkVerifier.installed(getApplication())) && saved.minSdk <= Build.VERSION.SDK_INT) {
                        File ready = UpdateTransfer.apk(directory(), saved);
                        if (ready.isFile()) {
                            try { transfer.verifyDigest(ready, saved); ApkVerifier.verify(getApplication(), ready, saved); publish(expected, Phase.READY, saved, "更新包已下载并通过校验，可以安装", saved.sizeBytes); return; }
                            catch (Exception invalid) { if (Thread.currentThread().isInterrupted()) return; Files.deleteIfExists(ready.toPath()); }
                        }
                        publish(expected, Phase.AVAILABLE, saved, "上次更新尚未完成，可继续下载或重新检查", 0); return;
                    }
                    prune(null);
                }
            } catch (Exception ignored) { if (Thread.currentThread().isInterrupted()) return; }
            checkFromWorker(expected, transfer, source());
        });
    }
    public void check() {
        int expected = begin(Phase.CHECKING, null, "正在检查新版本…"); UpdateTransfer client = transfer; String address = source();
        task = worker.submit(() -> checkFromWorker(expected, client, address));
    }
    private void checkFromWorker(int expected, UpdateTransfer client, String address) {
        try {
            UpdateManifest offer = client.manifest(address); offer.requirePackage(getApplication().getPackageName());
            if (offer.versionCode <= ApkVerifier.version(ApkVerifier.installed(getApplication()))) {
                publish(expected, Phase.CURRENT, offer, "当前版本已是此更新源的最新版本", 0); return;
            }
            if (offer.minSdk > Build.VERSION.SDK_INT) { publish(expected, Phase.INCOMPATIBLE, offer, "新版本需要 Android API " + offer.minSdk + " 或更高系统", 0); return; }
            saveOffer(offer); publish(expected, Phase.AVAILABLE, offer, "发现新版本", 0);
        } catch (Exception e) { error(expected, null, e); }
    }
    public void download() {
        State before = current(); if (before == null || before.offer == null) return; UpdateManifest offer = before.offer;
        if (offer.minSdk > Build.VERSION.SDK_INT) return;
        int expected = begin(Phase.DOWNLOADING, offer, "正在下载更新…"); UpdateTransfer client = transfer;
        task = worker.submit(() -> {
            try {
                offer.requirePackage(getApplication().getPackageName());
                if (offer.versionCode <= ApkVerifier.version(ApkVerifier.installed(getApplication()))) throw new IOException("该更新版本已安装，请重新检查");
                saveOffer(offer); prune(offer);
                final long[] last = {0};
                File file = client.download(directory(), offer, (done, total) -> {
                    long now = SystemClock.elapsedRealtime();
                    if (now - last[0] >= 200 || done == total) { last[0] = now; publish(expected, Phase.DOWNLOADING, offer, "正在下载更新…", done); }
                });
                publish(expected, Phase.VERIFYING, offer, "正在验证安装包…", offer.sizeBytes);
                ApkVerifier.verify(getApplication(), file, offer);
                publish(expected, Phase.READY, offer, "更新包已下载并通过校验，可以安装", offer.sizeBytes);
            } catch (Exception e) {
                if (!(e instanceof CancellationException) && !Thread.currentThread().isInterrupted()) {
                    // A complete but invalid candidate must not repeatedly resume into the same failure.
                    try { Files.deleteIfExists(UpdateTransfer.apk(directory(), offer).toPath()); } catch (IOException ignored) { }
                }
                error(expected, offer, e);
            }
        });
    }
    public void prepareInstall(Runnable ready) {
        State before = current(); if (before == null || before.offer == null) return; UpdateManifest offer = before.offer;
        int expected = begin(Phase.VERIFYING, offer, "正在检查安装包…"); UpdateTransfer client = transfer;
        task = worker.submit(() -> {
            try {
                client.verifyDigest(UpdateTransfer.apk(directory(), offer), offer); ApkVerifier.verify(getApplication(), UpdateTransfer.apk(directory(), offer), offer);
                main.post(() -> { if (revision.get() == expected) { state.setValue(new State(Phase.READY, offer, "即将打开系统安装确认", offer.sizeBytes)); ready.run(); } });
            } catch (Exception e) { error(expected, offer, e); }
        });
    }
    public void cancel() {
        State before = current(); revision.incrementAndGet(); if (task != null) task.cancel(true); if (transfer != null) transfer.cancel();
        if (before != null) state.setValue(new State(before.phase == Phase.READY ? Phase.READY : before.offer != null ? Phase.AVAILABLE : Phase.IDLE,
                before.offer, before.offer != null ? "已暂停，更新包与下载进度保留" : "已取消检查", before.done));
    }
    private void saveOffer(UpdateManifest offer) throws Exception {
        File dir = directory(); if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法保存更新信息");
        File temp = new File(dir, "offer.json.tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) { out.write(offer.json().toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        Files.move(temp.toPath(), new File(dir, "offer.json").toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    private void prune(UpdateManifest keep) {
        File[] files = directory().listFiles(); if (files == null) return;
        String prefix = keep == null ? "" : "update-" + keep.sha256;
        for (File file : files) if ((file.getName().startsWith("update-") && (keep == null || !file.getName().startsWith(prefix)))
                || keep == null && file.getName().startsWith("offer.json")) file.delete();
    }
    @Override protected void onCleared() { revision.incrementAndGet(); if (transfer != null) transfer.cancel(); if (task != null) task.cancel(true); worker.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
