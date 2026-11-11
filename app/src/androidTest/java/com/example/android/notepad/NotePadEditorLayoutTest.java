package com.example.android.notepad;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Uses only uniquely marked test notes and keeps user notes intact. */
public class NotePadEditorLayoutTest extends ActivityInstrumentationTestCase2<NotesList> {
    private NotesList list;
    private Activity editor;
    private String prefix;

    public NotePadEditorLayoutTest() { super(NotesList.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        prefix = "简洁编辑" + System.nanoTime();
        list = getActivity();
        getInstrumentation().waitForIdleSync();
    }

    @Override protected void tearDown() throws Exception {
        if (editor != null && !editor.isFinishing()) {
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override public void run() { editor.finish(); }
            });
            getInstrumentation().waitForIdleSync();
        }
        list.getContentResolver().delete(NotePad.Notes.CONTENT_URI,
                "title LIKE ? OR note LIKE ?", new String[] {prefix + "%", prefix + "%"});
        super.tearDown();
    }

    private void open(final Uri uri) {
        Instrumentation.ActivityMonitor monitor = getInstrumentation()
                .addMonitor(NoteEditor.class.getName(), null, false);
        try {
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override public void run() {
                    list.startActivity(new Intent(uri == null ? Intent.ACTION_INSERT : Intent.ACTION_EDIT,
                            uri == null ? NotePad.Notes.CONTENT_URI : uri, list, NoteEditor.class));
                }
            });
            editor = getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
            assertNotNull(editor);
            getInstrumentation().waitForIdleSync();
            long deadline = SystemClock.uptimeMillis() + 8000;
            while (!editor.hasWindowFocus() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
            assertTrue(editor.hasWindowFocus());
        } finally { getInstrumentation().removeMonitor(monitor); }
    }

    private void click(final int id) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() { editor.findViewById(id).performClick(); }
        });
        getInstrumentation().waitForIdleSync();
    }

    private void setText(final int id, final String text) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                EditText field = (EditText) editor.findViewById(id);
                field.requestFocus();
                field.setText(text);
                field.setSelection(field.length());
            }
        });
    }

    private void assertText(final int id, final String expected) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertEquals(expected, ((TextView) editor.findViewById(id)).getText().toString());
            }
        });
    }

    private Cursor ownNotes() {
        return list.getContentResolver().query(NotePad.Notes.CONTENT_URI, null,
                "title LIKE ? OR note LIKE ?", new String[] {prefix + "%", prefix + "%"}, null);
    }

    private Uri savedUri() {
        Cursor cursor = ownNotes();
        try {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            return Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, cursor.getString(
                    cursor.getColumnIndexOrThrow(NotePad.Notes._ID)));
        } finally { cursor.close(); }
    }

    public void testBlankPageStartsInBodyAndDoesNotInsert() throws Exception {
        open(null);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertTrue(editor.findViewById(R.id.note).hasFocus());
                assertEquals("标题", ((EditText) editor.findViewById(R.id.note_title)).getHint());
                assertFalse(editor.findViewById(R.id.editor_undo).isEnabled());
                assertFalse(editor.findViewById(R.id.editor_redo).isEnabled());
                assertFalse(editor.findViewById(R.id.editor_save).isEnabled());
                SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA);
                format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
                String date = format.format(new Date());
                assertTrue(((TextView) editor.findViewById(R.id.editor_metadata)).getText()
                        .toString().startsWith(date));
                assertTrue(((TextView) editor.findViewById(R.id.editor_metadata)).getText()
                        .toString().endsWith("0 字"));
            }
        });
        SystemClock.sleep(400);
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        File dir = new File(getInstrumentation().getTargetContext().getFilesDir(), "editor_checks");
        if (!dir.exists()) assertTrue(dir.mkdirs());
        FileOutputStream stream = new FileOutputStream(new File(dir, "简洁新建笔记.png"));
        try { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
        finally { stream.close(); bitmap.recycle(); }
        click(R.id.editor_back);
        Cursor cursor = ownNotes();
        try { assertEquals(0, cursor.getCount()); } finally { cursor.close(); }
    }

    public void testBodyOnlyBackSavesAndReopensWithEmptyTitle() {
        open(null);
        setText(R.id.note, prefix + "正文首行\n第二行");
        click(R.id.editor_back);
        Uri uri = savedUri();
        Cursor cursor = ownNotes();
        long modified;
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_TITLE)));
            modified = cursor.getLong(cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
        } finally { cursor.close(); }
        final long id = android.content.ContentUris.parseId(uri);
        final boolean[] summaryFound = {false};
        long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override public void run() {
                    for (int i = 0; i < list.getListAdapter().getCount(); i++) {
                        if (list.getListAdapter().getItemId(i) == id) {
                            View row = list.getListAdapter().getView(i, null, list.getListView());
                            assertEquals(prefix + "正文首行", ((TextView) row.findViewById(
                                    android.R.id.text1)).getText().toString());
                            summaryFound[0] = true;
                        }
                    }
                }
            });
            if (!summaryFound[0]) SystemClock.sleep(50);
        } while (!summaryFound[0] && SystemClock.uptimeMillis() < deadline);
        assertTrue(summaryFound[0]);
        open(uri);
        assertText(R.id.note_title, "");
        assertText(R.id.note, prefix + "正文首行\n第二行");
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() { editor.onBackPressed(); }
        });
        getInstrumentation().waitForIdleSync();
        cursor = ownNotes();
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(modified, cursor.getLong(cursor.getColumnIndexOrThrow(
                    NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)));
        } finally { cursor.close(); }
    }

    public void testUndoRedoAndNewEditClearRedo() {
        open(null);
        setText(R.id.note, prefix + "甲");
        setText(R.id.note, prefix + "甲乙");
        click(R.id.editor_undo);
        assertText(R.id.note, prefix + "甲");
        click(R.id.editor_redo);
        assertText(R.id.note, prefix + "甲乙");
        click(R.id.editor_undo);
        setText(R.id.note, prefix + "丙");
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertFalse(editor.findViewById(R.id.editor_redo).isEnabled());
            }
        });
        click(R.id.editor_save);
        savedUri();
    }

    public void testCountIgnoresWhitespaceAndHandlesEmoji() {
        open(null);
        setText(R.id.note_title, prefix + "标题不计入正文");
        setText(R.id.note, "你 A\n😀　B");
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                String metadata = ((TextView) editor.findViewById(R.id.editor_metadata)).getText().toString();
                assertTrue(metadata, metadata.endsWith("4 字"));
            }
        });
        click(R.id.editor_undo);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertTrue(((TextView) editor.findViewById(R.id.editor_metadata)).getText()
                        .toString().endsWith("0 字"));
            }
        });
        click(R.id.editor_back);
        savedUri();
    }

    public void testBeijingTimeWithUtcDeviceDefaultPreservesTimestamp() {
        final TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, prefix + "东八区验证");
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, "验证时间显示");
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, 1609459200000L);
            final Uri uri = list.getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);
            Instrumentation.ActivityMonitor monitor = getInstrumentation()
                    .addMonitor(NotesList.class.getName(), null, false);
            try {
                getInstrumentation().runOnMainSync(new Runnable() {
                    @Override public void run() { list.recreate(); }
                });
                list = (NotesList) getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
                assertNotNull(list);
                getInstrumentation().waitForIdleSync();
            } finally { getInstrumentation().removeMonitor(monitor); }
            final long id = android.content.ContentUris.parseId(uri);
            final boolean[] found = {false};
            long deadline = SystemClock.uptimeMillis() + 8000;
            do {
                getInstrumentation().runOnMainSync(new Runnable() {
                    @Override public void run() {
                        for (int i = 0; i < list.getListAdapter().getCount(); i++) {
                            if (list.getListAdapter().getItemId(i) == id) {
                                View row = list.getListAdapter().getView(i, null, list.getListView());
                                assertEquals("更新于：2021-01-01 08:00", ((TextView) row.findViewById(
                                        android.R.id.text2)).getText().toString());
                                found[0] = true;
                            }
                        }
                    }
                });
                if (!found[0]) SystemClock.sleep(50);
            } while (!found[0] && SystemClock.uptimeMillis() < deadline);
            assertTrue(found[0]);
            open(uri);
            assertText(R.id.editor_metadata, "2021/01/01 08:00 | 6 字");
            click(R.id.editor_back);
            Cursor cursor = ownNotes();
            try {
                assertTrue(cursor.moveToFirst());
                assertEquals(1609459200000L, cursor.getLong(cursor.getColumnIndexOrThrow(
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)));
            } finally { cursor.close(); }
        } finally { TimeZone.setDefault(previous); }
    }
}
