package com.example.android.notepad;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;

public class NotePadBackgroundTest extends ActivityInstrumentationTestCase2<NotesList> {
    private NotesList list;
    public NotePadBackgroundTest() { super(NotesList.class); }
    private void main(Runnable run) { getInstrumentation().runOnMainSync(run); getInstrumentation().waitForIdleSync(); }
    private void save(String name, Bitmap bitmap) throws Exception {
        File dir = new File(list.getFilesDir(), "background_checks"); dir.mkdirs();
        FileOutputStream stream = new FileOutputStream(new File(dir, name + ".png"));
        try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); } finally { stream.close(); bitmap.recycle(); }
    }
    private void dump(View view, String prefix, StringBuilder result) {
        if (view.getVisibility() != View.VISIBLE) return;
        result.append(prefix).append(view.getClass().getSimpleName()).append(" id=").append(view.getId())
            .append(" alpha=").append(view.getAlpha()).append(" focused=").append(view.isFocused()).append(" pressed=").append(view.isPressed())
            .append(" bg=").append(view.getBackground()).append(" fg=").append(view.getForeground());
        if (view.getBackground() instanceof ColorDrawable) result.append(" color=").append(Integer.toHexString(((ColorDrawable)view.getBackground()).getColor()));
        result.append('\n');
        if (view instanceof ViewGroup && prefix.length() < 10) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) dump(group.getChildAt(i), prefix + " ", result);
        }
    }
    private void snapshot(String name) throws Exception {
        SystemClock.sleep(500);
        final Bitmap[] software = {null};
        final StringBuilder info = new StringBuilder();
        main(new Runnable() { @Override public void run() {
            View root = list.getWindow().getDecorView();
            software[0] = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(software[0])); dump(root, "", info);
        }});
        save(name + "_software", software[0]);
        save(name + "_screen", getInstrumentation().getUiAutomation().takeScreenshot());
        FileOutputStream stream = new FileOutputStream(new File(list.getFilesDir(), "background_checks/" + name + ".txt"));
        try { stream.write(info.toString().getBytes("UTF-8")); } finally { stream.close(); }
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root, String label) {
        if (root == null) return null;
        if (root.isClickable() && (label.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())
                || label.contentEquals(root.getText() == null ? "" : root.getText()))) return root;
        for (int i=0;i<root.getChildCount();i++) { AccessibilityNodeInfo value=find(root.getChild(i),label); if(value!=null)return value; }
        return null;
    }
    private void tap(String label) {
        AccessibilityNodeInfo target=null;
        long end=SystemClock.uptimeMillis()+5000;
        do {
            target=find(getInstrumentation().getUiAutomation().getRootInActiveWindow(),label);
            if(target!=null)break;
            SystemClock.sleep(80);
        } while(SystemClock.uptimeMillis()<end);
        assertNotNull(label,target);
        Rect area=new Rect(); target.getBoundsInScreen(area);
        getInstrumentation().sendPointerSync(android.view.MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),0,area.centerX(),area.centerY(),0));
        getInstrumentation().sendPointerSync(android.view.MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),1,area.centerX(),area.centerY(),0));
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(250);
    }
    private void assertWhite() {
        final int[] color={0};
        final Rect point=new Rect();
        main(new Runnable() { @Override public void run() {
            View root=((ViewGroup)list.findViewById(android.R.id.content)).getChildAt(0);
            root.requestFocus();
            Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bitmap)); color[0]=bitmap.getPixel(2,root.getHeight()/2); bitmap.recycle();
            int[] xy=new int[2]; root.getLocationOnScreen(xy); point.set(xy[0]+2,xy[1]+root.getHeight()/2,0,0);
        }});
        assertEquals("Focused root must stay white",0xffffffff,color[0]);
        SystemClock.sleep(120);
        Bitmap screen=getInstrumentation().getUiAutomation().takeScreenshot();
        int actual=screen.getPixel(point.left,point.top); screen.recycle();
        assertEquals("Screen must stay white",0xffffffff,actual);
    }
    public void testRootFocusKeepsBackgroundWhiteAcrossTabsAndDialogs() throws Exception {
        list=getActivity();
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_TAB);
        assertWhite(); snapshot("01_focused_notes");
        for(int i=0;i<3;i++) {
            tap("最近删除"); assertWhite();
            tap("待办"); assertWhite();
            tap("笔记"); assertWhite();
            tap("切换笔记分类"); tap("取消"); assertWhite();
            getInstrumentation().invokeMenuActionSync(list,R.id.menu_sort,0); tap("取消"); assertWhite();
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_TAB); assertWhite();
        }
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(NotesList.class.getName(),null,false);
        main(new Runnable() { @Override public void run() { list.recreate(); }});
        list=(NotesList)getInstrumentation().waitForMonitorWithTimeout(monitor,8000);
        getInstrumentation().removeMonitor(monitor); assertNotNull(list); assertWhite(); snapshot("02_after_recreation");
    }
}
