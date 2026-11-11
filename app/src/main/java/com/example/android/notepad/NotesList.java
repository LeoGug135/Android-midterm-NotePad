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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Searchable notes with stable-ID selection and a persisted sort order. */
public class NotesList extends ListActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final int NOTES_LOADER = 0;
    private static final String STATE_QUERY = "search_query";
    private static final String SORT_PREFERENCE = "sort_order";
    private static final String[] PROJECTION = {
            NotePad.Notes._ID, NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, NotePad.Notes.COLUMN_NAME_NOTE
    };
    private static final String[] SORT_ORDERS = {
            "modified DESC, _id DESC", "modified ASC, _id ASC",
            "created DESC, _id DESC", "created ASC, _id ASC"
    };
    private final Set<Long> mSelectedIds = new HashSet<Long>();
    private SimpleCursorAdapter mAdapter;
    private TextView mEmptyView;
    private SearchView mSearchView;
    private Button mSelectAll;
    private Button mDeleteSelected;
    private String mQuery = "";
    private boolean mSelecting;
    private boolean mLoading = true;
    private int mSortOrder;
    private AlertDialog mSortDialog;
    private AlertDialog mDeleteDialog;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.notes_list);
        if (getIntent().getData() == null) getIntent().setData(NotePad.Notes.CONTENT_URI);
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        mSortOrder = prefs.getInt(SORT_PREFERENCE, 0);
        if (mSortOrder < 0 || mSortOrder >= SORT_ORDERS.length) mSortOrder = 0;
        if (state != null) {
            mQuery = state.getString(STATE_QUERY, "");
            mSelecting = state.getBoolean("selecting");
            long[] ids = state.getLongArray("selected_ids");
            if (ids != null) for (long id : ids) mSelectedIds.add(id);
        }
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        mAdapter = new SimpleCursorAdapter(this, R.layout.noteslist_item, null,
                new String[] { NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE },
                new int[] { android.R.id.text1, android.R.id.text2 }, 0) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                boolean selected = mSelectedIds.contains(getItemId(position));
                CheckBox checkBox = (CheckBox) row.findViewById(R.id.note_selected);
                checkBox.setVisibility(mSelecting ? View.VISIBLE : View.GONE);
                checkBox.setChecked(selected);
                row.setBackgroundColor(selected ? Color.rgb(229, 239, 255) : Color.TRANSPARENT);
                return row;
            }
        };
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override public boolean setViewValue(View view, Cursor cursor, int column) {
                if (view.getId() == android.R.id.text2) {
                    ((TextView) view).setText(getString(R.string.note_updated_at,
                            format.format(new Date(cursor.getLong(column)))));
                    return true;
                } else if (view.getId() == android.R.id.text1) {
                    String title = cursor.getString(column);
                    if (TextUtils.isEmpty(title) || NoteEditor.isLegacyTitle(title)) {
                        String body = cursor.getString(cursor.getColumnIndexOrThrow(
                                NotePad.Notes.COLUMN_NAME_NOTE));
                        title = body == null ? "" : body.trim();
                        int newline = title.indexOf('\n');
                        if (newline >= 0) title = title.substring(0, newline);
                        title = title.substring(0, Math.min(30, title.length()));
                    }
                    ((TextView) view).setText(TextUtils.isEmpty(title)
                            ? getString(R.string.untitled_note) : title);
                    return true;
                }
                return false;
            }
        });
        setListAdapter(mAdapter);
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View row,
                                                     int position, long id) {
                if (mLoading) return true;
                if (!mSelecting) {
                    mSelecting = true;
                    mSelectedIds.clear();
                    mSelectedIds.add(id);
                } else {
                    toggleSelected(id);
                }
                mSearchView.clearFocus();
                android.view.inputmethod.InputMethodManager keyboard =
                        (android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                keyboard.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
                updateSelectionUi();
                return true;
            }
        });
        mEmptyView = (TextView) findViewById(android.R.id.empty);
        mSearchView = (SearchView) findViewById(R.id.search_notes);
        mSelectAll = (Button) findViewById(R.id.select_all);
        mDeleteSelected = (Button) findViewById(R.id.delete_selected);
        mSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { toggleSelectAll(); }
        });
        findViewById(R.id.cancel_selection).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { exitSelection(); }
        });
        mDeleteSelected.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { confirmDeleteSelected(); }
        });
        updateEmptyMessage();
        updateSelectionUi();
        mSearchView.setQuery(mQuery, false);
        mSearchView.clearFocus();
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                mSearchView.clearFocus();
                return true;
            }
            @Override public boolean onQueryTextChange(String text) {
                String query = text.trim();
                if (!TextUtils.equals(mQuery, query)) {
                    // A different search defines a different selection scope.
                    exitSelection();
                    mQuery = query;
                    updateEmptyMessage();
                    reloadNotes();
                }
                return true;
            }
        });
        getLoaderManager().initLoader(NOTES_LOADER, null, this);
    }

    private void updateEmptyMessage() {
        mEmptyView.setText(TextUtils.isEmpty(mQuery)
                ? R.string.notes_empty : R.string.notes_search_empty);
    }

    private void reloadNotes() {
        mLoading = true;
        getListView().setEnabled(false);
        updateSelectionUi();
        getLoaderManager().restartLoader(NOTES_LOADER, null, this);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(STATE_QUERY, mQuery);
        state.putBoolean("selecting", mSelecting);
        long[] ids = new long[mSelectedIds.size()];
        int index = 0;
        for (long id : mSelectedIds) ids[index++] = id;
        state.putLongArray("selected_ids", ids);
        super.onSaveInstanceState(state);
    }

    @Override public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String selection = null;
        String[] arguments = null;
        if (!TextUtils.isEmpty(mQuery)) {
            String escaped = mQuery.replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_");
            String pattern = "%" + escaped + "%";
            selection = "(title LIKE ? ESCAPE '\\' OR note LIKE ? ESCAPE '\\')";
            arguments = new String[] { pattern, pattern };
        }
        return new CursorLoader(this, getIntent().getData(), PROJECTION,
                selection, arguments, SORT_ORDERS[mSortOrder]);
    }

    @Override public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
        mLoading = false;
        getListView().setEnabled(true);
        Set<Long> visibleIds = new HashSet<Long>();
        for (int i = 0; i < mAdapter.getCount(); i++) visibleIds.add(mAdapter.getItemId(i));
        mSelectedIds.retainAll(visibleIds);
        if (visibleIds.isEmpty()) mSelecting = false;
        updateSelectionUi();
    }

    @Override public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    private void toggleSelected(long id) {
        if (!mSelectedIds.remove(id)) mSelectedIds.add(id);
    }

    private void exitSelection() {
        mSelecting = false;
        mSelectedIds.clear();
        updateSelectionUi();
    }

    private void toggleSelectAll() {
        if (mLoading) return;
        if (mSelectedIds.size() == mAdapter.getCount()) {
            mSelectedIds.clear();
        } else {
            for (int i = 0; i < mAdapter.getCount(); i++) mSelectedIds.add(mAdapter.getItemId(i));
        }
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        if (getActionBar() != null) {
            if (mSelecting) getActionBar().hide(); else getActionBar().show();
        }
        findViewById(R.id.selection_bar).setVisibility(mSelecting ? View.VISIBLE : View.GONE);
        findViewById(R.id.selection_footer).setVisibility(mSelecting ? View.VISIBLE : View.GONE);
        mSearchView.setVisibility(mSelecting ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.selection_count)).setText(
                getString(R.string.notes_selected_count, mSelectedIds.size()));
        boolean all = mAdapter.getCount() > 0 && mSelectedIds.size() == mAdapter.getCount();
        mSelectAll.setText(all ? R.string.notes_unselect_all : R.string.notes_select_all);
        mSelectAll.setEnabled(!mLoading && mAdapter.getCount() > 0);
        mDeleteSelected.setText(getString(R.string.notes_delete_selected, mSelectedIds.size()));
        mDeleteSelected.setEnabled(!mLoading && !mSelectedIds.isEmpty());
        ((TextView) findViewById(R.id.selection_scope)).setText(
                TextUtils.isEmpty(mQuery) ? R.string.notes_select_scope_all
                        : R.string.notes_select_scope_search);
        mAdapter.notifyDataSetChanged();
    }

    private void confirmDeleteSelected() {
        if (mSelectedIds.isEmpty() || mLoading) return;
        // Snapshot database IDs: a later reordering cannot change which rows are deleted.
        final ArrayList<Long> ids = new ArrayList<Long>(mSelectedIds);
        mDeleteDialog = new AlertDialog.Builder(this).setTitle(R.string.notes_batch_delete_title)
                .setMessage(getString(R.string.notes_batch_delete_message, ids.size()))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        // Numeric IDs come only from the provider, never from user input.
                        // One SQL statement is atomic and avoids SQLite's bound-argument limit.
                        StringBuilder where = new StringBuilder("_id IN (");
                        for (int i = 0; i < ids.size(); i++) {
                            if (i > 0) where.append(',');
                            where.append(ids.get(i).longValue());
                        }
                        where.append(')');
                        try {
                            int count = getContentResolver().delete(getIntent().getData(),
                                    where.toString(), null);
                            exitSelection();
                            Toast.makeText(NotesList.this,
                                    getString(R.string.notes_deleted_count, count), Toast.LENGTH_SHORT).show();
                        } catch (RuntimeException error) {
                            Toast.makeText(NotesList.this, R.string.notes_delete_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }).create();
        mDeleteDialog.show();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.list_options_menu, menu);
        // A real action button opens the sheet directly, with no intermediate overflow menu.
        TextView more = new TextView(this);
        more.setText("\u22ee");
        more.setTextSize(28);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription(getString(R.string.notes_sort_title));
        int size = (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
        more.setLayoutParams(new ViewGroup.LayoutParams(size, ViewGroup.LayoutParams.MATCH_PARENT));
        android.util.TypedValue background = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true)) {
            more.setBackgroundResource(background.resourceId);
        }
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showSortDialog(); }
        });
        menu.findItem(R.id.menu_sort).setActionView(more);
        return true;
    }

    private void showSortDialog() {
        if (mSortDialog != null && mSortDialog.isShowing()) return;
        mSortDialog = new AlertDialog.Builder(this).setTitle(R.string.notes_sort_title)
                .setSingleChoiceItems(R.array.notes_sort_options, mSortOrder,
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                if (mSortOrder != which) {
                                    mSortOrder = which;
                                    getPreferences(MODE_PRIVATE).edit()
                                            .putInt(SORT_PREFERENCE, which).apply();
                                    reloadNotes();
                                }
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(R.string.button_cancel, null).create();
        mSortDialog.show();
        Window window = mSortDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.sort_sheet_background);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData(), this, NoteEditor.class));
            return true;
        } else if (item.getItemId() == R.id.menu_sort) {
            showSortDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override protected void onListItemClick(ListView parent, View row, int position, long id) {
        if (mLoading) return;
        if (mSelecting) {
            toggleSelected(id);
            updateSelectionUi();
            return;
        }
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
            finish();
        } else {
            startActivity(new Intent(Intent.ACTION_EDIT, uri, this, NoteEditor.class));
        }
    }

    @Override public void onBackPressed() {
        if (mSelecting) exitSelection(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (mSortDialog != null) mSortDialog.dismiss();
        if (mDeleteDialog != null) mDeleteDialog.dismiss();
        super.onDestroy();
    }
}
