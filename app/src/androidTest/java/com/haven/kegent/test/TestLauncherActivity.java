package com.haven.kegent.test;

/** Launcher metadata for consumer emulators that cannot index a headless test APK. */
public final class TestLauncherActivity extends android.app.Activity {
    @Override
    protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        finish();
    }
}
