package com.wevlo.amohir;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginPhone, etLoginName;
    private Button btnContinue;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public static String normalizePhone(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        return digits;
    }

    public static String getShortPhone(String digits) {
        if (digits.length() > 8) return digits.substring(digits.length() - 8);
        return digits;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ক্র্যাশ হলে এরর সেভ করে রাখার হ্যান্ডলার
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            String err = Log.getStackTraceString(e);
            getSharedPreferences("CrashLog", MODE_PRIVATE).edit().putString("err", err).apply();
            System.exit(1);
        });

        try {
            String lastCrash = getSharedPreferences("CrashLog", MODE_PRIVATE).getString("err", "");
            if (!lastCrash.isEmpty()) {
                getSharedPreferences("CrashLog", MODE_PRIVATE).edit().clear().apply();
                new AlertDialog.Builder(this)
                        .setTitle("Previous Crash Error")
                        .setMessage(lastCrash)
                        .setPositiveButton("OK", null)
                        .show();
            }

            SharedPreferences prefs = getSharedPreferences("DivineWA", MODE_PRIVATE);
            String savedPhone = prefs.getString("my_phone", "");
            if (!savedPhone.isEmpty()) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            }

            setContentView(R.layout.activity_login);
            initFirebase();

            etLoginPhone = findViewById(R.id.etLoginPhone);
            etLoginName = findViewById(R.id.etLoginName);
            btnContinue = findViewById(R.id.btnContinue);

            btnContinue.setOnClickListener(v -> attemptLogin());
        } catch (Throwable t) {
            showCrashOnScreen(Log.getStackTraceString(t));
        }
    }

    private void showCrashOnScreen(String text) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.parseColor("#8B0000"));
        tv.setPadding(32, 64, 32, 32);
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        setContentView(sv);
    }

    private void initFirebase() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey("AIzaSyAmLJ5Vdd6Pqm-FrPfANvjy1TZkhG1mxns")
                    .setApplicationId("1:367706014019:web:f4e33a639f4573262baff6")
                    .setProjectId("good-865a2")
                    .setStorageBucket("good-865a2.firebasestorage.app")
                    .setGcmSenderId("367706014019")
                    .build();
            FirebaseApp.initializeApp(this, options);
        }
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    private void attemptLogin() {
        String phone = normalizePhone(etLoginPhone.getText().toString());
        String name = etLoginName.getText().toString().trim();

        if (phone.length() < 7) {
            Toast.makeText(this, "সঠিক মোবাইল নম্বর দিন (কমপক্ষে ৭ সংখ্যা)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (name.isEmpty()) {
            Toast.makeText(this, "আপনার নাম লিখুন", Toast.LENGTH_SHORT).show();
            return;
        }

        btnContinue.setEnabled(false);
        btnContinue.setText("Connecting...");

        if (auth.getCurrentUser() != null) {
            saveAccountAndProceed(phone, name);
        } else {
            auth.signInAnonymously()
                    .addOnSuccessListener(res -> saveAccountAndProceed(phone, name))
                    .addOnFailureListener(e -> {
                        btnContinue.setEnabled(true);
                        btnContinue.setText("AGREE AND CONTINUE");
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void saveAccountAndProceed(String phone, String name) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid", phone);
        userMap.put("phone", phone);
        userMap.put("shortPhone", getShortPhone(phone));
        userMap.put("name", name);
        userMap.put("about", "Hey there! I am using WhatsApp.");
        userMap.put("online", true);
        userMap.put("lastSeen", System.currentTimeMillis());

        db.collection("users").document(phone).set(userMap, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    getSharedPreferences("DivineWA", MODE_PRIVATE).edit()
                            .putString("my_phone", phone)
                            .putString("name", name)
                            .putString("about", "Hey there! I am using WhatsApp.")
                            .apply();

                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnContinue.setEnabled(true);
                    btnContinue.setText("AGREE AND CONTINUE");
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}