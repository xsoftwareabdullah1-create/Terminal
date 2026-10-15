package com.astraterm.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

public final class TerminalView extends View {
    private static final int MAX_LINES = 1500;
    private static final int MAX_CURRENT_CHARS = 8192;
    private static final int ESC_NORMAL = 0;
    private static final int ESC_AFTER_INTRODUCER = 1;
    private static final int ESC_CSI = 2;
    private static final int ESC_OSC = 3;
    private static final int ESC_OSC_AFTER_ESCAPE = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<String> lines = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();
    private float textSize;
    private float cellWidth;
    private int escapeState = ESC_NORMAL;

    public TerminalView(Context context) {
        this(context, null);
    }

    public TerminalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setFocusableInTouchMode(true);
        textSize = 14f * getResources().getDisplayMetrics().scaledDensity;
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(textSize);
        paint.setColor(0xFF202124);
        cellWidth = Math.max(1f, paint.measureText("M"));
        lines.add("AstraTerm 0.1.0 — Android system shell");
        lines.add("No root required. Type commands below.");
        lines.add("");
    }

    public synchronized void appendOutput(String data) {
        if (data == null || data.isEmpty()) return;

        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (escapeState != ESC_NORMAL) {
                consumeEscape(c);
                continue;
            }
            if (c == 27) {
                escapeState = ESC_AFTER_INTRODUCER;
            } else if (c == '\r') {
                current.setLength(0);
            } else if (c == '\n') {
                addCurrentLine();
            } else if (c == '\b' || c == 127) {
                if (current.length() > 0) current.deleteCharAt(current.length() - 1);
            } else if (c == '\t') {
                int spaces = 4 - (current.length() % 4);
                for (int j = 0; j < spaces; j++) current.append(' ');
            } else if (c >= 32 && c != 127) {
                current.append(c);
                if (current.length() > MAX_CURRENT_CHARS) {
                    current.delete(0, current.length() - MAX_CURRENT_CHARS);
                }
            }
        }

        while (lines.size() > MAX_LINES) lines.remove(0);
        postInvalidate();
    }

    private void consumeEscape(char c) {
        switch (escapeState) {
            case ESC_AFTER_INTRODUCER:
                if (c == '[') escapeState = ESC_CSI;
                else if (c == ']') escapeState = ESC_OSC;
                else escapeState = ESC_NORMAL;
                break;
            case ESC_CSI:
                if (c >= 0x40 && c <= 0x7e) escapeState = ESC_NORMAL;
                break;
            case ESC_OSC:
                if (c == 7) escapeState = ESC_NORMAL;
                else if (c == 27) escapeState = ESC_OSC_AFTER_ESCAPE;
                break;
            case ESC_OSC_AFTER_ESCAPE:
                escapeState = (c == '\\') ? ESC_NORMAL : ESC_OSC;
                break;
            default:
                escapeState = ESC_NORMAL;
        }
    }

    private void addCurrentLine() {
        lines.add(current.toString());
        current.setLength(0);
        while (lines.size() > MAX_LINES) lines.remove(0);
    }

    public void setFontScale(float delta) {
        textSize = Math.max(10f, Math.min(28f, textSize + delta));
        paint.setTextSize(textSize);
        cellWidth = Math.max(1f, paint.measureText("M"));
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFFF8F9FA);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(textSize);
        paint.setColor(0xFF202124);

        float lineHeight = textSize * 1.45f;
        int capacity = Math.max(1, (int) (getHeight() / lineHeight));
        int total = lines.size() + 1;
        int start = Math.max(0, total - capacity);
        float y = textSize + 8;

        for (int i = start; i < lines.size(); i++) {
            canvas.save();
            canvas.clipRect(0, y - lineHeight, getWidth(), y + 4);
            canvas.drawText(lines.get(i), 10, y, paint);
            canvas.restore();
            y += lineHeight;
        }

        canvas.save();
        canvas.clipRect(0, y - lineHeight, getWidth(), y + 4);
        canvas.drawText(current.toString(), 10, y, paint);
        float cursorX = 10 + paint.measureText(current.toString());
        paint.setColor(0xFF4285F4);
        canvas.drawRect(cursorX, y - textSize + 3, cursorX + cellWidth, y + 3, paint);
        canvas.restore();
    }
}
