package com.astraterm.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;

public final class TerminalView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<String> lines = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();
    private float textSize;
    private float cellWidth;
    private boolean cursorVisible = true;
    private int maxLines = 1500;

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
        paint.setColor(0xFFE5EDF5);
        cellWidth = paint.measureText("M");
        lines.add("AstraTerm 0.1.0 — Android system shell");
        lines.add("No root required. Type commands below.");
        lines.add("");
    }

    public synchronized void appendOutput(String data) {
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (c == '\r') continue;
            if (c == '\n') { lines.add(current.toString()); current.setLength(0); }
            else if (c == '\b' || c == 127) { if (current.length() > 0) current.deleteCharAt(current.length()-1); }
            else if (c == 27) { /* Minimal renderer: ignore ANSI escape introducer. */ }
            else if (c == '\t') current.append("    ");
            else if (c >= 32) current.append(c);
        }
        while (lines.size() > maxLines) lines.remove(0);
        invalidate();
    }

    public void setFontScale(float scale) {
        textSize = Math.max(10f, Math.min(28f, textSize + scale));
        paint.setTextSize(textSize);
        cellWidth = Math.max(1f, paint.measureText("M"));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFF10151C);
        paint.setTextSize(textSize);
        paint.setColor(0xFFE5EDF5);
        float lineHeight = textSize * 1.45f;
        int capacity = Math.max(1, (int)(getHeight() / lineHeight));
        int total = lines.size() + 1;
        int start = Math.max(0, total - capacity);
        float y = textSize + 8;
        for (int i = start; i < lines.size(); i++) {
            canvas.drawText(lines.get(i), 10, y, paint);
            y += lineHeight;
        }
        canvas.drawText(current.toString(), 10, y, paint);
        if (cursorVisible) {
            float x = 10 + paint.measureText(current.toString());
            paint.setColor(0xFF35E6A5);
            canvas.drawRect(x, y - textSize + 3, x + Math.max(2, cellWidth), y + 3, paint);
        }
        postInvalidateDelayed(500);
    }
}
