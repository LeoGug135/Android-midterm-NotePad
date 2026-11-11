package com.example.android.notepad;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Uses isolated migration databases and uniquely tagged rows; never clears user notes. */
public class NotePadExtensionsTest extends ActivityInstrumentationTestCase2<NotesList> {
    private NotesList list;
    private Activity editor;
    private ContentResolver resolver;
    private String prefix;
    private final ArrayList<Uri> notes = new ArrayList<Uri>();
    private final ArrayList<Uri> categories = new ArrayList<Uri>();
    private long a, b, c;
    private int oldSort, oldFont;
    private boolean hadSort, hadFont;

    public NotePadExtensionsTest() { super(NotesList.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        resolver = getInstrumentation().getTargetContext().getContentResolver();
        prefix = "扩展" + (System.nanoTime() % 1000000000L);
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("NotesList", 0);
        hadSort = prefs.contains("sort_order"); oldSort = prefs.getInt("sort_order", 0);
        prefs.edit().putInt("sort_order", 0).commit();
        android.content.SharedPreferences appearance = NoteFeatures.preferences(getInstrumentation().getTargetContext());
        hadFont = appearance.contains("font_index"); oldFont = appearance.getInt("font_index", 1);
        a = insert("甲", 100, 300); b = insert("乙", 300, 200); c = insert("丙", 200, 100);
        list = getActivity(); search(prefix); awaitRows(a, b, c);
    }

    private long insert(String name, long created, long modified) {
        ContentValues values = new ContentValues();
        values.put("title", prefix + name); values.put("note", prefix + "正文\n第二行");
        values.put("created", 1609459200000L + created); values.put("modified", 1609459200000L + modified);
        Uri uri = resolver.insert(NotePad.Notes.CONTENT_URI, values); notes.add(uri);
        return ContentUris.parseId(uri);
    }

    private long category(String name) {
        ContentValues values = new ContentValues(); values.put("name", name);
        Uri uri = resolver.insert(NoteFeatures.CATEGORIES_URI, values); categories.add(uri);
        return ContentUris.parseId(uri);
    }

    private void update(long id, String key, long value) {
        ContentValues values = new ContentValues(); values.put(key, value);
        assertEquals(1, resolver.update(NoteFeatures.noteUri(id), values, null, null));
    }

    private long value(long id, String key, boolean trash) {
        Uri uri = trash ? NoteFeatures.TRASH_URI : NotePad.Notes.CONTENT_URI;
        Cursor cursor = resolver.query(uri, new String[] { key }, "_id=?", new String[] { "" + id }, null);
        try { assertTrue(cursor.moveToFirst()); return cursor.getLong(0); } finally { cursor.close(); }
    }

    private void main(Runnable action) { getInstrumentation().runOnMainSync(action); getInstrumentation().waitForIdleSync(); }
    private void search(final String query) {
        main(new Runnable() { @Override public void run() {
            ((SearchView) list.findViewById(R.id.search_notes)).setQuery(query, false);
        }});
    }
    private List<Long> rows() {
        final ArrayList<Long> ids = new ArrayList<Long>();
        main(new Runnable() { @Override public void run() {
            for (int i = 0; i < list.getListAdapter().getCount(); i++) ids.add(list.getListAdapter().getItemId(i));
        }});
        return ids;
    }
    private void awaitRows(long... ids) {
        ArrayList<Long> expected = new ArrayList<Long>(); for (long id : ids) expected.add(id);
        long end = SystemClock.uptimeMillis() + 8000;
        do { if (expected.equals(rows())) return; SystemClock.sleep(60); } while (SystemClock.uptimeMillis() < end);
        assertEquals(expected, rows());
    }
    private void click(final int id) {
        main(new Runnable() { @Override public void run() { assertTrue(list.findViewById(id).performClick()); }});
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root, String label, boolean description) {
        if (root == null) return null;
        CharSequence value = description ? root.getContentDescription() : root.getText();
        if (label.contentEquals(value == null ? "" : value) && root.isClickable()) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo result = find(root.getChild(i), label, description);
            if (result != null) return result;
        }
        return null;
    }
    private void tap(String label) { tap(label, false); }
    private void tap(String label, boolean description) {
        long end = SystemClock.uptimeMillis() + 8000;
        do {
            AccessibilityNodeInfo target = find(getInstrumentation().getUiAutomation().getRootInActiveWindow(), label, description);
            if (target != null) {
                assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                getInstrumentation().waitForIdleSync(); SystemClock.sleep(120); return;
            }
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < end);
        fail("Missing action " + label);
    }
    private void scope(String name) { tap("切换分类与最近删除", true); tap(name); }
    private void select(final int position) {
        main(new Runnable() { @Override public void run() {
            View row = list.getListAdapter().getView(position, null, list.getListView());
            list.getListView().getOnItemLongClickListener().onItemLongClick(list.getListView(), row,
                    position, list.getListAdapter().getItemId(position));
        }});
    }
    private void swipeFirst(boolean left) {
        final Rect bounds = new Rect();
        main(new Runnable() { @Override public void run() {
            View row = list.getListView().getChildAt(0);
            int[] location = new int[2]; row.getLocationOnScreen(location);
            bounds.set(location[0], location[1], location[0] + row.getWidth(), location[1] + row.getHeight());
        }});
        float start = left ? bounds.right - 60 : bounds.left + 60;
        float end = left ? bounds.left + 60 : bounds.right - 60;
        long down = SystemClock.uptimeMillis();
        getInstrumentation().sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, start, bounds.top + 75, 0));
        for (int i = 1; i <= 12; i++) {
            SystemClock.sleep(20);
            getInstrumentation().sendPointerSync(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE,
                    start + (end - start) * i / 12, bounds.top + 75, 0));
        }
        getInstrumentation().sendPointerSync(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                end, bounds.top + 75, 0));
        getInstrumentation().waitForIdleSync();
    }
    private void screenshot(String name) throws Exception {
        SystemClock.sleep(250);
        Bitmap image = getInstrumentation().getUiAutomation().takeScreenshot();
        File directory = new File(getInstrumentation().getTargetContext().getFilesDir(), "extension_checks");
        directory.mkdirs();
        FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"));
        try { image.compress(Bitmap.CompressFormat.PNG, 100, output); }
        finally { output.close(); image.recycle(); }
    }

    private AccessibilityNodeInfo editable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if ("android.widget.EditText".contentEquals(node.getClassName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = editable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private void enterCategoryName(String name) {
        AccessibilityNodeInfo input = editable(getInstrumentation().getUiAutomation().getRootInActiveWindow());
        assertNotNull(input);
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name);
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments));
        getInstrumentation().waitForIdleSync();
    }

    public void testCategoryDialogsCreateRenameDeleteAndBulkMove() throws Exception {
        String name = prefix + "学习", renamed = prefix + "生活";
        scope("管理分类"); tap("＋ 新建分类");
        screenshot("居中分类输入弹窗"); enterCategoryName(name); tap("确定");
        long categoryId = 0;
        for (NoteFeatures.Category category : NoteFeatures.categories(getInstrumentation().getTargetContext())) {
            if (name.equals(category.name)) {
                categoryId = category.id;
                categories.add(ContentUris.withAppendedId(NoteFeatures.CATEGORIES_URI, categoryId));
            }
        }
        assertTrue(categoryId > 0); awaitRows(a, b, c);
        select(0); click(R.id.select_all); click(R.id.move_selected); tap(name);
        assertEquals(categoryId, value(a, "category_id", false));
        assertEquals(categoryId, value(b, "category_id", false));
        assertEquals(categoryId, value(c, "category_id", false));
        scope(name); awaitRows(a, b, c); search(prefix); awaitRows(a, b, c);
        screenshot("分类筛选与笔记数量");
        scope("管理分类"); tap(name); tap("重命名"); enterCategoryName(renamed); tap("确定");
        awaitRows(a, b, c);
        assertEquals(renamed, NoteFeatures.categoryName(getInstrumentation().getTargetContext(), categoryId));
        scope("管理分类"); tap(renamed); tap("删除分类"); tap("删除分类");
        awaitRows(a, b, c);
        assertEquals(0, value(a, "category_id", false));
        assertEquals(1609459200300L, value(a, "modified", false));
    }

    public void testMigrationPreservesLegacyNotesAndDefaults() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        NotePadProvider.DatabaseHelper helper = new NotePadProvider.DatabaseHelper(getInstrumentation().getTargetContext());
        try {
            db.execSQL("CREATE TABLE notes (_id INTEGER PRIMARY KEY,title TEXT,note TEXT,created INTEGER,modified INTEGER)");
            db.execSQL("INSERT INTO notes VALUES (17,'旧标题','中文正文',123,456)");
            helper.onUpgrade(db, 2, 3);
            Cursor cursor = db.rawQuery("SELECT * FROM notes WHERE _id=17", null);
            try {
                assertTrue(cursor.moveToFirst()); assertEquals("旧标题", cursor.getString(cursor.getColumnIndexOrThrow("title")));
                assertEquals("中文正文", cursor.getString(cursor.getColumnIndexOrThrow("note")));
                assertEquals(123, cursor.getLong(cursor.getColumnIndexOrThrow("created")));
                assertEquals(456, cursor.getLong(cursor.getColumnIndexOrThrow("modified")));
                for (String column : Arrays.asList("pinned", "background", "category_id", "deleted_at"))
                    assertEquals(0, cursor.getLong(cursor.getColumnIndexOrThrow(column)));
            } finally { cursor.close(); }
            ContentValues category = new ContentValues(); category.put("name", "学习");
            assertTrue(db.insertOrThrow("categories", null, category) > 0);
        } finally { helper.close(); db.close(); }
    }

    public void testPinnedGroupsFollowAllFourSortOrders() throws Exception {
        update(a, "background", 4); update(b, "background", 1); update(c, "background", 3);
        update(a, "pinned", 1); update(c, "pinned", 1); awaitRows(a, c, b);
        main(new Runnable() { @Override public void run() {
            for (int i = 0; i < 3; i++) {
                View row = list.getListAdapter().getView(i, null, list.getListView());
                android.graphics.drawable.Drawable drawable = row.findViewById(R.id.note_card).getBackground();
                Rect bounds = new Rect(drawable.getBounds());
                Bitmap pixels = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
                drawable.setBounds(0, 0, 100, 100); drawable.draw(new android.graphics.Canvas(pixels));
                assertEquals(i < 2 ? 0xFFFFF6B8 : 0xFFFFFFFF, pixels.getPixel(50, 50));
                drawable.setBounds(bounds); pixels.recycle();
            }
        }});
        String[] options = { "按修改时间：旧到新", "按创建时间：新到旧", "按创建时间：旧到新", "按修改时间：新到旧" };
        long[][] expected = { {c, a, b}, {c, a, b}, {a, c, b}, {a, c, b} };
        for (int i = 0; i < options.length; i++) {
            getInstrumentation().invokeMenuActionSync(list, R.id.menu_sort, 0); tap(options[i]); awaitRows(expected[i]);
        }
        assertEquals(1609459200300L, value(a, "modified", false));
        swipeFirst(true); tap("取消置顶", true); awaitRows(c, a, b);
        screenshot("置顶与卡片列表");
    }

    public void testRealSwipeClassifyMoreMoveAndCategoryFilter() throws Exception {
        long id = category(prefix + "学习");
        final int[] before = new int[2];
        main(new Runnable() { @Override public void run() {
            before[0] = list.getListView().getChildAt(0).getHeight();
            before[1] = list.getListView().getChildAt(1).getTop();
        }});
        swipeFirst(true);
        main(new Runnable() { @Override public void run() {
            View row = list.getListView().getChildAt(0);
            assertEquals("Opening actions must not increase row height", before[0], row.getHeight());
            assertEquals("Opening actions must not move the next note", before[1], list.getListView().getChildAt(1).getTop());
            assertEquals(row.findViewById(R.id.note_card).getTop(), row.findViewById(R.id.note_actions).getTop());
            assertEquals(View.VISIBLE, row.findViewById(R.id.note_actions).getVisibility());
            assertEquals(View.INVISIBLE, row.findViewById(R.id.note_card).getVisibility());
        }});
        screenshot("左滑覆盖当前笔记");
        swipeFirst(false);
        main(new Runnable() { @Override public void run() {
            View row = list.getListView().getChildAt(0);
            assertEquals(View.GONE, row.findViewById(R.id.note_actions).getVisibility());
            assertEquals(View.VISIBLE, row.findViewById(R.id.note_card).getVisibility());
        }});
        swipeFirst(true);
        tap("标签", true); tap(prefix + "学习");
        assertEquals(id, value(a, "category_id", false));
        awaitRows(a, b, c);
        swipeFirst(true); tap("更多", true); tap("移动"); tap("未分类");
        assertEquals(0, value(a, "category_id", false));
        update(a, "category_id", id); update(c, "category_id", id);
        scope(prefix + "学习"); awaitRows(a, c);
        search(prefix + "丙"); awaitRows(c);
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(NotesList.class.getName(), null, false);
        main(new Runnable() { @Override public void run() { list.recreate(); }});
        list = (NotesList) getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
        getInstrumentation().removeMonitor(monitor); assertNotNull(list);
        awaitRows(c);
    }

    public void testTrashBatchRestoreAndPermanentDeleteAreScoped() throws Exception {
        select(0); click(R.id.select_all); click(R.id.delete_selected); tap("删除"); awaitRows();
        assertTrue(value(a, "deleted_at", true) > 0);
        assertEquals(1609459200300L, value(a, "modified", true));
        scope("最近删除"); search(prefix); awaitRows(c, b, a);
        screenshot("最近删除列表");
        select(0); click(R.id.select_all); click(R.id.restore_selected); awaitRows();
        scope("全部"); search(prefix); awaitRows(a, b, c);
        select(0); click(R.id.delete_selected); tap("删除"); awaitRows(b, c);
        scope("最近删除"); search(prefix); awaitRows(a);
        select(0); click(R.id.delete_selected); tap("取消"); awaitRows(a);
        click(R.id.delete_selected); tap("彻底删除"); awaitRows();
        Cursor cursor = resolver.query(NoteFeatures.TRASH_URI, null, "_id=?", new String[] { ""+a }, null);
        try { assertEquals(0, cursor.getCount()); } finally { cursor.close(); }
        assertEquals(1609459200200L, value(b, "modified", false));
    }

    public void testCategoryRenameDeletePreservesActiveAndDeletedNotes() {
        long id = category(prefix + "分类");
        update(a, "category_id", id); update(b, "category_id", id);
        update(b, "deleted_at", 1000);
        ContentValues name = new ContentValues(); name.put("name", prefix + "更名");
        Uri uri = ContentUris.withAppendedId(NoteFeatures.CATEGORIES_URI, id);
        assertEquals(1, resolver.update(uri, name, null, null));
        assertEquals(prefix + "更名", NoteFeatures.categoryName(getInstrumentation().getTargetContext(), id));
        assertEquals(1, resolver.delete(uri, null, null));
        assertEquals(0, value(a, "category_id", false)); assertEquals(0, value(b, "category_id", true));
        assertEquals(1000, value(b, "deleted_at", true));
        assertEquals(1609459200300L, value(a, "modified", false));
    }

    public void testBackgroundFontPersistenceAndUnchangedContentTime() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_EDIT, NoteFeatures.noteUri(a),
                getInstrumentation().getTargetContext(), NoteEditor.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        editor = getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        tap("背景与字号", true); tap("背景：浅黄", true); tap("字号：大", true);
        screenshot("背景与字号面板"); tap("完成");
        main(new Runnable() { @Override public void run() { editor.onBackPressed(); }});
        assertEquals(1, value(a, "background", false)); assertEquals(1609459200300L, value(a, "modified", false));
        editor = getInstrumentation().startActivitySync(intent); getInstrumentation().waitForIdleSync();
        main(new Runnable() { @Override public void run() {
            assertEquals(NoteFeatures.COLORS[1], ((ColorDrawable) editor.findViewById(R.id.editor_root).getBackground()).getColor());
            float sp = ((EditText) editor.findViewById(R.id.note)).getTextSize()
                    / editor.getResources().getDisplayMetrics().scaledDensity;
            assertEquals(20f, sp, 0.2f);
        }});
        screenshot("浅黄笔记编辑页");
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(NoteEditor.class.getName(), null, false);
        main(new Runnable() { @Override public void run() { editor.recreate(); }});
        Activity recreated = getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
        getInstrumentation().removeMonitor(monitor); assertNotNull(recreated); editor = recreated;
        getInstrumentation().waitForIdleSync();
        main(new Runnable() { @Override public void run() {
            assertEquals(NoteFeatures.COLORS[1], ((ColorDrawable) editor.findViewById(R.id.editor_root).getBackground()).getColor());
            editor.onBackPressed();
        }});
        assertEquals(1609459200300L, value(a, "modified", false));
    }

    @Override protected void tearDown() throws Exception {
        if (editor != null && !editor.isFinishing()) main(new Runnable() {
            @Override public void run() { editor.finish(); }
        });
        for (Uri uri : notes) resolver.delete(uri, null, null);
        for (Uri uri : categories) resolver.delete(uri, null, null);
        android.content.SharedPreferences.Editor sort = getInstrumentation().getTargetContext()
                .getSharedPreferences("NotesList", 0).edit();
        if (hadSort) sort.putInt("sort_order", oldSort); else sort.remove("sort_order"); sort.commit();
        android.content.SharedPreferences.Editor font = NoteFeatures.preferences(getInstrumentation().getTargetContext()).edit();
        if (hadFont) font.putInt("font_index", oldFont); else font.remove("font_index"); font.commit();
        super.tearDown();
    }
}
