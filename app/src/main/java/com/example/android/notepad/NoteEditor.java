/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

/** A single editor for blank notes, clipboard drafts and saved notes. */
public class NoteEditor extends Activity {
    private static final String TAG = "NoteEditor";
    private static final String[] PROJECTION = {
            NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
    };
    private EditText mTitle;
    private EditText mText;
    private Uri mUri;
    private boolean mIsNewNote;
    private boolean mDiscarded;
    private boolean mLoaded;
    private String mOriginalTitle = "";
    private String mOriginalText = "";
    private long mOriginalModified;

    public static class LinedEditText extends EditText {
        private final Rect mRect = new Rect();
        private final Paint mPaint = new Paint();

        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0xFFE6EAF0);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            for (int i = 0; i < getLineCount(); i++) {
                int baseline = getLineBounds(i, mRect);
                canvas.drawLine(mRect.left, baseline + 6, getWidth() - getPaddingRight(),
                        baseline + 6, mPaint);
            }
            super.onDraw(canvas);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.note_editor);
        mTitle = (EditText) findViewById(R.id.note_title);
        mText = (EditText) findViewById(R.id.note);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        if (state != null) {
            String uri = state.getString("note_uri");
            mUri = uri == null ? null : Uri.parse(uri);
            mIsNewNote = state.getBoolean("new_note");
            mOriginalTitle = state.getString("original_title", "");
            mOriginalText = state.getString("original_text", "");
            mOriginalModified = state.getLong("original_modified");
            mTitle.setText(state.getString("draft_title", ""));
            mText.setText(state.getString("draft_text", ""));
            mLoaded = true;
        } else {
            String action = getIntent().getAction();
            mIsNewNote = Intent.ACTION_INSERT.equals(action) || Intent.ACTION_PASTE.equals(action);
            if (mIsNewNote) {
                // Blank drafts do not create database rows until there is something to save.
                Bundle content = getIntent().getExtras();
                if (Intent.ACTION_PASTE.equals(action)
                        && (content == null || !content.containsKey(Intent.EXTRA_TEXT))) {
                    content = NoteClipboard.read(this);
                }
                if (Intent.ACTION_PASTE.equals(action) && content == null) {
                    Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show();
                    mDiscarded = true;
                    finish();
                    return;
                }
                if (content != null) {
                    mTitle.setText(content.getString(Intent.EXTRA_TITLE, ""));
                    mText.setText(content.getString(Intent.EXTRA_TEXT, ""));
                }
                mLoaded = true;
            } else if (Intent.ACTION_EDIT.equals(action) || Intent.ACTION_VIEW.equals(action)) {
                mUri = getIntent().getData();
                loadNote();
            }
        }
        if (!mLoaded) {
            Toast.makeText(this, R.string.error_message, Toast.LENGTH_SHORT).show();
            mDiscarded = true;
            finish();
            return;
        }
        setTitle(mIsNewNote ? R.string.title_create : R.string.title_edit);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                invalidateOptionsMenu();
            }
            @Override public void afterTextChanged(Editable value) { }
        };
        mTitle.addTextChangedListener(watcher);
        mText.addTextChangedListener(watcher);
    }

    private void loadNote() {
        if (mUri == null) return;
        Cursor cursor = getContentResolver().query(mUri, PROJECTION, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                mOriginalTitle = cursor.getString(0);
                mOriginalText = cursor.getString(1);
                if (mOriginalTitle == null) mOriginalTitle = "";
                if (mOriginalText == null) mOriginalText = "";
                mOriginalModified = cursor.getLong(2);
                mTitle.setText(isLegacyTitle(mOriginalTitle) ? "" : mOriginalTitle);
                mText.setText(mOriginalText);
                mLoaded = true;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    static boolean isLegacyTitle(String title) {
        return "<Untitled>".equalsIgnoreCase(title);
    }

    private String resolvedTitle() {
        String title = mTitle.getText().toString().trim();
        if (!TextUtils.isEmpty(title)) return title;
        String body = mText.getText().toString().trim();
        if (TextUtils.isEmpty(body)) return getString(R.string.untitled_note);
        int newline = body.indexOf('\n');
        if (newline >= 0) body = body.substring(0, newline);
        return body.substring(0, Math.min(30, body.length()));
    }

    private boolean hasContent() {
        return !TextUtils.isEmpty(mTitle.getText().toString().trim())
                || !TextUtils.isEmpty(mText.getText().toString().trim());
    }

    private boolean saveNote(boolean feedback) {
        if (!mLoaded || mDiscarded) return false;
        if (mIsNewNote && !hasContent()) {
            // If an autosaved new draft was cleared, do not leave its old content behind.
            if (mUri != null) {
                getContentResolver().delete(mUri, null, null);
                mUri = null;
                setResult(RESULT_CANCELED);
            }
            if (feedback) Toast.makeText(this, R.string.empty_note_hint, Toast.LENGTH_SHORT).show();
            return false;
        }
        String title = resolvedTitle();
        String body = mText.getText().toString();
        try {
            boolean changed = true;
            if (mUri != null) {
                Cursor cursor = getContentResolver().query(mUri, PROJECTION, null, null, null);
                try {
                    if (cursor == null || !cursor.moveToFirst()) {
                        if (feedback) Toast.makeText(this, R.string.error_message,
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    // Preserve the legacy title until the user actually changes this note.
                    boolean sameTitle = TextUtils.equals(title, cursor.getString(0))
                            || TextUtils.equals(mTitle.getText().toString(), cursor.getString(0))
                            || (isLegacyTitle(cursor.getString(0))
                            && TextUtils.isEmpty(mTitle.getText().toString()));
                    changed = !sameTitle || !TextUtils.equals(body, cursor.getString(1));
                } finally {
                    if (cursor != null) cursor.close();
                }
            }
            if (changed) {
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, body);
                values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
                if (mUri == null) {
                    mUri = getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);
                    if (mUri == null) throw new IllegalStateException("Insert returned null");
                } else if (getContentResolver().update(mUri, values, null, null) == 0) {
                    throw new IllegalStateException("Note no longer exists");
                }
            }
            setResult(RESULT_OK, new Intent().setData(mUri));
            if (feedback) Toast.makeText(this, R.string.note_saved, Toast.LENGTH_SHORT).show();
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to save note", error);
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    protected void onPause() {
        if (!mDiscarded) saveNote(false);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        // Save before recording the URI, avoiding duplicate inserts after recreation.
        saveNote(false);
        state.putString("note_uri", mUri == null ? null : mUri.toString());
        state.putBoolean("new_note", mIsNewNote);
        state.putString("original_title", mOriginalTitle);
        state.putString("original_text", mOriginalText);
        state.putLong("original_modified", mOriginalModified);
        state.putString("draft_title", mTitle.getText().toString());
        state.putString("draft_text", mText.getText().toString());
        super.onSaveInstanceState(state);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor_options_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_delete).setVisible(!mIsNewNote);
        menu.findItem(R.id.menu_revert).setTitle(mIsNewNote
                ? R.string.menu_discard_new : R.string.menu_revert);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        if (mDiscarded || (mIsNewNote && !hasContent()) || saveNote(false)) {
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.menu_save) {
            if (saveNote(true)) finish();
            return true;
        } else if (id == R.id.menu_delete) {
            new AlertDialog.Builder(this).setTitle(R.string.delete_note_title)
                    .setMessage(R.string.delete_note_message)
                    .setNegativeButton(R.string.button_cancel, null)
                    .setPositiveButton(R.string.button_delete, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            if (mUri != null) getContentResolver().delete(mUri, null, null);
                            mDiscarded = true;
                            finish();
                        }
                    }).show();
            return true;
        } else if (id == R.id.menu_revert) {
            new AlertDialog.Builder(this).setTitle(mIsNewNote
                    ? R.string.menu_discard_new : R.string.menu_revert)
                    .setMessage(mIsNewNote ? R.string.discard_new_message
                            : R.string.discard_changes_message)
                    .setNegativeButton(R.string.button_cancel, null)
                    .setPositiveButton(R.string.button_discard, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            if (mUri != null) {
                                if (mIsNewNote) {
                                    getContentResolver().delete(mUri, null, null);
                                } else {
                                    ContentValues values = new ContentValues();
                                    values.put(NotePad.Notes.COLUMN_NAME_TITLE, mOriginalTitle);
                                    values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalText);
                                    values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                                            mOriginalModified);
                                    getContentResolver().update(mUri, values, null, null);
                                }
                            }
                            mDiscarded = true;
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    }).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
