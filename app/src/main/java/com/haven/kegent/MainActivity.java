package com.haven.kegent;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean dark = getSharedPreferences("workspace", MODE_PRIVATE).getBoolean("theme_dark", false);
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        startActivity(new Intent(this, FakeGPSActivity.class));
        finish();
    }
}
