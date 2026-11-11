package com.example.android.notepad;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Set;

/** Exercises the real provider, list, search widget and editor without deleting user notes. */
public class NotePadBasicsTest extends ActivityInstrumentationTestCase2<NotesList> {
    private final List<Uri> inserted = new ArrayList<Uri>();
    private NotesList activity;
    private Activity editor;
    private String prefix;
    private long timestamp;
    private Uri first;
    private Uri second;
    private Uri third;

    public NotePadBasicsTest() {
        super(NotesList.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        prefix = "Basics" + System.nanoTime();
        timestamp = System.currentTimeMillis() - 120000;
        first = insert(prefix + "标题", "原始正文", timestamp);
        second = insert(prefix + "第二条", prefix + "正文 " + prefix + " 100% "
                + prefix + " _ " + prefix + " O'Reilly " + prefix + " \\", timestamp - 1000);
        third = insert(prefix + "第三条", prefix + " 100X " + prefix + " a", timestamp - 2000);
        activity = getActivity();
        getInstrumentation().waitForIdleSync();
    }

    private Uri insert(String title, String body, long modified) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, body);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, modified);
        Uri uri = getInstrumentation().getTargetContext().getContentResolver()
                .insert(NotePad.Notes.CONTENT_URI, values);
        assertNotNull(uri);
        inserted.add(uri);
        return uri;
    }

    @Override
    protected void tearDown() throws Exception {
        if (editor != null && !editor.isFinishing()) {
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override public void run() { editor.finish(); }
            });
            getInstrumentation().waitForIdleSync();
        }
        for (Uri uri : inserted) {
            getInstrumentation().getTargetContext().getContentResolver()
                    .delete(uri, null, null);
        }
        super.tearDown();
    }

    private void search(final String query) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                ((SearchView) activity.findViewById(R.id.search_notes)).setQuery(query, false);
            }
        });
    }

    private Set<Long> rows() {
        final Set<Long> ids = new HashSet<Long>();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < activity.getListAdapter().getCount(); i++) {
                    ids.add(activity.getListAdapter().getItemId(i));
                }
            }
        });
        return ids;
    }

    private void awaitRows(Uri... uris) {
        Set<Long> expected = new HashSet<Long>();
        for (Uri uri : uris) expected.add(ContentUris.parseId(uri));
        long deadline = SystemClock.uptimeMillis() + 8000;
        Set<Long> actual;
        do {
            actual = rows();
            if (actual.equals(expected)) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        assertEquals(expected, actual);
    }

    public void testSearchTitleBodyLiteralCharactersAndClear() {
        search(prefix + "标题");
        awaitRows(first);
        search(prefix + "正文");
        awaitRows(second);
        for (String literal : Arrays.asList("100%", "_", "O'Reilly", "\\")) {
            search(prefix + " " + literal);
            awaitRows(second);
        }
        search(prefix + "不存在");
        awaitRows();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                assertEquals(activity.getString(R.string.notes_search_empty),
                        ((TextView) activity.findViewById(android.R.id.empty)).getText().toString());
            }
        });
        search("");
        long deadline = SystemClock.uptimeMillis() + 8000;
        while (!rows().containsAll(Arrays.asList(ContentUris.parseId(first),
                ContentUris.parseId(second), ContentUris.parseId(third)))
                && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50);
        }
        assertTrue(rows().contains(ContentUris.parseId(first)));
        assertTrue(rows().contains(ContentUris.parseId(second)));
        assertTrue(rows().contains(ContentUris.parseId(third)));
    }

    public void testTimestampAndProviderChangeRefresh() {
        search(prefix + "标题");
        awaitRows(first);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                android.view.View row = activity.getListAdapter().getView(
                        0, null, activity.getListView());
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
                String formatted = format.format(new Date(timestamp));
                assertEquals(activity.getString(R.string.note_updated_at, formatted),
                        ((TextView) row.findViewById(android.R.id.text2)).getText().toString());
            }
        });
        search(prefix);
        awaitRows(first, second, third);
        activity.getContentResolver().delete(second, null, null);
        awaitRows(first, third);
    }

    private void openEditor(final Uri uri, final Class<? extends Activity> type,
                            final String action) {
        Instrumentation.ActivityMonitor monitor = getInstrumentation()
                .addMonitor(type.getName(), null, false);
        try {
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override public void run() {
                    activity.startActivity(new Intent(action, uri, activity, type));
                }
            });
            editor = getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
            assertNotNull("Editor did not open", editor);
            getInstrumentation().waitForIdleSync();
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    private void closeEditor() {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() { editor.finish(); }
        });
        getInstrumentation().waitForIdleSync();
    }

    private long modified(Uri uri) {
        Cursor c = activity.getContentResolver().query(uri,
                new String[] { NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE }, null, null, null);
        try {
            assertNotNull(c);
            assertTrue(c.moveToFirst());
            return c.getLong(0);
        } finally {
            if (c != null) c.close();
        }
    }

    public void testOpeningPreservesTimeAndBodyEditRefreshesSearch() {
        search(prefix + "标题");
        awaitRows(first);
        openEditor(first, NoteEditor.class, Intent.ACTION_EDIT);
        closeEditor();
        assertEquals(timestamp, modified(first));

        search(prefix + "新增正文");
        awaitRows();
        openEditor(first, NoteEditor.class, Intent.ACTION_EDIT);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                ((EditText) editor.findViewById(R.id.note)).setText(prefix + "新增正文");
            }
        });
        closeEditor();
        assertTrue(modified(first) > timestamp);
        awaitRows(first);
    }

    public void testTitleEditUpdatesTimeAndSearch() {
        search(prefix + "新标题");
        awaitRows();
        openEditor(first, NoteEditor.class, Intent.ACTION_EDIT);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override public void run() {
                ((EditText) editor.findViewById(R.id.note_title)).setText(prefix + "新标题");
            }
        });
        closeEditor();
        assertTrue(modified(first) > timestamp);
        awaitRows(first);
    }
}
