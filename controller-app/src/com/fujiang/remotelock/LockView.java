package com.fujiang.remotelock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.security.MessageDigest;
import java.util.ArrayList;

public class LockView extends View {
    private final float[][] pts = new float[9][2]; // 3x3 中心点
    private final ArrayList<Integer> sel = new ArrayList<>();
    private float curX, curY;
    private boolean drawing = false;
    private int error = 0; // 0 normal 1 error flash
    private final Paint pDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBg = new Paint();
    private final Context ctx;

    public LockView(Context c) {
        super(c);
        ctx = c;
        setFocusable(true);
        pBg.setColor(0xFF101418);
        pDot.setColor(0xFF3A4149);
        pRing.setColor(0xFF4FC3F7); pRing.setStyle(Paint.Style.STROKE); pRing.setStrokeWidth(6f);
        pLine.setColor(0xCC4FC3F7); pLine.setStyle(Paint.Style.STROKE); pLine.setStrokeWidth(12f);
        pLine.setStrokeCap(Paint.Cap.ROUND); pLine.setStrokeJoin(Paint.Join.ROUND);
        pText.setColor(0xFFB0BEC5); pText.setTextSize(46f); pText.setTextAlign(Paint.Align.CENTER);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        // 正方形网格: 间距取宽高中较小值的28%, 整体居中
        float spX = Math.min(w, h) * 0.28f;          // 横向保持紧凑
        float spY = (h * 0.25f + Math.min(w, h) * 0.28f) / 2f;  // 垂直取新旧平均
        float cx = w / 2f, cy = h / 2f;
        for (int i = 0; i < 9; i++) {
            pts[i][0] = cx + (i % 3 - 1) * spX;
            pts[i][1] = cy + (i / 3 - 1) * spY;
        }
    }

    private int hit(float x, float y) {
        float r = Math.min(getWidth(), getHeight()) * 0.28f * 0.5f;
        for (int i = 0; i < 9; i++) {
            float dx = x - pts[i][0], dy = y - pts[i][1];
            if (dx * dx + dy * dy < r * r && !sel.contains(i)) return i;
        }
        return -1;
    }

    // 标准图案锁行为: 两点之间跨过的未选中中间点自动补选
    private void addWithMiddle(int next) {
        if (!sel.isEmpty()) {
            int prev = sel.get(sel.size() - 1);
            int pr = prev / 3, pc = prev % 3, nr = next / 3, nc = next % 3;
            if ((Math.abs(pr - nr) == 2 && pc == nc) || (Math.abs(pc - nc) == 2 && pr == nr)
                || (Math.abs(pr - nr) == 2 && Math.abs(pc - nc) == 2)) {
                int mid = (pr + nr) / 2 * 3 + (pc + nc) / 2;
                if (!sel.contains(mid)) sel.add(mid);
            }
        }
        sel.add(next);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                sel.clear(); error = 0; drawing = true;
                int h0 = hit(x, y); if (h0 >= 0) addWithMiddle(h0);
                curX = x; curY = y; invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                curX = x; curY = y;
                int h = hit(x, y); if (h >= 0) addWithMiddle(h);
                invalidate(); return true;
            case MotionEvent.ACTION_UP:
                drawing = false; verify(); invalidate(); return true;
        }
        return super.onTouchEvent(e);
    }

    private void verify() {
        StringBuilder sb = new StringBuilder();
        for (int v : sel) sb.append(v);
        String digest = sha256(sb.toString());
        String expect = Prefs.getHash(ctx);
        if (!expect.isEmpty() && expect.equalsIgnoreCase(digest)) {
            Prefs.setFail(ctx, 0);
            ((LockService) ctx).manualUnlock();
        } else {
            Prefs.setFail(ctx, Prefs.getFail(ctx) + 1);
            error = 1; sel.clear();
        }
    }

    static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte x : b) hex.append(String.format("%02x", x));
            return hex.toString();
        } catch (Exception e) { return ""; }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        return true; // 吞掉返回/音量等所有按键
    }

    @Override protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), pBg);
        float r = Math.min(getWidth(), getHeight()) * 0.28f * 0.35f;
        c.drawText("RemoteLock", getWidth() / 2f, getHeight() * 0.10f, pText);
        if (error == 1) c.drawText("图案错误 (" + Prefs.getFail(ctx) + ")", getWidth() / 2f, getHeight() * 0.16f, pText);
        // 连线
        if (!sel.isEmpty()) {
            Path path = new Path();
            path.moveTo(pts[sel.get(0)][0], pts[sel.get(0)][1]);
            for (int i = 1; i < sel.size(); i++) path.lineTo(pts[sel.get(i)][0], pts[sel.get(i)][1]);
            if (drawing) path.lineTo(curX, curY);
            c.drawPath(path, pLine);
        }
        for (int i = 0; i < 9; i++) {
            c.drawCircle(pts[i][0], pts[i][1], r * 0.45f, pDot);
            if (sel.contains(i)) c.drawCircle(pts[i][0], pts[i][1], r, pRing);
        }
    }
}
