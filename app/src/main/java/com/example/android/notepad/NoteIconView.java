package com.example.android.notepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/** Vector-like line icons without a dependency on newer drawable APIs. */
public class NoteIconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private String kind = "more";
    private int tint = 0xFF404346;
    public NoteIconView(Context context) { super(context); }
    public NoteIconView(Context context, AttributeSet attrs) { super(context, attrs); }
    void setTint(int value) { tint = value; invalidate(); }
    void setKind(String value) { kind = value; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        String icon = getTag() == null ? kind : getTag().toString();
        canvas.save();
        float scale = NoteFeatures.dp(getContext(), 25) / 24f;
        canvas.translate((getWidth() - 24 * scale) / 2, (getHeight() - 24 * scale) / 2);
        canvas.scale(scale, scale);
        paint.setColor(tint); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();
        if ("notes".equals(icon)) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new android.graphics.RectF(4, 2, 20, 22), 3, 3, paint);
            paint.setColor(0xFFFFFFFF); paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(8, 7, 11, 7, paint);
            canvas.drawLine(8, 12, 16, 12, paint);
            canvas.drawLine(8, 17, 13, 17, paint);
        } else if ("todo".equals(icon)) {
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12, 12, 10, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setColor(0xFFFFFFFF); paint.setStrokeWidth(2.5f);
            path.moveTo(7.5f, 12); path.lineTo(10.5f, 15); path.lineTo(16.5f, 9);
        } else if ("trash".equals(icon)) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new android.graphics.RectF(6, 7, 18, 22), 2, 2, paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(4, 5, 20, 5, paint); canvas.drawLine(9, 2, 15, 2, paint);
            paint.setColor(0xFFFFFFFF); paint.setStrokeWidth(1.5f);
            canvas.drawLine(10, 10, 10, 18, paint); canvas.drawLine(14, 10, 14, 18, paint);
        } else if ("pin".equals(icon) || "unpin".equals(icon)) {
            path.moveTo(5, 4); path.lineTo(19, 4);
            path.moveTo(12, 21); path.lineTo(12, 8);
            path.moveTo(5, 14); path.lineTo(12, 7); path.lineTo(19, 14);
            if ("unpin".equals(icon)) { path.moveTo(3, 21); path.lineTo(21, 3); }
        } else if ("tag".equals(icon)) {
            path.moveTo(4, 4); path.lineTo(13, 4); path.lineTo(22, 13);
            path.lineTo(13, 22); path.lineTo(4, 13); path.close();
            canvas.drawCircle(8, 8, 1, paint);
        } else if ("delete".equals(icon)) {
            path.moveTo(4, 6); path.lineTo(20, 6);
            path.moveTo(9, 3); path.lineTo(15, 3);
            path.moveTo(6, 6); path.lineTo(7, 21); path.lineTo(17, 21); path.lineTo(18, 6);
            path.moveTo(10, 10); path.lineTo(10, 17);
            path.moveTo(14, 10); path.lineTo(14, 17);
        } else if ("move".equals(icon)) {
            path.moveTo(3, 9); path.lineTo(13, 9); path.lineTo(13, 4);
            path.lineTo(22, 12); path.lineTo(13, 20); path.lineTo(13, 15);
            path.lineTo(3, 15); path.close();
        } else if ("importance".equals(icon)) {
            path.moveTo(12, 3); path.lineTo(22, 21); path.lineTo(2, 21); path.close();
            path.moveTo(6, 14); path.lineTo(18, 14);
        } else if ("restore".equals(icon)) {
            path.moveTo(5, 10); path.cubicTo(8, 0, 22, 4, 21, 13);
            path.cubicTo(20, 22, 8, 23, 5, 16);
            path.moveTo(4, 4); path.lineTo(4, 11); path.lineTo(11, 11);
        } else if ("color".equals(icon)) {
            int[] shades = {0xFFFFE778, 0xFFA6DDBB, 0xFF9BDCF2, 0xFFFFB7C2, 0xFFFFD093};
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3.2f);
            android.graphics.RectF ring = new android.graphics.RectF(3, 3, 21, 21);
            for (int i = 0; i < shades.length; i++) {
                paint.setColor(shades[i]); canvas.drawArc(ring, -90 + i * 72, 74, false, paint);
            }
        } else if ("style".equals(icon)) {
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(18); paint.setFakeBoldText(true);
            canvas.drawText("Aa", 1, 19, paint); paint.setFakeBoldText(false);
        } else {
            for (int y = 5; y <= 19; y += 7) canvas.drawCircle(12, y, 1.5f, paint);
        }
        canvas.drawPath(path, paint); canvas.restore();
    }
}
