package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

/**
 * TextImageExporter
 * ─────────────────
 * Renders the composed fictional-script text onto a branded PNG
 * using the custom font, then returns a content:// URI suitable
 * for sharing via WhatsApp / Telegram / any share target.
 */
public class TextImageExporter {

    private static final String TAG = "TextImageExporter";
    private static final int IMG_W      = 1080;
    private static final int PADDING    = 64;
    private static final int FONT_SIZE  = 80;
    private static final int LINE_H     = 100;
    private static final int CORNER     = 32;

    public static Uri export(Context ctx, String text) throws Exception {
        // ── Load font ───────────────────────────────────────────────────────
        Typeface tf;
        try {
            tf = Typeface.createFromAsset(ctx.getAssets(), "fonts/fictional_font.ttf");
        } catch (Exception e) {
            Log.w(TAG, "Font missing, using sans-serif for export");
            tf = Typeface.SANS_SERIF;
        }

        // ── Measure text height ─────────────────────────────────────────────
        String[] lines = text.split("\n", -1);
        int contentH = PADDING * 2 + lines.length * LINE_H + 80 /* footer */;
        int IMG_H    = Math.max(400, contentH);

        // ── Draw ────────────────────────────────────────────────────────────
        Bitmap bmp = Bitmap.createBitmap(IMG_W, IMG_H, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(bmp);

        // Background gradient simulation (dark purple)
        c.drawColor(0xFF1A0A3C);

        // Glow card
        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setColor(0xFF2D1B6B);
        RectF card = new RectF(PADDING / 2f, PADDING / 2f,
                IMG_W - PADDING / 2f, IMG_H - 60);
        c.drawRoundRect(card, CORNER, CORNER, cardPaint);

        // Border
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setColor(0xFF8B5CF6);
        c.drawRoundRect(card, CORNER, CORNER, borderPaint);

        // Text
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(tf);
        textPaint.setTextSize(FONT_SIZE);
        textPaint.setColor(0xFFE9D5FF);
        textPaint.setTextAlign(Paint.Align.CENTER);

        float y = PADDING + LINE_H;
        for (String line : lines) {
            if (!line.isEmpty()) {
                c.drawText(line, IMG_W / 2f, y, textPaint);
            }
            y += LINE_H;
        }

        // Footer label
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC));
        footerPaint.setTextSize(28f);
        footerPaint.setColor(0xFF7C3AED);
        footerPaint.setTextAlign(Paint.Align.CENTER);
        c.drawText("✦ Fictional Script — install font to read ✦",
                IMG_W / 2f, IMG_H - 20, footerPaint);

        // ── Save to cache ───────────────────────────────────────────────────
        File dir = new File(ctx.getCacheDir(), "fictional_shares");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, "fictional_message.png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        bmp.recycle();

        // ── Return FileProvider URI ──────────────────────────────────────────
        return FileProvider.getUriForFile(ctx,
                ctx.getPackageName() + ".fileprovider", out);
    }
}
