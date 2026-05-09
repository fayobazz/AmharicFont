package com.fictional.ime;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class MyKeyboardService extends InputMethodService
        implements FictionalKeyboardView.KeyEventListener,
                   FictionalKeyboardView.ShareRequestListener {

    private FictionalKeyboardView mKeyboardView;
    private final StringBuilder mComposedText = new StringBuilder();

    @Override
    public View onCreateInputView() {
        mKeyboardView = new FictionalKeyboardView(this);
        mKeyboardView.setKeyEventListener(this);
        mKeyboardView.setShareRequestListener(this);
        return mKeyboardView;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (!restarting) mComposedText.setLength(0);
        if (mKeyboardView != null) mKeyboardView.reset();
    }

    // ── KeyEventListener ──────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(String symbol) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (symbol) {
            case FictionalKeyboardView.KEY_BACKSPACE:
                ic.deleteSurroundingText(1, 0);
                if (mComposedText.length() > 0)
                    mComposedText.deleteCharAt(mComposedText.length() - 1);
                break;

            case FictionalKeyboardView.KEY_ENTER: {
                EditorInfo ei = getCurrentInputEditorInfo();
                int action = ei != null
                        ? (ei.imeOptions & EditorInfo.IME_MASK_ACTION)
                        : EditorInfo.IME_ACTION_NONE;
                if (action != EditorInfo.IME_ACTION_NONE
                        && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(action);
                } else {
                    ic.commitText("\n", 1);
                    mComposedText.append('\n');
                }
                break;
            }

            case FictionalKeyboardView.KEY_SPACE:
                ic.commitText(" ", 1);
                mComposedText.append(' ');
                break;

            case FictionalKeyboardView.KEY_SHIFT:
            case FictionalKeyboardView.KEY_EMOJI_TOGGLE:
            case FictionalKeyboardView.KEY_CLIP_TOGGLE:
            case FictionalKeyboardView.KEY_THEME_TOGGLE:
                // handled inside view
                break;

            default:
                ic.commitText(symbol, 1);
                mComposedText.append(symbol);
                break;
        }
    }

    // ── ShareRequestListener ──────────────────────────────────────────────────

    @Override
    public void onShareRequested() {
        String text = mComposedText.toString().trim();
        if (text.isEmpty()) text = "✦";

        try {
            Uri imageUri = TextImageExporter.export(this, text);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_STREAM, imageUri);
            share.putExtra(Intent.EXTRA_TEXT,
                    "Sent via Fictional Keyboard — install the font to read!");
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                         | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(share, "Share image via…")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, text);
            share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(share, "Share text via…")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
