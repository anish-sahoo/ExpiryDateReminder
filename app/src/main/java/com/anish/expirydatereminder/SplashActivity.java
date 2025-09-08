package com.anish.expirydatereminder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.anish.expirydatereminder.utils.ViewUtils;

import java.util.Objects;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        Objects.requireNonNull(getSupportActionBar()).hide();
        ViewUtils.applyInsets(findViewById(R.id.splash_activity), getWindow());

        final Intent i = new Intent(SplashActivity.this, MainActivity.class);
        new Handler().postDelayed(() -> {
            startActivity(i);
            finish();
        }, 2000);

    }
}