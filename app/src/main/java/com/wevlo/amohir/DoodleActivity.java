package com.wevlo.amohir;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class DoodleActivity extends AppCompatActivity {

    public static Bitmap resultBitmap;

    private ImageView ivBasePhoto;
    private DoodleView doodleView;
    private Bitmap baseBmp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doodle);

        ivBasePhoto = findViewById(R.id.ivBasePhoto);
        doodleView = findViewById(R.id.doodleView);

        String uriStr = getIntent().getStringExtra("imageUri");
        if (uriStr != null) {
            loadBaseBitmap(Uri.parse(uriStr));
        } else {
            finish();
            return;
        }

        findViewById(R.id.btnCloseDoodle).setOnClickListener(v -> finish());
        findViewById(R.id.btnUndo).setOnClickListener(v -> doodleView.undo());
        findViewById(R.id.btnDoneDoodle).setOnClickListener(v -> finishAndSend());

        findViewById(R.id.colorRed).setOnClickListener(v -> doodleView.setColor(Color.parseColor("#EF4444")));
        findViewById(R.id.colorGreen).setOnClickListener(v -> doodleView.setColor(Color.parseColor("#25D366")));
        findViewById(R.id.colorBlue).setOnClickListener(v -> doodleView.setColor(Color.parseColor("#3B82F6")));
        findViewById(R.id.colorYellow).setOnClickListener(v -> doodleView.setColor(Color.parseColor("#FACC15")));
        findViewById(R.id.colorWhite).setOnClickListener(v -> doodleView.setColor(Color.WHITE));
    }

    private void loadBaseBitmap(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            Bitmap orig = BitmapFactory.decodeStream(is);
            if (orig == null) {
                finish();
                return;
            }
            int w = orig.getWidth(), h = orig.getHeight(), max = 1080;
            if (w > h && w > max) { h = Math.round((float) h * max / w); w = max; }
            else if (h > max) { w = Math.round((float) w * max / h); h = max; }
            baseBmp = Bitmap.createScaledBitmap(orig, w, h, true);
            ivBasePhoto.setImageBitmap(baseBmp);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void finishAndSend() {
        if (baseBmp == null) {
            finish();
            return;
        }
        Bitmap finalBmp = doodleView.hasStrokes() ? doodleView.applyDoodleOn(baseBmp) : baseBmp;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        finalBmp.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        Intent out = new Intent();
        out.putExtra("doodledB64", b64);
        setResult(RESULT_OK, out);
        finish();
    }
}
