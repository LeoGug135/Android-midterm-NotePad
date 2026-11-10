package com.example.android.notepad;

import android.app.Instrumentation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SearchView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NotePadSelectionSortTest extends ActivityInstrumentationTestCase2<NotesList> {
    private NotesList activity;
    private String prefix;
    private final List<Uri> inserted = new ArrayList<Uri>();
    private long a, b, c, outside;
    private SharedPreferences preferences;
    private boolean hadSort;
    private int oldSort;

    public NotePadSelectionSortTest() { super(NotesList.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        prefix = "多选排序" + System.nanoTime();
        preferences = getInstrumentation().getTargetContext()
                .getSharedPreferences("NotesList", 0);
        hadSort = preferences.contains("sort_order");
        oldSort = preferences.getInt("sort_order", 0);
        preferences.edit().remove("sort_order").commit();
        a = insert(prefix + "甲", 100, 300);
        b = insert(prefix + "乙", 300, 200);
        c = insert(prefix + "丙", 200, 100);
        outside = insert("范围外" + prefix, 400, 400);
        activity = getActivity();
        search(prefix + "%"); // literal wildcard should not select anything
        awaitRows();
        search(prefix);
        // outside contains prefix in its title, so use body marker for the exact test scope.
        search(prefix + "正文");
        awaitRows(a, b, c);
    }

    private long insert(String title, long created, long modified) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE,
                title.startsWith(prefix) ? prefix + "正文" : "不在本次搜索范围");
        values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, 1609459200000L + created);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, 1609459200000L + modified);
        Uri uri = getInstrumentation().getTargetContext().getContentResolver()
                .insert(NotePad.Notes.CONTENT_URI, values);
        inserted.add(uri);
        return ContentUris.parseId(uri);
    }

    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                activity.onBackPressed();
            }
        });
        for (Uri uri : inserted) {
            getInstrumentation().getTargetContext().getContentResolver().delete(uri, null, null);
        }
        if (hadSort) preferences.edit().putInt("sort_order", oldSort).commit();
        else preferences.edit().remove("sort_order").commit();
        super.tearDown();
    }

    private void search(final String query) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                ((SearchView) activity.findViewById(R.id.search_notes)).setQuery(query, false);
            }
        });
    }

    private List<Long> rows() {
        final List<Long> result = new ArrayList<Long>();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < activity.getListAdapter().getCount(); i++) {
                    result.add(activity.getListAdapter().getItemId(i));
                }
            }
        });
        return result;
    }

    private void awaitRows(long... expectedValues) {
        List<Long> expected = new ArrayList<Long>();
        for (long id : expectedValues) expected.add(id);
        long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            if (expected.equals(rows())) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        assertEquals(expected, rows());
    }

    private void click(final int id) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() { activity.findViewById(id).performClick(); }
        });
        getInstrumentation().waitForIdleSync();
    }

    private void select(final int position, final boolean longPress) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                long id = activity.getListAdapter().getItemId(position);
                View row = activity.getListAdapter().getView(position, null, activity.getListView());
                if (longPress) {
                    assertTrue(activity.getListView().getOnItemLongClickListener()
                            .onItemLongClick(activity.getListView(), row, position, id));
                } else activity.getListView().performItemClick(row, position, id);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private void assertSelected(final int count) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertEquals("已选 " + count + " 项", ((TextView) activity.findViewById(
                        R.id.selection_count)).getText().toString());
                assertEquals(count > 0, activity.findViewById(R.id.delete_selected).isEnabled());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.selection_bar).getVisibility());
                int checked = 0;
                for (int i = 0; i < activity.getListAdapter().getCount(); i++) {
                    View row = activity.getListAdapter().getView(i, null, activity.getListView());
                    CheckBox box = (CheckBox) row.findViewById(R.id.note_selected);
                    assertEquals(View.VISIBLE, box.getVisibility());
                    if (box.isChecked()) checked++;
                }
                assertEquals(count, checked);
            }
        });
    }

    private void tapText(String text) {
        long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                    if (text.contentEquals(node.getText()) && node.isClickable()) {
                        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                        getInstrumentation().waitForIdleSync();
                        return;
                    }
                }
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("No clickable text: " + text);
    }

    private void screenshot(String name) throws Exception {
        SystemClock.sleep(400);
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        File directory = new File(getInstrumentation().getTargetContext().getFilesDir(), "selection_checks");
        if (!directory.exists()) assertTrue(directory.mkdirs());
        FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"));
        try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        finally { stream.close(); bitmap.recycle(); }
    }

    private boolean clickSortButton(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (android.text.TextUtils.equals("笔记排序", node.getContentDescription()) && node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (clickSortButton(node.getChild(i))) return true;
        }
        return false;
    }

    private void recreateList() {
        Instrumentation.ActivityMonitor monitor = getInstrumentation()
                .addMonitor(NotesList.class.getName(), null, false);
        try {
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override public void run() { activity.recreate(); }
            });
            activity = (NotesList) getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
            assertNotNull(activity);
            getInstrumentation().waitForIdleSync();
        } finally { getInstrumentation().removeMonitor(monitor); }
    }

    public void testFourSortOrdersAndPersistence() throws Exception {
        // Click the visible three-dot action itself: there must be no intermediate menu.
        boolean clicked = false;
        long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            clicked = clickSortButton(getInstrumentation().getUiAutomation().getRootInActiveWindow());
            if (!clicked) SystemClock.sleep(50);
        } while (!clicked && SystemClock.uptimeMillis() < deadline);
        assertTrue("Visible sort action did not become available", clicked);
        SystemClock.sleep(300);
        AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("按修改时间：新到旧");
        assertFalse(nodes.isEmpty());
        assertTrue(nodes.get(0).isChecked());
        screenshot("笔记排序弹窗");
        tapText("按修改时间：旧到新");
        awaitRows(c, b, a);
        getInstrumentation().invokeMenuActionSync(activity, R.id.menu_sort, 0);
        tapText("按创建时间：新到旧");
        awaitRows(b, c, a);
        getInstrumentation().invokeMenuActionSync(activity, R.id.menu_sort, 0);
        tapText("按创建时间：旧到新");
        awaitRows(a, c, b);
        recreateList();
        awaitRows(a, c, b);
        assertEquals(3, preferences.getInt("sort_order", -1));
        getInstrumentation().invokeMenuActionSync(activity, R.id.menu_sort, 0);
        tapText("按修改时间：新到旧");
        awaitRows(a, b, c);
    }

    public void testLongPressSelectAllToggleAndCancel() throws Exception {
        select(0, true);
        assertSelected(1);
        select(2, false);
        assertSelected(2);
        recreateList();
        awaitRows(a, b, c);
        assertSelected(2);
        screenshot("笔记多选界面");
        click(R.id.select_all);
        assertSelected(3);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertEquals("取消全选", ((Button) activity.findViewById(R.id.select_all)).getText());
                assertEquals(activity.getString(R.string.notes_select_scope_search),
                        ((TextView) activity.findViewById(R.id.selection_scope)).getText());
            }
        });
        click(R.id.select_all);
        assertSelected(0);
        click(R.id.cancel_selection);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertEquals(View.GONE, activity.findViewById(R.id.selection_bar).getVisibility());
                assertTrue(activity.getActionBar().isShowing());
            }
        });
    }

    public void testBatchDeleteCancelThenOnlySelectedIds() {
        select(0, true);
        select(1, false);
        click(R.id.delete_selected);
        tapText("取消");
        awaitRows(a, b, c);
        assertSelected(2);
        click(R.id.delete_selected);
        tapText("删除");
        awaitRows(c);
        Cursor cursor = activity.getContentResolver().query(ContentUris.withAppendedId(
                NotePad.Notes.CONTENT_URI, outside), null, null, null, null);
        try { assertEquals(1, cursor.getCount()); } finally { cursor.close(); }
    }

    public void testSearchSelectAllDoesNotDeleteOutsideAndBackExitsSelection() {
        select(0, true);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() { activity.onBackPressed(); }
        });
        select(1, true);
        search(prefix + "不存在");
        awaitRows();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertEquals(View.GONE, activity.findViewById(R.id.selection_bar).getVisibility());
            }
        });
        search(prefix + "正文");
        awaitRows(a, b, c);
        select(0, true);
        click(R.id.select_all);
        assertSelected(3);
        click(R.id.delete_selected);
        tapText("删除");
        awaitRows();
        Cursor cursor = activity.getContentResolver().query(ContentUris.withAppendedId(
                NotePad.Notes.CONTENT_URI, outside), null, null, null, null);
        try { assertEquals(1, cursor.getCount()); } finally { cursor.close(); }
    }
}
