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
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

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
    private boolean mApplyingHistory;
    private long mMetadataTime;
    private final ArrayList<Bundle> mUndo = new ArrayList<Bundle>();
    private final ArrayList<Bundle> mRedo = new ArrayList<Bundle>();
    private final Handler mClock = new Handler();
    private final Runnable mRefreshTime = new Runnable() {
        @Override public void run() {
            if (mIsNewNote && !hasContent()) mMetadataTime = System.currentTimeMillis();
            updateEditorUi();
            mClock.postDelayed(this, 60000);
        }
    };

    /** Small line icons keep the toolbar consistent on this project's older Android APIs. */
    public static class EditorActionView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public EditorActionView(Context context, AttributeSet attrs) {
            super(context, attrs);
            setClickable(true);
            setFocusable(true);
        }

        @Override public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = 26 * getResources().getDisplayMetrics().density / 24;
            canvas.save();
            canvas.translate((getWidth() - 24 * scale) / 2, (getHeight() - 24 * scale) / 2);
            canvas.scale(scale, scale);
            paint.setColor(isEnabled() ? 0xFF42474D : 0xFFC8CBCF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            if (getId() == R.id.editor_back) {
                path.moveTo(15, 4); path.lineTo(7, 12); path.lineTo(15, 20);
            } else if (getId() == R.id.editor_save) {
                path.moveTo(4, 12); path.lineTo(10, 18); path.lineTo(21, 6);
            } else {
                if (getId() == R.id.editor_redo) { canvas.translate(24, 0); canvas.scale(-1, 1); }
                path.moveTo(4, 8); path.lineTo(14, 8);
                path.cubicTo(23, 8, 23, 20, 13, 20);
                path.moveTo(9, 3); path.lineTo(4, 8); path.lineTo(9, 13);
            }
            canvas.drawPath(path, paint);
            canvas.restore();
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.note_editor);
        mTitle = (EditText) findViewById(R.id.note_title);
        mText = (EditText) findViewById(R.id.note);
        mMetadataTime = System.currentTimeMillis();
        findViewById(R.id.editor_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { onBackPressed(); }
        });
        findViewById(R.id.editor_save).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { if (saveNote(true)) finish(); }
        });
        findViewById(R.id.editor_undo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { moveHistory(mUndo, mRedo); }
        });
        findViewById(R.id.editor_redo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { moveHistory(mRedo, mUndo); }
        });

        if (state != null) {
            String uri = state.getString("note_uri");
            mUri = uri == null ? null : Uri.parse(uri);
            mIsNewNote = state.getBoolean("new_note");
            mMetadataTime = state.getLong("metadata_time", mMetadataTime);
            ArrayList<Bundle> undo = state.getParcelableArrayList("undo_history");
            ArrayList<Bundle> redo = state.getParcelableArrayList("redo_history");
            if (undo != null) mUndo.addAll(undo);
            if (redo != null) mRedo.addAll(redo);
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
        watchEdits(mTitle);
        watchEdits(mText);
        if (state != null && state.getBoolean("title_focused")) mTitle.requestFocus();
        else mText.requestFocus();
        if (state != null) {
            setSelection(mTitle, state.getInt("title_start"), state.getInt("title_end"));
            setSelection(mText, state.getInt("body_start"), state.getInt("body_end"));
        }
        updateEditorUi();
    }

    private Bundle captureEdit() {
        Bundle state = new Bundle();
        state.putString("title", mTitle.getText().toString());
        state.putString("body", mText.getText().toString());
        state.putBoolean("title_focused", mTitle.hasFocus());
        state.putInt("title_start", mTitle.getSelectionStart());
        state.putInt("title_end", mTitle.getSelectionEnd());
        state.putInt("body_start", mText.getSelectionStart());
        state.putInt("body_end", mText.getSelectionEnd());
        return state;
    }

    private void watchEdits(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            private Bundle before;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!mApplyingHistory) before = captureEdit();
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                if (mApplyingHistory || before == null) return;
                if (!TextUtils.equals(before.getString("title"), mTitle.getText())
                        || !TextUtils.equals(before.getString("body"), mText.getText())) {
                    mUndo.add(before);
                    if (mUndo.size() > 100) mUndo.remove(0);
                    mRedo.clear();
                    mMetadataTime = System.currentTimeMillis();
                    updateEditorUi();
                }
                before = null;
            }
        });
    }

    private static void setSelection(EditText field, int start, int end) {
        int length = field.length();
        field.setSelection(Math.max(0, Math.min(start, length)),
                Math.max(0, Math.min(end, length)));
    }

    private void moveHistory(ArrayList<Bundle> from, ArrayList<Bundle> to) {
        if (from.isEmpty()) return;
        to.add(captureEdit());
        Bundle previous = from.remove(from.size() - 1);
        mApplyingHistory = true;
        try {
            mTitle.setText(previous.getString("title", ""));
            mText.setText(previous.getString("body", ""));
            if (previous.getBoolean("title_focused")) mTitle.requestFocus();
            else mText.requestFocus();
            setSelection(mTitle, previous.getInt("title_start"), previous.getInt("title_end"));
            setSelection(mText, previous.getInt("body_start"), previous.getInt("body_end"));
        } finally { mApplyingHistory = false; }
        mMetadataTime = System.currentTimeMillis();
        updateEditorUi();
    }

    private void updateEditorUi() {
        if (!mLoaded) return;
        findViewById(R.id.editor_undo).setEnabled(!mUndo.isEmpty());
        findViewById(R.id.editor_redo).setEnabled(!mRedo.isEmpty());
        findViewById(R.id.editor_save).setEnabled(!mIsNewNote || hasContent());
        int count = 0;
        String body = mText.getText().toString();
        for (int offset = 0; offset < body.length();) {
            int codePoint = body.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) count++;
            offset += Character.charCount(codePoint);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        String date = format.format(new Date(mMetadataTime));
        ((TextView) findViewById(R.id.editor_metadata)).setText(
                getString(R.string.editor_metadata, date, count));
    }

    // Keep recreation state bounded; the most recent steps are the most useful ones.
    private ArrayList<Bundle> savedHistory(ArrayList<Bundle> history) {
        ArrayList<Bundle> result = new ArrayList<Bundle>();
        int characters = 0;
        for (int i = history.size() - 1; i >= 0 && result.size() < 20; i--) {
            Bundle state = history.get(i);
            characters += state.getString("title", "").length() + state.getString("body", "").length();
            if (characters > 20000) break;
            result.add(0, state);
        }
        return result;
    }

    private void loadNote() {
        if (mUri == null) return;
        Cursor cursor = getContentResolver().query(mUri, PROJECTION, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                String title = cursor.getString(0);
                mMetadataTime = cursor.getLong(2);
                mTitle.setText(isLegacyTitle(title) ? "" : title);
                mText.setText(cursor.getString(1));
                mLoaded = true;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    static boolean isLegacyTitle(String title) {
        return "<Untitled>".equalsIgnoreCase(title);
    }

    private boolean hasContent() {
        return !TextUtils.isEmpty(mTitle.getText().toString().trim())
                || !TextUtils.isEmpty(mText.getText().toString().trim());
    }

    private boolean saveNote(boolean feedback) {
        if (!mLoaded || mDiscarded) return false;
        try {
            if (mIsNewNote && !hasContent()) {
                // If an autosaved new draft was cleared, do not leave its old content behind.
                if (mUri != null) {
                    getContentResolver().delete(mUri, null, null);
                    mUri = null;
                    setResult(RESULT_CANCELED);
                }
                if (feedback) Toast.makeText(this, R.string.empty_note_hint, Toast.LENGTH_SHORT).show();
                return true;
            }
            // An optional title stays empty; the list derives a display summary from the body.
            String title = mTitle.getText().toString().trim();
            String body = mText.getText().toString();
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

    @Override protected void onResume() {
        super.onResume();
        mClock.removeCallbacks(mRefreshTime);
        mClock.post(mRefreshTime);
    }

    @Override protected void onDestroy() {
        mClock.removeCallbacks(mRefreshTime);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        mClock.removeCallbacks(mRefreshTime);
        if (!mDiscarded) saveNote(false);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        // Save before recording the URI, avoiding duplicate inserts after recreation.
        saveNote(false);
        state.putString("note_uri", mUri == null ? null : mUri.toString());
        state.putBoolean("new_note", mIsNewNote);
        state.putLong("metadata_time", mMetadataTime);
        state.putParcelableArrayList("undo_history", savedHistory(mUndo));
        state.putParcelableArrayList("redo_history", savedHistory(mRedo));
        state.putBoolean("title_focused", mTitle.hasFocus());
        state.putInt("title_start", mTitle.getSelectionStart());
        state.putInt("title_end", mTitle.getSelectionEnd());
        state.putInt("body_start", mText.getSelectionStart());
        state.putInt("body_end", mText.getSelectionEnd());
        state.putString("draft_title", mTitle.getText().toString());
        state.putString("draft_text", mText.getText().toString());
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (mDiscarded || saveNote(false)) finish();
    }
}
