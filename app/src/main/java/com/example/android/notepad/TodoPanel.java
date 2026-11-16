package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;

/** Task list with persistent completion state and explicit deletion confirmation. */
final class TodoPanel {
    private final Activity activity;
    private final TodoStore store;
    private final Runnable changed;
    private final BaseAdapter adapter;
    private ArrayList<TodoStore.Item> items;
    private AlertDialog dialog;

    TodoPanel(Activity owner, FrameLayout container, Runnable listener) {
        activity = owner; changed = listener; store = new TodoStore(owner);
        items = store.items();
        ListView list = new ListView(owner);
        list.setContentDescription("待办列表");
        list.setDivider(null); list.setClipToPadding(false);
        list.setPadding(0, dp(8), 0, dp(16));
        container.addView(list, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = new TextView(owner);
        empty.setText("暂无待办\n点击右上角“新建”添加待办");
        empty.setTextSize(16); empty.setTextColor(0xFF92969B);
        empty.setGravity(Gravity.CENTER); empty.setLineSpacing(dp(8), 1);
        container.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int position) { return items.get(position); }
            @Override public long getItemId(int position) { return items.get(position).id; }
            @Override public boolean hasStableIds() { return true; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                final TodoStore.Item item = items.get(position);
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(76)); row.setPadding(dp(20), dp(12), dp(24), dp(12));
                CheckBox check = new CheckBox(activity);
                check.setChecked(item.done); check.setFocusable(false);
                check.setContentDescription((item.done ? "标为未完成：" : "完成待办：") + item.text);
                check.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        store.setDone(item.id, ((CheckBox) view).isChecked()); refresh();
                    }
                });
                row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
                TextView text = new TextView(activity);
                text.setText(item.text); text.setTextSize(17);
                text.setTextColor(item.done ? 0xFF9B9EA2 : 0xFF25282C);
                if (item.done) text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                text.setPadding(dp(12), 0, 0, 0);
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
                row.setContentDescription(item.text + (item.done ? "，已完成" : "，未完成")
                        + "，点击编辑，长按删除");
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                edit(items.get(position));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final TodoStore.Item item = items.get(position);
                dismissDialog();
                dialog = new AlertDialog.Builder(activity).setTitle("删除待办？")
                        .setMessage("删除后无法恢复。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface value, int which) {
                                store.delete(item.id); refresh();
                            }
                        }).create();
                dialog.show(); return true;
            }
        });
    }

    int count() { return items.size(); }
    int remaining() {
        int count = 0; for (TodoStore.Item item : items) if (!item.done) count++;
        return count;
    }
    void refresh() { items = store.items(); adapter.notifyDataSetChanged(); changed.run(); }
    void add() { edit(null); }
    private void edit(final TodoStore.Item item) {
        dismissDialog();
        final EditText input = new EditText(activity);
        input.setContentDescription("待办内容"); input.setHint("输入待办内容");
        input.setTextSize(17); input.setMinLines(2); input.setMaxLines(6);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});
        if (item != null) { input.setText(item.text); input.setSelection(input.length()); }
        LinearLayout body = new LinearLayout(activity);
        body.setPadding(dp(24), dp(8), dp(24), dp(8));
        body.addView(input, new LinearLayout.LayoutParams(-1, -2));
        dialog = new AlertDialog.Builder(activity).setTitle(item == null ? "新建待办" : "编辑待办")
                .setView(body).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String text = input.getText().toString().trim();
                if (text.length() == 0) { input.setError("请输入待办内容"); return; }
                if (item == null) store.add(text); else store.setText(item.id, text);
                dialog.dismiss(); refresh();
            }
        });
        input.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    void dismissDialog() { if (dialog != null) { dialog.dismiss(); dialog = null; } }
    void destroy() { dismissDialog(); store.close(); }
    private int dp(int value) { return NoteFeatures.dp(activity, value); }
}
