package com.fictional.ime;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * FictionalKeyboardView
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * CHARACTERS: The font maps Ethiopic codepoints → fictional glyphs.
 * Row layout taken directly from the calligraphr sheet (image):
 *
 *   Row 0 (circles w/ cross):  ሀ ሁ ሂ ሃ ሄ ህ  (U+1200–U+1205)
 *   Row 1 (anchor variants):   ሠ ሡ ሢ ሣ ሤ ሥ ሦ ሧ (U+1220–U+1227)
 *   Row 2 (arrow-T variants):  ሐ ሑ ሒ ሓ ሔ ሕ ሖ ሗ (U+1210–U+1217)
 *   Row 3 (O/ring variants):   ደ ዱ ዲ ዳ ዴ ድ ዶ ዷ (U+12F0–U+12F7)
 *   Row 4 (omega variants):    ጠ ጡ ጢ ጣ ጤ ጥ ጦ ጧ (U+1360–U+1367... actually U+1320–1327)
 *
 * Only codepoints visible in the calligraphr sheet are used.
 * Shift shows second half of each row.
 *
 * PANELS: Main keyboard | Emoji panel | Clipboard panel
 * THEMES: Dark (default) | Light
 */
public class FictionalKeyboardView extends View {

    private static final String TAG = "FictionalKB";

    // ── Sentinel codes ───────────────────────────────────────────────────────
    public static final String KEY_BACKSPACE    = "__BS__";
    public static final String KEY_ENTER        = "__EN__";
    public static final String KEY_SPACE        = "__SP__";
    public static final String KEY_SHIFT        = "__SH__";
    public static final String KEY_EMOJI_TOGGLE = "__EM__";
    public static final String KEY_CLIP_TOGGLE  = "__CL__";
    public static final String KEY_THEME_TOGGLE = "__TH__";
    public static final String KEY_SHARE        = "__SHARE__";

    // ── Interfaces ───────────────────────────────────────────────────────────
    public interface KeyEventListener  { void onKeyPressed(String symbol); }
    public interface ShareRequestListener { void onShareRequested(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Characters — Ethiopic codepoints that the custom font remaps
    // Only the ones confirmed visible in the calligraphr sheet
    // ─────────────────────────────────────────────────────────────────────────

    // Row 0: Circle-cross family  ሀ ሁ ሂ ሃ ሄ ህ  (6 chars, all visible)
    private static final String[] R0 = {
        "\u1200","\u1201","\u1202","\u1203","\u1204","\u1205"
    };

    // Row 1a (lower shift=off): anchor family  ሠ ሡ ሢ ሣ  (first 4)
    // Row 1b (upper shift=on):  anchor family  ሤ ሥ ሦ ሧ  (next 4)
    private static final String[] R1A = {
        "\u1220","\u1221","\u1222","\u1223"
    };
    private static final String[] R1B = {
        "\u1224","\u1225","\u1226","\u1227"
    };

    // Row 2a: arrow-T/cross family  ሐ ሑ ሒ ሓ
    // Row 2b:                       ሔ ሕ ሖ ሗ
    private static final String[] R2A = {
        "\u1210","\u1211","\u1212","\u1213"
    };
    private static final String[] R2B = {
        "\u1214","\u1215","\u1216","\u1217"
    };

    // Row 3a: O/ring family  ደ ዱ ዲ ዳ
    // Row 3b:                ዴ ድ ዶ ዷ
    private static final String[] R3A = {
        "\u12F0","\u12F1","\u12F2","\u12F3"
    };
    private static final String[] R3B = {
        "\u12F4","\u12F5","\u12F6","\u12F7"
    };

    // Row 4a: omega/horseshoe family  ጠ ጡ ጢ ጣ
    // Row 4b:                         ጤ ጥ ጦ ጧ
    private static final String[] R4A = {
        "\u1320","\u1321","\u1322","\u1323"
    };
    private static final String[] R4B = {
        "\u1324","\u1325","\u1326","\u1327"
    };

    // ── Emoji set ─────────────────────────────────────────────────────────────
    private static final String[] EMOJIS = {
        "😀","😂","🥹","😍","🤩","😎","🥳","😜","🤔","😴",
        "❤️","🧡","💛","💚","💙","💜","🖤","🤍","💯","✨",
        "🔥","⚡","🌙","⭐","🌈","🎉","🎊","🎁","🏆","👑",
        "👍","👋","🙌","🤝","✌️","🫶","💪","🙏","👀","💀",
        "🌸","🌺","🌻","🍀","🌿","🌊","🏔","🌅","🦋","🐉"
    };

    // ── Key model ────────────────────────────────────────────────────────────
    private static class Key {
        String label, code;
        float  x, y, w, h;
        boolean special, pressed;

        Key(String label, String code) {
            this.label   = label;
            this.code    = code;
            this.special = false;
        }

        Key special(boolean s) { this.special = s; return this; }
        boolean hit(float px, float py) {
            return px >= x && px <= x+w && py >= y && py <= y+h;
        }
    }

    // ── State ────────────────────────────────────────────────────────────────
    private final List<Key> mMainKeys  = new ArrayList<>();
    private final List<Key> mEmojiKeys = new ArrayList<>();
    private final List<Key> mClipKeys  = new ArrayList<>();

    private KeyEventListener    mKeyListener;
    private ShareRequestListener mShareListener;

    private boolean mShifted    = false;
    private boolean mDark       = true;   // default dark
    private int     mPanel      = 0;      // 0=main 1=emoji 2=clipboard

    private Typeface mFontFictional;
    private Typeface mFontSystem;

    // Paints
    private final Paint mBgPaint      = new Paint();
    private final Paint mCardPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mKeyPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mKeyPressPaint= new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSpecialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mActivePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSmallPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGradPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShadowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Dims
    private float mKM, mKR, mRH, mFS, mSFS;  // margin, corner, rowH, fontSize, smallFontSize

    // Clipboard
    private final List<String> mClipItems = new ArrayList<>();

    public FictionalKeyboardView(Context ctx) {
        super(ctx);
        loadFont(ctx);
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // needed for setShadowLayer
    }

    private void loadFont(Context ctx) {
        mFontSystem = Typeface.SANS_SERIF;
        try {
            mFontFictional = Typeface.createFromAsset(
                    ctx.getAssets(), "fonts/fictional_font.ttf");
            Log.d(TAG, "Custom font loaded OK");
        } catch (Exception e) {
            Log.w(TAG, "Font missing → fallback. " + e.getMessage());
            mFontFictional = mFontSystem;
        }
    }

    public void setKeyEventListener(KeyEventListener l)     { mKeyListener   = l; }
    public void setShareRequestListener(ShareRequestListener l) { mShareListener = l; }

    public void reset() {
        mShifted = false;
        mPanel   = 0;
        for (Key k : mMainKeys)  k.pressed = false;
        for (Key k : mEmojiKeys) k.pressed = false;
        invalidate();
    }

    // ── Colour helpers (theme-aware) ──────────────────────────────────────────
    private int cBg()        { return mDark ? 0xFF0F0520 : 0xFFF0EBF8; }
    private int cCard()      { return mDark ? 0xFF1E0D40 : 0xFFE8E0F5; }
    private int cKey()       { return mDark ? 0xFF2D1B6B : 0xFFD4C8EE; }
    private int cKeyPress()  { return mDark ? 0xFF7C3AED : 0xFF9B59D6; }
    private int cSpecial()   { return mDark ? 0xFF180A35 : 0xFFC8B8E8; }
    private int cActive()    { return mDark ? 0xFF6D28D9 : 0xFF7C3AED; }
    private int cBorder()    { return mDark ? 0xFF4C1D95 : 0xFF9B7DD4; }
    private int cLabel()     { return mDark ? 0xFFEDE9FE : 0xFF1E0D40; }
    private int cLabelSpec() { return mDark ? 0xFFA78BFA : 0xFF4C1D95; }
    private int cAccent()    { return 0xFF8B5CF6; }

    private void applyTheme() {
        setBackgroundColor(cBg());

        mCardPaint.setColor(cCard());
        mCardPaint.setStyle(Paint.Style.FILL);

        mKeyPaint.setColor(cKey());
        mKeyPaint.setStyle(Paint.Style.FILL);

        mKeyPressPaint.setColor(cKeyPress());
        mKeyPressPaint.setStyle(Paint.Style.FILL);

        mSpecialPaint.setColor(cSpecial());
        mSpecialPaint.setStyle(Paint.Style.FILL);

        mActivePaint.setColor(cActive());
        mActivePaint.setStyle(Paint.Style.FILL);

        mBorderPaint.setColor(cBorder());
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(1.5f);

        mLabelPaint.setColor(cLabel());
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mLabelPaint.setTypeface(mFontFictional);
        mLabelPaint.setTextSize(mFS);

        mSmallPaint.setColor(cLabelSpec());
        mSmallPaint.setTextAlign(Paint.Align.CENTER);
        mSmallPaint.setTypeface(mFontSystem);
        mSmallPaint.setTextSize(mSFS);

        mShadowPaint.setColor(cAccent());
        mShadowPaint.setStyle(Paint.Style.STROKE);
        mShadowPaint.setStrokeWidth(2f);
        mShadowPaint.setAlpha(60);
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        mKM  = w * 0.008f;
        mKR  = w * 0.018f;
        mRH  = h / 6.2f;
        mFS  = mRH * 0.46f;
        mSFS = mRH * 0.33f;

        applyTheme();
        buildMainKeys(w, h);
        buildEmojiKeys(w, h);
        buildClipKeys(w, h);
    }

    // ── build main keyboard ────────────────────────────────────────────────────
    private void buildMainKeys(int W, int H) {
        mMainKeys.clear();
        float gY  = H * 0.01f;  // top gap
        float gap = H * 0.008f;
        float y   = gY;

        // Row 0: 6 chars  (circle-cross family)
        y = addCharRow(mMainKeys, R0, null, W, y, 6);
        y += gap;
        // Row 1: 4+4 chars (anchor family, shift toggles)
        y = addCharRow(mMainKeys, R1A, R1B, W, y, 4);
        y += gap;
        // Row 2: 4+4 (arrow-T family)
        y = addCharRow(mMainKeys, R2A, R2B, W, y, 4);
        y += gap;
        // Row 3: 4+4 (O/ring family) — centred
        y = addCentredCharRow(mMainKeys, R3A, R3B, W, y, 4);
        y += gap;
        // Row 4: 4+4 (omega family) — centred
        y = addCentredCharRow(mMainKeys, R4A, R4B, W, y, 4);
        y += gap;

        // Bottom action row
        buildActionRow(mMainKeys, W, y);
    }

    /** Uniform row — no shift variant. */
    private float addCharRow(List<Key> list, String[] lo, String[] hi,
                              int W, float y, int count) {
        float avail = W - mKM * (count + 1);
        float kW    = avail / count;
        float x     = mKM;
        // We store the pair in alternating Keys controlled by mShifted at draw-time
        // Strategy: store a custom tag in label vs code
        for (int i = 0; i < count; i++) {
            String loChar = (lo  != null && i < lo.length)  ? lo[i]  : "";
            String hiChar = (hi  != null && i < hi.length)  ? hi[i]  : loChar;
            // Encode as "lo|hi" in code; parse at draw/input time
            Key k = new Key(loChar, loChar + "|" + hiChar);
            k.x = x; k.y = y; k.w = kW; k.h = mRH;
            list.add(k);
            x += kW + mKM;
        }
        return y + mRH;
    }

    private float addCentredCharRow(List<Key> list, String[] lo, String[] hi,
                                     int W, float y, int count) {
        float kW    = (W - mKM * (count + 1)) / (count + 0.5f);
        float total = kW * count + mKM * (count - 1);
        float x     = (W - total) / 2f;
        for (int i = 0; i < count; i++) {
            String loChar = (lo != null && i < lo.length) ? lo[i] : "";
            String hiChar = (hi != null && i < hi.length) ? hi[i] : loChar;
            Key k = new Key(loChar, loChar + "|" + hiChar);
            k.x = x; k.y = y; k.w = kW; k.h = mRH;
            list.add(k);
            x += kW + mKM;
        }
        return y + mRH;
    }

    private void buildActionRow(List<Key> list, int W, float y) {
        // [EMOJI][CLIP][SHIFT][  SPACE  ][SHARE][⌫][↵]
        float unit  = (W - mKM * 8) / 9f;
        float x     = mKM;

        Key emoji = new Key("😊", KEY_EMOJI_TOGGLE).special(true);
        emoji.x = x; emoji.y = y; emoji.w = unit; emoji.h = mRH; list.add(emoji);
        x += unit + mKM;

        Key clip = new Key("📋", KEY_CLIP_TOGGLE).special(true);
        clip.x = x; clip.y = y; clip.w = unit; clip.h = mRH; list.add(clip);
        x += unit + mKM;

        Key theme = new Key("☀", KEY_THEME_TOGGLE).special(true);
        theme.x = x; theme.y = y; theme.w = unit; theme.h = mRH; list.add(theme);
        x += unit + mKM;

        Key shift = new Key("⇧", KEY_SHIFT).special(true);
        shift.x = x; shift.y = y; shift.w = unit; shift.h = mRH; list.add(shift);
        x += unit + mKM;

        Key space = new Key("·", KEY_SPACE).special(true);
        space.x = x; space.y = y; space.w = unit * 2f + mKM; space.h = mRH; list.add(space);
        x += unit * 2f + mKM + mKM;

        Key share = new Key("↗", KEY_SHARE).special(true);
        share.x = x; share.y = y; share.w = unit; share.h = mRH; list.add(share);
        x += unit + mKM;

        Key bs = new Key("⌫", KEY_BACKSPACE).special(true);
        bs.x = x; bs.y = y; bs.w = unit; bs.h = mRH; list.add(bs);
        x += unit + mKM;

        Key enter = new Key("↵", KEY_ENTER).special(true);
        enter.x = x; enter.y = y; enter.w = unit; enter.h = mRH; list.add(enter);
    }

    // ── build emoji panel ──────────────────────────────────────────────────────
    private void buildEmojiKeys(int W, int H) {
        mEmojiKeys.clear();
        int cols = 8;
        float kW  = (W - mKM * (cols + 1)) / cols;
        float kH  = mRH * 0.9f;
        float gap = mKM;
        float y   = H * 0.01f;
        int row = 0, col = 0;
        for (String em : EMOJIS) {
            Key k = new Key(em, em);
            k.x   = mKM + col * (kW + gap);
            k.y   = y   + row * (kH + gap);
            k.w   = kW; k.h = kH;
            mEmojiKeys.add(k);
            col++;
            if (col >= cols) { col = 0; row++; }
        }
        // Back button
        float backY = y + (row + 1) * (kH + gap);
        Key back = new Key("⌨", KEY_EMOJI_TOGGLE).special(true);
        back.x = mKM; back.y = backY;
        back.w = W - mKM * 2; back.h = mRH * 0.8f;
        mEmojiKeys.add(back);
    }

    // ── build clipboard panel ──────────────────────────────────────────────────
    private void buildClipKeys(int W, int H) {
        mClipKeys.clear();
        // Dynamically rebuilt when panel opens — see openClipboard()
    }

    private void openClipboard(int W, int H) {
        mClipKeys.clear();
        mClipItems.clear();

        // Read from system clipboard
        ClipboardManager cm = (ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
            int cnt = cm.getPrimaryClip().getItemCount();
            for (int i = 0; i < cnt && i < 6; i++) {
                CharSequence txt = cm.getPrimaryClip().getItemAt(i).getText();
                if (txt != null && txt.length() > 0)
                    mClipItems.add(txt.toString());
            }
        }
        if (mClipItems.isEmpty()) mClipItems.add("(clipboard empty)");

        float y  = H * 0.02f;
        float kH = mRH * 0.85f;
        for (String item : mClipItems) {
            String label = item.length() > 22 ? item.substring(0, 20) + "…" : item;
            Key k = new Key(label, item);
            k.x = mKM; k.y = y;
            k.w = W - mKM * 2; k.h = kH;
            mClipKeys.add(k);
            y += kH + mKM;
        }

        Key back = new Key("⌨", KEY_CLIP_TOGGLE).special(true);
        back.x = mKM; back.y = y;
        back.w = W - mKM * 2; back.h = mRH * 0.8f;
        mClipKeys.add(back);
    }

    // ── Drawing ───────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch (mPanel) {
            case 1: drawPanel(canvas, mEmojiKeys, false); break;
            case 2: drawPanel(canvas, mClipKeys,  true);  break;
            default: drawMainKeys(canvas); break;
        }
    }

    private void drawMainKeys(Canvas canvas) {
        RectF rect = new RectF();
        for (Key k : mMainKeys) {
            drawKey(canvas, k, rect, true);
        }
    }

    private void drawPanel(Canvas canvas, List<Key> keys, boolean largeFont) {
        RectF rect = new RectF();
        for (Key k : keys) drawKey(canvas, k, rect, !largeFont);
    }

    private void drawKey(Canvas canvas, Key k, RectF rect, boolean fictionalFont) {
        rect.set(k.x, k.y, k.x + k.w, k.y + k.h);

        // Pick background
        Paint bg;
        if (k.pressed)                        bg = mKeyPressPaint;
        else if (k.code.equals(KEY_SHIFT) && mShifted)  bg = mActivePaint;
        else if (k.code.equals(KEY_THEME_TOGGLE))        bg = mSpecialPaint;
        else if (k.special)                              bg = mSpecialPaint;
        else                                             bg = mKeyPaint;

        // Shadow / glow for pressed
        if (k.pressed) {
            canvas.drawRoundRect(new RectF(rect.left+3, rect.top+3, rect.right+3, rect.bottom+3),
                    mKR, mKR, mShadowPaint);
        }

        canvas.drawRoundRect(rect, mKR, mKR, bg);
        canvas.drawRoundRect(rect, mKR, mKR, mBorderPaint);

        // Label
        float cx = k.x + k.w / 2f;
        float cy = k.y + k.h / 2f;

        String lbl = resolveLabel(k);

        Paint lp;
        if (k.special || !fictionalFont) {
            lp = mSmallPaint;
            lp.setTextSize(mSFS * 1.1f);
            lp.setTypeface(mFontSystem);
        } else {
            lp = mLabelPaint;
            lp.setTextSize(mFS);
            lp.setTypeface(mFontFictional);
        }

        // vertically centre using font metrics
        float baseline = cy - (lp.descent() + lp.ascent()) / 2f;
        canvas.drawText(lbl, cx, baseline, lp);

        // tiny shift-indicator dot on shifted char keys
        if (!k.special && mShifted && fictionalFont) {
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(cAccent());
            dot.setStyle(Paint.Style.FILL);
            canvas.drawCircle(k.x + k.w - mKM * 2, k.y + mKM * 2, mKM * 1.2f, dot);
        }
    }

    /** Resolves what label/code to show based on shift state and encoding. */
    private String resolveLabel(Key k) {
        if (k.special) return k.label;
        // encoded as "lo|hi"
        int pipe = k.label.indexOf('|');
        if (pipe >= 0) {
            // label field holds "lo" normally; we need to parse code
            // but we stuffed lo into label; hi is after pipe in code field
            // Actually we stored label = lo, code = "lo|hi"
        }
        int pipeC = k.code.indexOf('|');
        if (pipeC >= 0) {
            String lo = k.code.substring(0, pipeC);
            String hi = k.code.substring(pipeC + 1);
            return mShifted ? hi : lo;
        }
        return k.label;
    }

    private String resolveCode(Key k) {
        if (k.special) return k.code;
        int pipe = k.code.indexOf('|');
        if (pipe >= 0) {
            String lo = k.code.substring(0, pipe);
            String hi = k.code.substring(pipe + 1);
            return mShifted ? hi : lo;
        }
        return k.code;
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float px = ev.getX(), py = ev.getY();
        List<Key> active = activeKeys();

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                for (Key k : active) { if (k.hit(px,py)) { k.pressed = true; break; } }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                Key hit = null;
                for (Key k : active) { if (k.hit(px,py)) { hit = k; break; } }
                for (Key k : active) k.pressed = false;
                if (hit != null) handleKey(hit);
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                for (Key k : active) k.pressed = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private List<Key> activeKeys() {
        switch (mPanel) {
            case 1: return mEmojiKeys;
            case 2: return mClipKeys;
            default: return mMainKeys;
        }
    }

    private void handleKey(Key k) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);

        String code = k.code;

        // Panel toggles
        if (code.equals(KEY_EMOJI_TOGGLE)) {
            mPanel = (mPanel == 1) ? 0 : 1;
            invalidate(); return;
        }
        if (code.equals(KEY_CLIP_TOGGLE)) {
            if (mPanel == 2) { mPanel = 0; }
            else { mPanel = 2; openClipboard(getWidth(), getHeight()); }
            invalidate(); return;
        }
        if (code.equals(KEY_THEME_TOGGLE)) {
            mDark = !mDark;
            applyTheme(); invalidate(); return;
        }
        if (code.equals(KEY_SHIFT)) {
            mShifted = !mShifted;
            invalidate(); return;
        }
        if (code.equals(KEY_SHARE)) {
            if (mShareListener != null) mShareListener.onShareRequested();
            return;
        }

        // Clipboard item paste
        if (mPanel == 2 && !code.equals(KEY_CLIP_TOGGLE)) {
            if (mKeyListener != null) {
                for (char c : code.toCharArray()) {
                    mKeyListener.onKeyPressed(String.valueOf(c));
                }
            }
            mPanel = 0; invalidate(); return;
        }

        // Normal / emoji key
        String toSend = resolveCode(k);
        if (mKeyListener != null) mKeyListener.onKeyPressed(toSend);

        // Auto-reset shift after one char
        if (!k.special && mShifted) { mShifted = false; invalidate(); }
    }

    // ── Measure ───────────────────────────────────────────────────────────────
    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = (int)(w * 0.50f);   // slightly taller for 6 rows
        setMeasuredDimension(w, h);
    }
}
