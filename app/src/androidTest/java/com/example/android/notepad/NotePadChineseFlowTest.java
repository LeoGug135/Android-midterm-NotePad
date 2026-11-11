package com.example.android.notepad;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.SearchView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** Regression coverage for the Chinese menus and blank/clipboard draft lifecycle. */
public class NotePadChineseFlowTest extends ActivityInstrumentationTestCase2<NotesList> {
    private NotesList list;
    private Activity editor;
    private String prefix;
    private ClipboardManager clipboard;
    private ClipData oldClip;

    public NotePadChineseFlowTest() { super(NotesList.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        prefix = "交互验证" + System.nanoTime();
        list = getActivity();
        getInstrumentation().waitForIdleSync();
        awaitFocus(list);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                clipboard = (ClipboardManager) list.getSystemService(Context.CLIPBOARD_SERVICE);
                oldClip = clipboard.getPrimaryClip();
            }
        });
    }

    @Override protected void tearDown() throws Exception {
        if (editor != null && !editor.isFinishing()) closeEditor();
        list.getContentResolver().delete(NotePad.Notes.CONTENT_URI,
                NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ?",
                new String[] { prefix + "%" });
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                clipboard.setPrimaryClip(oldClip == null ? ClipData.newPlainText("", "") : oldClip);
            }
        });
        super.tearDown();
    }

    private void open(final Intent intent) {
        Instrumentation.ActivityMonitor monitor = getInstrumentation()
                .addMonitor(NoteEditor.class.getName(), null, false);
        try {
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override public void run() { list.startActivity(intent); }
            });
            editor = getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
            assertNotNull(editor);
            getInstrumentation().waitForIdleSync();
            awaitFocus(editor);
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    private Intent newIntent(String action) {
        return new Intent(action, NotePad.Notes.CONTENT_URI, list, NoteEditor.class);
    }

    private void edit(final String title, final String body) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                ((EditText) editor.findViewById(R.id.note_title)).setText(title);
                ((EditText) editor.findViewById(R.id.note)).setText(body);
            }
        });
    }

    private void closeEditor() {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() { editor.finish(); }
        });
        getInstrumentation().waitForIdleSync();
        awaitFocus(list);
    }

    private void awaitFocus(final Activity activity) {
        final boolean[] focused = new boolean[1];
        long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override public void run() { focused[0] = activity.hasWindowFocus(); }
            });
            if (focused[0]) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Activity did not gain input focus");
    }

    private void recreateEditor() {
        Instrumentation.ActivityMonitor monitor = getInstrumentation()
                .addMonitor(NoteEditor.class.getName(), null, false);
        try {
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override public void run() { editor.recreate(); }
            });
            editor = getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
            assertNotNull(editor);
            getInstrumentation().waitForIdleSync();
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    private int countAll() {
        Cursor cursor = list.getContentResolver().query(NotePad.Notes.CONTENT_URI,
                new String[] { NotePad.Notes._ID }, null, null, null);
        try { return cursor.getCount(); } finally { cursor.close(); }
    }

    private Cursor ownNotes() {
        return list.getContentResolver().query(NotePad.Notes.CONTENT_URI, null,
                NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ?",
                new String[] { prefix + "%" }, null);
    }

    private void assertOwnCount(int count) {
        Cursor cursor = ownNotes();
        try { assertEquals(count, cursor.getCount()); } finally { cursor.close(); }
    }

    private void screenshot(String name) throws Exception {
        SystemClock.sleep(350);
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(getInstrumentation().getTargetContext().getFilesDir(), "ui_checks");
        if (!directory.exists()) assertTrue(directory.mkdirs());
        File file = new File(directory, name + ".png");
        FileOutputStream stream = new FileOutputStream(file);
        try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        finally { stream.close(); bitmap.recycle(); }
    }

    private void clickDialogButton(String text) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation()
                    .getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isClickable() && text.contentEquals(node.getText())) {
                        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                        getInstrumentation().waitForIdleSync();
                        return;
                    }
                }
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Dialog button was not found: " + text);
    }

    public void testBlankAndClipboardUseSameChineseEditor() throws Exception {
        final int before = countAll();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                PopupMenu popup = new PopupMenu(list, list.getListView());
                list.onCreateOptionsMenu(popup.getMenu());
                assertEquals(2, popup.getMenu().size());
                assertEquals("新建", popup.getMenu().findItem(R.id.menu_add).getTitle());
                assertEquals("笔记排序", popup.getMenu().findItem(R.id.menu_sort).getTitle());
            }
        });
        open(newIntent(Intent.ACTION_INSERT));
        assertEquals("新建笔记", editor.getTitle());
        assertNotNull(editor.findViewById(R.id.note_title));
        screenshot("中文空白新建");
        clickEditor(R.id.editor_save);
        assertEquals(before, countAll());
        closeEditor();
        assertEquals(before, countAll());

        final Bundle[] contents = new Bundle[1];
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                clipboard.setPrimaryClip(ClipData.newPlainText("验证", "来自剪贴板的正文"));
                contents[0] = NoteClipboard.read(list);
            }
        });
        assertNotNull(contents[0]);
        open(newIntent(Intent.ACTION_PASTE).putExtras(contents[0]));
        assertEquals("新建笔记", editor.getTitle());
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertEquals("来自剪贴板的正文",
                        ((EditText) editor.findViewById(R.id.note)).getText().toString());
            }
        });
        edit(prefix + "剪贴板笔记", "来自剪贴板的正文");
        screenshot("中文剪贴板新建");
        clickEditor(R.id.editor_save);
        getInstrumentation().waitForIdleSync();
        assertOwnCount(1);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                ((SearchView) list.findViewById(R.id.search_notes)).setQuery(prefix, false);
            }
        });
        SystemClock.sleep(350);
        screenshot("中文笔记列表");
    }

    public void testEmptyClipboardDoesNotCreateNote() {
        final int before = countAll();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                clipboard.setPrimaryClip(ClipData.newPlainText("", "   "));
                assertNull(NoteClipboard.read(list));
            }
        });
        getInstrumentation().waitForIdleSync();
        assertEquals(before, countAll());
    }

    public void testDraftRecreationDoesNotDuplicateAndTitleOnlyIsSaved() {
        open(newIntent(Intent.ACTION_INSERT));
        edit(prefix + "仅标题", "");
        recreateEditor();
        assertEquals("新建笔记", editor.getTitle());
        assertOwnCount(1);
        clickEditor(R.id.editor_save);
        getInstrumentation().waitForIdleSync();
        assertOwnCount(1);
    }

    private void clickEditor(final int id) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() { editor.findViewById(id).performClick(); }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testUndoNewDraftAfterAutomaticSaveLeavesNoEmptyRow() {
        open(newIntent(Intent.ACTION_INSERT));
        edit(prefix + "临时草稿", "撤销后不保留");
        recreateEditor();
        assertOwnCount(1);
        clickEditor(R.id.editor_undo);
        clickEditor(R.id.editor_undo);
        clickEditor(R.id.editor_back);
        assertOwnCount(0);
    }

    public void testBackSavesExistingNoteAndNoExtraEditorMenu() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, prefix + "返回验证");
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "测试内容");
        Uri uri = list.getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);
        open(new Intent(Intent.ACTION_EDIT, uri, list, NoteEditor.class));
        assertEquals("编辑笔记", editor.getTitle());
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                PopupMenu popup = new PopupMenu(editor, editor.findViewById(R.id.note));
                editor.onCreateOptionsMenu(popup.getMenu());
                assertEquals(0, popup.getMenu().size());
            }
        });
        edit(prefix + "返回验证", "返回时保存的内容");
        clickEditor(R.id.editor_back);
        Cursor cursor = ownNotes();
        try {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("返回时保存的内容", cursor.getString(
                    cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_NOTE)));
        } finally { cursor.close(); }
    }

    public void testUndoRedoExistingTitleAndBodyAfterRecreation() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, prefix + "原始标题");
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "原始正文");
        Uri uri = list.getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);
        open(new Intent(Intent.ACTION_EDIT, uri, list, NoteEditor.class));
        edit(prefix + "修改后标题", "修改后正文");
        recreateEditor();
        clickEditor(R.id.editor_undo);
        clickEditor(R.id.editor_undo);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertEquals(prefix + "原始标题", ((EditText) editor.findViewById(
                        R.id.note_title)).getText().toString());
                assertEquals("原始正文", ((EditText) editor.findViewById(
                        R.id.note)).getText().toString());
            }
        });
        clickEditor(R.id.editor_redo);
        clickEditor(R.id.editor_redo);
        clickEditor(R.id.editor_save);
        Cursor cursor = ownNotes();
        try {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals(prefix + "修改后标题", cursor.getString(
                    cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_TITLE)));
            assertEquals("修改后正文", cursor.getString(
                    cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_NOTE)));
        } finally { cursor.close(); }
    }
}
