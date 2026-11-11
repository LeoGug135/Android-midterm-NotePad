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
import android.content.ContentValues;
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
import android.view.MotionEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
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
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, NotePad.Notes.COLUMN_NAME_NOTE,
            "pinned", "background", "category_id", "deleted_at"
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
    private long mCategory = -1;
    private boolean mTrash;
    private long mOpenNote = -1;
    private float mTouchX, mTouchY;
    private boolean mHorizontalDrag, mVerticalDrag;
    private long mTouchNote = -1;
    private NoteDialogs mDialogs;
    private TextView mScopeTitle, mScopeCount;
    private Menu mMenu;
    private final HashMap<Long, String> mCategoryNames = new HashMap<Long, String>();


    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.notes_list);
        mDialogs = new NoteDialogs(this);
        if (getIntent().getData() == null) getIntent().setData(NotePad.Notes.CONTENT_URI);
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        mSortOrder = prefs.getInt(SORT_PREFERENCE, 0);
        if (mSortOrder < 0 || mSortOrder >= SORT_ORDERS.length) mSortOrder = 0;
        if (state != null) {
            mQuery = state.getString(STATE_QUERY, "");
            mCategory = state.getLong("category_filter", -1);
            mTrash = state.getBoolean("trash_filter");
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
                bindRow(row, (Cursor) getItem(position));
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
                    mOpenNote = -1;
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
        findViewById(R.id.move_selected).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                chooseCategory(new ArrayList<Long>(mSelectedIds), -1);
            }
        });
        findViewById(R.id.restore_selected).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { restore(new ArrayList<Long>(mSelectedIds)); }
        });
        createScopeHeader();
        ((NoteListView) getListView()).setSwipeTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    mTouchX = event.getX(); mTouchY = event.getY();
                    mHorizontalDrag = false; mVerticalDrag = false;
                    int position = getListView().pointToPosition((int) mTouchX, (int) mTouchY);
                    mTouchNote = position == ListView.INVALID_POSITION ? -1 : mAdapter.getItemId(position);
                    if (mOpenNote != -1 && mTouchNote != mOpenNote) closeActions();
                } else if (action == MotionEvent.ACTION_MOVE && !mSelecting && !mLoading && mTouchNote != -1) {
                    float dx = Math.abs(event.getX() - mTouchX), dy = Math.abs(event.getY() - mTouchY);
                    int slop = android.view.ViewConfiguration.get(NotesList.this).getScaledTouchSlop();
                    if (!mHorizontalDrag && dy > slop && dy > dx) mVerticalDrag = true;
                    if (!mVerticalDrag && !mHorizontalDrag && dx > slop * 2 && dx > dy * 1.5f) {
                        mHorizontalDrag = true;
                        // Cancel ListView's click and pending long press once the gesture becomes horizontal.
                        MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
                        getListView().onTouchEvent(cancel); cancel.recycle();
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (mHorizontalDrag) return true;
                } else if (action == MotionEvent.ACTION_UP && mHorizontalDrag) {
                    float dx = event.getX() - mTouchX;
                    if (Math.abs(dx) > NoteFeatures.dp(NotesList.this, 40)) {
                        mOpenNote = dx < 0 ? mTouchNote : -1;
                        mAdapter.notifyDataSetChanged();
                    }
                    mHorizontalDrag = false;
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    mHorizontalDrag = false;
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
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
        mEmptyView.setText(!TextUtils.isEmpty(mQuery) ? getString(R.string.notes_search_empty)
                : mTrash ? "最近删除为空" : mCategory >= 0 ? "此分类暂无笔记" : getString(R.string.notes_empty));
    }

    private void reloadNotes() {
        mOpenNote = -1;
        mLoading = true;
        getListView().setEnabled(false);
        // restartLoader closes its old Cursor; detach it before ListView can lay out again.
        mAdapter.swapCursor(null);
        updateSelectionUi();
        getLoaderManager().restartLoader(NOTES_LOADER, null, this);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(STATE_QUERY, mQuery);
        state.putLong("category_filter", mCategory);
        state.putBoolean("trash_filter", mTrash);
        state.putBoolean("selecting", mSelecting);
        long[] ids = new long[mSelectedIds.size()];
        int index = 0;
        for (long id : mSelectedIds) ids[index++] = id;
        state.putLongArray("selected_ids", ids);
        super.onSaveInstanceState(state);
    }

    @Override public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String selection = mTrash || mCategory < 0 ? "1" : "category_id=" + mCategory;
        String[] arguments = null;
        if (!TextUtils.isEmpty(mQuery)) {
            String escaped = mQuery.replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_");
            String pattern = "%" + escaped + "%";
            selection += " AND (title LIKE ? ESCAPE '\\' OR note LIKE ? ESCAPE '\\')";
            arguments = new String[] { pattern, pattern };
        }
        return new CursorLoader(this, mTrash ? NoteFeatures.TRASH_URI : getIntent().getData(), PROJECTION,
                selection, arguments, mTrash ? "deleted_at DESC, _id DESC" : "pinned DESC, " + SORT_ORDERS[mSortOrder]);
    }

    @Override public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mCategoryNames.clear();
        mCategoryNames.put(0L, "未分类");
        for (NoteFeatures.Category category : NoteFeatures.categories(this))
            mCategoryNames.put(category.id, category.name);
        if (mCategory > 0 && !mCategoryNames.containsKey(mCategory)) {
            mCategory = 0;
            reloadNotes();
            return;
        }
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
        mDeleteSelected.setText(mTrash ? "彻底删除（" + mSelectedIds.size() + "）"
                : getString(R.string.notes_delete_selected, mSelectedIds.size()));
        findViewById(R.id.move_selected).setVisibility(mTrash ? View.GONE : View.VISIBLE);
        findViewById(R.id.restore_selected).setVisibility(mTrash ? View.VISIBLE : View.GONE);
        findViewById(R.id.move_selected).setEnabled(!mLoading && !mSelectedIds.isEmpty());
        findViewById(R.id.restore_selected).setEnabled(!mLoading && !mSelectedIds.isEmpty());
        updateScopeHeader();
        mDeleteSelected.setEnabled(!mLoading && !mSelectedIds.isEmpty());
        ((TextView) findViewById(R.id.selection_scope)).setText(
                TextUtils.isEmpty(mQuery) ? R.string.notes_select_scope_all
                        : R.string.notes_select_scope_search);
        if (mTrash) ((TextView) findViewById(R.id.selection_scope)).setText("仅操作当前最近删除列表中的所选笔记");
        else if (mCategory >= 0) ((TextView) findViewById(R.id.selection_scope))
                .setText("全选仅包含当前分类及搜索结果");
        mAdapter.notifyDataSetChanged();
    }

    private void confirmDeleteSelected() {
        if (mSelectedIds.isEmpty() || mLoading) return;
        confirmDelete(new ArrayList<Long>(mSelectedIds));
    }


    @Override public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
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
        updateScopeHeader();
        return true;
    }

    private void showSortDialog() {
        closeActions();
        if (mTrash) {
            mDialogs.items("最近删除", new String[] { "清空最近删除" }, new NoteDialogs.Choice() {
                @Override public void select(int index) { clearTrash(); }
            });
            return;
        }
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
            if (!mTrash) startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData(), this,
                    NoteEditor.class).putExtra("category_id", Math.max(0, mCategory)));
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
        if (mOpenNote != -1) { closeActions(); return; }
        if (mTrash) {
            final ArrayList<Long> ids = one(id);
            mDialogs.items("最近删除", new String[] { "恢复笔记", "彻底删除" }, new NoteDialogs.Choice() {
                @Override public void select(int index) {
                    if (index == 0) restore(ids); else confirmDelete(ids);
                }
            });
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

    private void createScopeHeader() {
        if (getActionBar() == null) return;
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(NoteFeatures.dp(this, 8), 0, NoteFeatures.dp(this, 8), 0);
        mScopeTitle = new TextView(this); mScopeTitle.setTextSize(21);
        mScopeTitle.setSingleLine(true); mScopeTitle.setMaxWidth(NoteFeatures.dp(this, 190));
        mScopeTitle.setEllipsize(TextUtils.TruncateAt.END); mScopeTitle.setTextColor(0xFF202124); mScopeTitle.setTypeface(null, 1);
        mScopeCount = new TextView(this); mScopeCount.setTextSize(12); mScopeCount.setTextColor(0xFF85898E);
        header.addView(mScopeTitle); header.addView(mScopeCount);
        header.setContentDescription("切换分类与最近删除");
        header.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showScopes(); }
        });
        getActionBar().setDisplayShowTitleEnabled(false);
        getActionBar().setDisplayShowHomeEnabled(false);
        getActionBar().setDisplayShowCustomEnabled(true);
        getActionBar().setCustomView(header, new android.app.ActionBar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getActionBar().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFFFFFFFF));
    }

    private void updateScopeHeader() {
        if (mScopeTitle == null) return;
        String name = mTrash ? "最近删除" : mCategory < 0 ? "全部"
                : mCategoryNames.containsKey(mCategory) ? mCategoryNames.get(mCategory) : "未分类";
        mScopeTitle.setText(name + " ▾");
        mScopeCount.setText((TextUtils.isEmpty(mQuery) ? "" : "搜索结果 · ") + mAdapter.getCount() + " 篇笔记");
        TextView notice = (TextView) findViewById(R.id.trash_notice);
        notice.setVisibility(mTrash && !mSelecting ? View.VISIBLE : View.GONE);
        if (mMenu != null) {
            mMenu.findItem(R.id.menu_add).setVisible(!mTrash);
            View more = mMenu.findItem(R.id.menu_sort).getActionView();
            if (more != null) more.setContentDescription(mTrash ? "最近删除操作" : getString(R.string.notes_sort_title));
        }
    }

    private void showScopes() {
        closeActions();
        final ArrayList<NoteFeatures.Category> categories = NoteFeatures.categories(this);
        String[] names = new String[categories.size() + 4];
        names[0] = "全部"; names[1] = "未分类";
        for (int i = 0; i < categories.size(); i++) names[i + 2] = categories.get(i).name;
        names[names.length - 2] = "最近删除"; names[names.length - 1] = "管理分类";
        mDialogs.items("笔记分类", names, new NoteDialogs.Choice() {
            @Override public void select(int index) {
                if (index == categories.size() + 3) mDialogs.manageCategories(new Runnable() {
                    @Override public void run() { reloadNotes(); }
                });
                else if (index == categories.size() + 2) changeScope(-1, true);
                else changeScope(index == 0 ? -1 : index == 1 ? 0 : categories.get(index - 2).id, false);
            }
        });
    }

    private void changeScope(long category, boolean trash) {
        exitSelection(); mOpenNote = -1;
        mCategory = category; mTrash = trash;
        mQuery = ""; mSearchView.setQuery("", false); mSearchView.clearFocus();
        updateEmptyMessage(); reloadNotes();
    }

    private static ArrayList<Long> one(long id) {
        ArrayList<Long> ids = new ArrayList<Long>(); ids.add(id); return ids;
    }

    private static String idsWhere(ArrayList<Long> ids) {
        StringBuilder where = new StringBuilder("_id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) where.append(',');
            where.append(ids.get(i).longValue());
        }
        return where.append(')').toString();
    }

    private void closeActions() {
        if (mOpenNote != -1) { mOpenNote = -1; mAdapter.notifyDataSetChanged(); }
    }

    private void bindRow(final View row, Cursor cursor) {
        final long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
        final boolean pinned = cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) != 0;
        final long category = cursor.getLong(cursor.getColumnIndexOrThrow("category_id"));
        boolean selected = mSelectedIds.contains(id);
        CheckBox box = (CheckBox) row.findViewById(R.id.note_selected);
        box.setVisibility(mSelecting ? View.VISIBLE : View.GONE); box.setChecked(selected);
        View card = row.findViewById(R.id.note_card);
        int listColor = pinned && !mTrash ? 0xFFFFF6B8 : Color.WHITE;
        card.setBackgroundDrawable(NoteFeatures.rounded(this, selected ? 0xFFE5EFFF
                : listColor, 18));
        String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("note"));
        body = body == null ? "" : body.trim();
        String summary = body.replace('\n', ' ').replace('\r', ' ');
        if (TextUtils.isEmpty(title) || NoteEditor.isLegacyTitle(title)) {
            int newline = body.indexOf('\n');
            summary = newline >= 0 ? body.substring(newline + 1).trim().replace('\n', ' ') : "";
        }
        TextView preview = (TextView) row.findViewById(R.id.note_preview);
        preview.setText(summary); preview.setVisibility(TextUtils.isEmpty(summary) ? View.GONE : View.VISIBLE);
        TextView status = (TextView) row.findViewById(R.id.note_status);
        String categoryName = mCategoryNames.get(category);
        status.setText((pinned && !mTrash ? "↑ 置顶  ·  " : "")
                + (categoryName == null ? "未分类" : categoryName));
        if (mTrash) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            ((TextView) row.findViewById(android.R.id.text2)).setText("删除于："
                    + format.format(new Date(cursor.getLong(cursor.getColumnIndexOrThrow("deleted_at")))));
        }
        LinearLayout actions = (LinearLayout) row.findViewById(R.id.note_actions);
        actions.removeAllViews();
        boolean actionsOpen = !mSelecting && mOpenNote == id;
        actions.setVisibility(actionsOpen ? View.VISIBLE : View.GONE);
        actions.setBackgroundDrawable(NoteFeatures.rounded(this, listColor, 18));
        // INVISIBLE preserves the card's measured height while the buttons cover its content.
        card.setVisibility(actionsOpen ? View.INVISIBLE : View.VISIBLE);
        if (mTrash) {
            addAction(actions, "restore", "恢复", new Runnable() {
                @Override public void run() { restore(one(id)); }
            });
            addAction(actions, "delete", "彻底删除", new Runnable() {
                @Override public void run() { confirmDelete(one(id)); }
            });
        } else {
            addAction(actions, pinned ? "unpin" : "pin", pinned ? "取消置顶" : "置顶", new Runnable() {
                @Override public void run() {
                    ContentValues values = new ContentValues(); values.put("pinned", pinned ? 0 : 1);
                    updateNotes(one(id), values, false);
                }
            });
            addAction(actions, "tag", "标签", new Runnable() {
                @Override public void run() { chooseCategory(one(id), category); }
            });
            addAction(actions, "delete", "删除", new Runnable() {
                @Override public void run() { confirmDelete(one(id)); }
            });
            addAction(actions, "more", "更多", new Runnable() {
                @Override public void run() {
                    mDialogs.items("更多操作", new String[] { "移动" }, new NoteDialogs.Choice() {
                        @Override public void select(int index) { chooseCategory(one(id), category); }
                    });
                }
            });
        }
        row.setContentDescription(((TextView) row.findViewById(android.R.id.text1)).getText()
                + "，" + status.getText() + "，向左滑动显示操作");
    }

    private void addAction(LinearLayout parent, String icon, String label, final Runnable action) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL); button.setGravity(Gravity.CENTER);
        button.setPadding(0, NoteFeatures.dp(this, 12), 0, NoteFeatures.dp(this, 10));
        button.setContentDescription(label); button.setClickable(true); button.setFocusable(false);
        NoteIconView image = new NoteIconView(this); image.setKind(icon);
        image.setBackgroundDrawable(NoteFeatures.rounded(this, 0xFFF0F0F0, 30));
        button.addView(image, new LinearLayout.LayoutParams(NoteFeatures.dp(this, 48), NoteFeatures.dp(this, 48)));
        TextView text = new TextView(this); text.setText(label); text.setTextSize(12);
        text.setTextColor(0xFF363A40); text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams caption = new LinearLayout.LayoutParams(-1, -2);
        caption.topMargin = NoteFeatures.dp(this, 6); button.addView(text, caption);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { if (!mLoading) action.run(); }
        });
        parent.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void chooseCategory(final ArrayList<Long> ids, long current) {
        if (ids.isEmpty() || mLoading) return;
        mDialogs.categoryPicker(current, new NoteDialogs.CategoryChoice() {
            @Override public void select(long category) {
                ContentValues values = new ContentValues(); values.put("category_id", category);
                updateNotes(ids, values, false);
            }
        });
    }

    private void updateNotes(ArrayList<Long> ids, ContentValues values, boolean trash) {
        if (ids.isEmpty()) return;
        try {
            int count = getContentResolver().update(NotePad.Notes.CONTENT_URI, values,
                    idsWhere(ids) + (trash ? " AND deleted_at>0" : " AND deleted_at=0"), null);
            closeActions(); exitSelection();
            Toast.makeText(this, "已更新 " + count + " 篇笔记", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            Toast.makeText(this, "操作失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private void restore(ArrayList<Long> ids) {
        ContentValues values = new ContentValues(); values.put("deleted_at", 0);
        updateNotes(ids, values, true);
    }

    private void confirmDelete(final ArrayList<Long> ids) {
        if (ids.isEmpty()) return;
        final boolean permanent = mTrash;
        mDeleteDialog = new AlertDialog.Builder(this)
                .setTitle(permanent ? "彻底删除笔记？" : "移入最近删除？")
                .setMessage(permanent ? "将永久删除这 " + ids.size() + " 篇笔记，无法恢复。"
                        : "将这 " + ids.size() + " 篇笔记移入最近删除，之后可以恢复。")
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(permanent ? "彻底删除" : "删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (permanent) {
                            try {
                                int count = getContentResolver().delete(NotePad.Notes.CONTENT_URI,
                                        idsWhere(ids) + " AND deleted_at>0", null);
                                closeActions(); exitSelection();
                                Toast.makeText(NotesList.this, "已彻底删除 " + count + " 篇笔记", 0).show();
                            } catch (RuntimeException error) {
                                Toast.makeText(NotesList.this, "删除失败，请重试", 0).show();
                            }
                        } else {
                            ContentValues values = new ContentValues();
                            values.put("deleted_at", System.currentTimeMillis());
                            updateNotes(ids, values, false);
                        }
                    }
                }).create();
        mDialogs.show(mDeleteDialog);
    }

    private void clearTrash() {
        ArrayList<Long> ids = new ArrayList<Long>();
        Cursor cursor = getContentResolver().query(NoteFeatures.TRASH_URI,
                new String[] { "_id" }, null, null, null);
        try { if (cursor != null) while (cursor.moveToNext()) ids.add(cursor.getLong(0)); }
        finally { if (cursor != null) cursor.close(); }
        if (ids.isEmpty()) Toast.makeText(this, "最近删除为空", 0).show();
        else confirmDelete(ids);
    }


    @Override public void onBackPressed() {
        if (mSelecting) exitSelection();
        else if (mOpenNote != -1) closeActions();
        else if (mTrash || mCategory != -1) changeScope(-1, false);
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (mDialogs != null) mDialogs.destroy();
        if (mSortDialog != null) mSortDialog.dismiss();
        if (mDeleteDialog != null) mDeleteDialog.dismiss();
        super.onDestroy();
    }
}
