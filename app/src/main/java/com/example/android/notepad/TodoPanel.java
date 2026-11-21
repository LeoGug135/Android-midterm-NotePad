package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/** Rounded task cards, persistent colors, and actions revealed within the row. */
final class TodoPanel {
    private static final int[] COLORS = {0xFFFFFFFF, 0xFFFFF1A8, 0xFFDDF5E8, 0xFFD9EDFE, 0xFFFFE2E7, 0xFFFFE6C3};
    static int taskColor(int index) { return COLORS[Math.max(0, Math.min(COLORS.length - 1, index))]; }
    static final int DONE_COLOR = 0xFFF3F3F3;
    private final Activity activity;
    private final TodoStore store;
    private final Runnable changed;
    private final BaseAdapter adapter;
    private final NoteListView list;
    private ArrayList<TodoStore.Item> items;
    private Dialog dialog;
    private long openId = -1, touchId = -1;
    private float touchX, touchY;
    private boolean horizontal, vertical;

    TodoPanel(Activity owner, FrameLayout container, Runnable listener) {
        activity = owner; changed = listener; store = new TodoStore(owner); items = store.items();
        list = new NoteListView(owner, null); list.setTag("todo_list");
        list.setContentDescription("待办列表"); list.setDivider(null); list.setClipToPadding(false);
        list.setPadding(dp(24), dp(18), dp(24), dp(96));
        container.addView(list, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = new TextView(owner); empty.setText("暂无待办\n点击右下角“＋”添加待办");
        empty.setTextSize(16); empty.setTextColor(0xFF92969B);
        empty.setGravity(Gravity.CENTER); empty.setLineSpacing(dp(8), 1);
        container.addView(empty, new FrameLayout.LayoutParams(-1, -1)); list.setEmptyView(empty);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int position) { return items.get(position); }
            @Override public long getItemId(int position) { return items.get(position).id; }
            @Override public boolean hasStableIds() { return true; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                return taskRow(items.get(position), position > 0 && items.get(position).done && !items.get(position - 1).done);
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!closeActions()) edit(items.get(position));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDelete(items.get(position)); return true;
            }
        });
        list.setSwipeTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    touchX = event.getX(); touchY = event.getY(); horizontal = false; vertical = false;
                    int position = list.pointToPosition((int) touchX, (int) touchY);
                    touchId = position == AdapterView.INVALID_POSITION ? -1 : items.get(position).id;
                    if (openId != -1 && touchId != openId) closeActions();
                } else if (action == MotionEvent.ACTION_MOVE && touchId != -1) {
                    float dx = Math.abs(event.getX() - touchX), dy = Math.abs(event.getY() - touchY);
                    int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
                    if (!horizontal && dy > slop && dy > dx) vertical = true;
                    if (!vertical && dx > slop * 2 && dx > dy * 1.5f) horizontal = true;
                    if (horizontal) { view.getParent().requestDisallowInterceptTouchEvent(true); return true; }
                } else if (action == MotionEvent.ACTION_UP && horizontal) {
                    if (Math.abs(event.getX() - touchX) > dp(40)) {
                        openId = openId == touchId ? -1 : touchId; adapter.notifyDataSetChanged();
                    }
                    horizontal = false; view.getParent().requestDisallowInterceptTouchEvent(false); return true;
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    horizontal = false; view.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
        });
        TextView add = new TextView(owner); add.setText("＋"); add.setTextColor(0xFFFFFFFF);
        add.setTextSize(38); add.setGravity(Gravity.CENTER); add.setContentDescription("新建待办");
        add.setBackgroundDrawable(NoteFeatures.rounded(owner, 0xFFFFC900, 40));
        if (android.os.Build.VERSION.SDK_INT >= 21) add.setElevation(dp(6));
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM | Gravity.RIGHT);
        addParams.rightMargin = dp(26); addParams.bottomMargin = dp(24); container.addView(add, addParams);
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { add(); } });
    }

    private View taskRow(final TodoStore.Item item, boolean divider) {
        LinearLayout outer = new LinearLayout(activity); outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(0, 0, 0, dp(12)); outer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (divider) {
            View line = new View(activity); line.setBackgroundColor(0xFFE9E9E9);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
            params.topMargin = dp(14); params.bottomMargin = dp(26); outer.addView(line, params);
        }
        final int color = item.done ? DONE_COLOR : taskColor(item.color);
        FrameLayout frame = new FrameLayout(activity); frame.setTag("todo_card");
        frame.setBackgroundDrawable(NoteFeatures.rounded(activity, color, 18)); frame.setClipChildren(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) frame.setClipToOutline(true);
        outer.addView(frame, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(activity); actions.setTag("todo_actions");
        actions.setGravity(Gravity.CENTER); actions.setBackgroundDrawable(NoteFeatures.rounded(activity, darker(color), 18));
        int actionWidth = dp(item.done ? 64 : 128);
        frame.addView(actions, new FrameLayout.LayoutParams(actionWidth + dp(18), -1, Gravity.RIGHT));
        actions.setPadding(dp(18), 0, 0, 0);
        if (!item.done) addAction(actions, "color", "颜色", new Runnable() { @Override public void run() { chooseColor(item); } });
        addAction(actions, "delete", "删除", new Runnable() { @Override public void run() { confirmDelete(item); } });
        actions.setVisibility(openId == item.id ? View.VISIBLE : View.INVISIBLE);
        LinearLayout card = new LinearLayout(activity); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(76)); card.setPadding(dp(12), dp(12), dp(16), dp(12));
        card.setBackgroundDrawable(NoteFeatures.rounded(activity, color, 18));
        card.setTranslationX(openId == item.id ? -actionWidth : 0);
        frame.addView(card, new FrameLayout.LayoutParams(-1, -2));
        CircleCheck check = new CircleCheck(activity, item.done);
        check.setContentDescription((item.done ? "标为未完成：" : "完成待办：") + item.text);
        check.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { store.setDone(item.id, !item.done); refresh(); }
        });
        card.addView(check, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView text = new TextView(activity); text.setText(item.text); text.setTextSize(18);
        text.setTextColor(item.done ? 0xFFA9ABAE : 0xFF242629); text.setPadding(dp(6), 0, 0, 0);
        card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        outer.setContentDescription(item.text + (item.done ? "，已完成" : "，未完成") + "，点击编辑，长按删除");
        return outer;
    }

    private int darker(int color) {
        return android.graphics.Color.rgb((int) (android.graphics.Color.red(color) * .88f),
                (int) (android.graphics.Color.green(color) * .88f), (int) (android.graphics.Color.blue(color) * .88f));
    }
    private void addAction(LinearLayout parent, String icon, String label, final Runnable run) {
        LinearLayout button = new LinearLayout(activity); button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER); button.setContentDescription(label); button.setClickable(true);
        NoteIconView image = new NoteIconView(activity); image.setKind(icon); image.setTint(0xFFFFFFFF);
        button.addView(image, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView text = new TextView(activity); text.setText(label); text.setTextSize(11);
        text.setTextColor(0xFFFFFFFF); text.setGravity(Gravity.CENTER); button.addView(text, new LinearLayout.LayoutParams(-1, -2));
        button.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { run.run(); } });
        parent.addView(button, new LinearLayout.LayoutParams(dp(64), -1));
    }
    private void confirmDelete(final TodoStore.Item item) {
        dismissDialog();
        dialog = new AlertDialog.Builder(activity).setTitle("删除待办？").setMessage("删除后无法恢复。")
                .setNegativeButton("取消", null).setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface value, int which) { store.delete(item.id); refresh(); }
                }).create(); dialog.show();
    }

    private interface ColorChoice { void select(int color); }
    private LinearLayout palette(final int[] selected, final ColorChoice choice) {
        final LinearLayout colors = new LinearLayout(activity); colors.setGravity(Gravity.CENTER);
        colors.setPadding(dp(8), dp(12), dp(8), dp(12));
        colors.setBackgroundDrawable(NoteFeatures.rounded(activity, 0xFFF7F7F7, 20));
        for (int i = 0; i < NoteFeatures.COLORS.length; i++) {
            final int index = i;
            TextView swatch = new TextView(activity); swatch.setGravity(Gravity.CENTER);
            swatch.setTextColor(0xFF343638); swatch.setTextSize(21); swatch.setText(i == selected[0] ? "✓" : "");
            swatch.setContentDescription("待办颜色：" + NoteFeatures.COLOR_NAMES[i]); swatch.setSelected(i == selected[0]);
            GradientDrawable shape = NoteFeatures.rounded(activity, taskColor(i), 30);
            shape.setStroke(dp(1), i == 0 ? 0xFFDDDDDD : taskColor(i)); android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
            states.addState(new int[] {android.R.attr.state_focused}, shape);
            states.addState(new int[] {}, shape); swatch.setBackgroundDrawable(states);
            FrameLayout cell = new FrameLayout(activity);
            cell.addView(swatch, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
            colors.addView(cell, new LinearLayout.LayoutParams(0, dp(46), 1));
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    selected[0] = index;
                    for (int n = 0; n < colors.getChildCount(); n++) {
                        TextView value = (TextView) ((FrameLayout) colors.getChildAt(n)).getChildAt(0); value.setText(n == index ? "✓" : ""); value.setSelected(n == index);
                    }
                    choice.select(index);
                }
            });
        }
        return colors;
    }
    private void chooseColor(final TodoStore.Item item) {
        if (item.done) return;
        dismissDialog(); final int[] selected = {item.color};
        LinearLayout palette = palette(selected, new ColorChoice() {
            @Override public void select(int color) { store.setColor(item.id, color); dialog.dismiss(); refresh(); }
        });
        dialog = new AlertDialog.Builder(activity).setTitle("待办颜色").setView(palette).setNegativeButton("取消", null).create();
        dialog.show(); dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.getWindow().setBackgroundDrawable(NoteFeatures.rounded(activity, 0xFFFFFFFF, 22)); dialog.getWindow().setLayout(-1, -2);
    }

    int count() { return items.size(); }
    int remaining() { int count = 0; for (TodoStore.Item item : items) if (!item.done) count++; return count; }
    boolean closeActions() { if (openId == -1) return false; openId = -1; adapter.notifyDataSetChanged(); return true; }
    void refresh() { openId = -1; items = store.items(); adapter.notifyDataSetChanged(); changed.run(); }
    void add() { closeActions(); edit(null); }
    private void edit(final TodoStore.Item item) {
        dismissDialog();
        final int[] selected = {item == null ? NoteFeatures.preferences(activity).getInt("todo_color", 2) : item.color};
        if (selected[0] < 0 || selected[0] >= NoteFeatures.COLORS.length) selected[0] = 2;
        final Dialog editor = new Dialog(activity, R.style.EditorTheme); dialog = editor;
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(0xFFFFFFFF); body.setPadding(dp(24), dp(12), dp(24), dp(20));
        LinearLayout toolbar = new LinearLayout(activity); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(activity, null, android.R.attr.borderlessButtonStyle); back.setText("返回"); back.setMinWidth(0); back.setTextColor(0xFF55595D); back.setContentDescription("返回待办列表");
        toolbar.addView(back, new LinearLayout.LayoutParams(-2, dp(48)));
        TextView title = new TextView(activity); title.setText(item == null ? "新建待办" : "编辑待办");
        title.setTextSize(23); title.setTextColor(0xFF27292C); title.setTypeface(null, 1); title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button save = new Button(activity, null, android.R.attr.borderlessButtonStyle); save.setText("保存"); save.setMinWidth(0); save.setTextColor(0xFFBFA000); toolbar.addView(save, new LinearLayout.LayoutParams(-2, dp(48)));
        body.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        final LinearLayout card = new LinearLayout(activity); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(18), dp(18), dp(18));
        card.setBackgroundDrawable(NoteFeatures.rounded(activity, item != null && item.done ? DONE_COLOR : taskColor(selected[0]), 18));
        card.addView(new CircleCheck(activity, item != null && item.done), new LinearLayout.LayoutParams(dp(40), dp(44)));
        final EditText input = new EditText(activity); input.setContentDescription("待办内容"); input.setHint("请输入待办");
        input.setTextSize(19); input.setTextColor(0xFF303437); input.setHintTextColor(0xFF9DB4A7);
        input.setBackgroundColor(0x00000000); input.setMinLines(1); input.setMaxLines(8);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});
        if (item != null) { input.setText(item.text); input.setSelection(input.length()); }
        card.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2); cardParams.topMargin = dp(28); body.addView(card, cardParams);
        body.addView(new View(activity), new LinearLayout.LayoutParams(-1, 0, 1));
        if (item == null || !item.done) body.addView(palette(selected, new ColorChoice() {
            @Override public void select(int color) { card.setBackgroundDrawable(NoteFeatures.rounded(activity, taskColor(color), 18)); }
        }), new LinearLayout.LayoutParams(-1, -2));
        editor.setContentView(body);
        editor.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        // Some new Android versions overlay the IME on a legacy full-screen Dialog.
        // Keep the palette inside the visible area even when adjustResize is ignored.
        final LinearLayout editorBody = body;
        body.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                android.graphics.Rect visible = new android.graphics.Rect();
                editorBody.getWindowVisibleDisplayFrame(visible);
                int[] location = new int[2]; editorBody.getLocationOnScreen(location);
                int overlap = Math.max(0, location[1] + editorBody.getHeight() - visible.bottom);
                int bottom = dp(20) + (overlap > dp(120) ? overlap : 0);
                if (editorBody.getPaddingBottom() != bottom)
                    editorBody.setPadding(dp(24), dp(12), dp(24), bottom);
            }
        });
        editor.show(); editor.getWindow().setLayout(-1, -1);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { editor.dismiss(); } });
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String text = input.getText().toString().trim(); if (text.length() == 0) { input.setError("请输入待办内容"); return; }
                if (item == null) store.add(text, selected[0]);
                else { store.setText(item.id, text); if (!item.done) store.setColor(item.id, selected[0]); }
                NoteFeatures.preferences(activity).edit().putInt("todo_color", selected[0]).apply(); editor.dismiss(); refresh();
            }
        }); input.requestFocus();
    }
    void dismissDialog() { if (dialog != null) { dialog.dismiss(); dialog = null; } }
    void destroy() { dismissDialog(); store.close(); }
    private int dp(int value) { return NoteFeatures.dp(activity, value); }

    private static final class CircleCheck extends View {
        private final boolean checked;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        CircleCheck(android.content.Context context, boolean value) { super(context); checked = value; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = NoteFeatures.dp(getContext(), 9);
            paint.setColor(checked ? 0xFFB3B5B7 : 0xFF373A3D); paint.setStrokeWidth(NoteFeatures.dp(getContext(), 2));
            paint.setStyle(checked ? Paint.Style.FILL : Paint.Style.STROKE); canvas.drawCircle(cx, cy, radius, paint);
            if (checked) {
                paint.setColor(0xFFFFFFFF); paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
                Path path = new Path(); path.moveTo(cx - radius * .45f, cy); path.lineTo(cx - radius * .1f, cy + radius * .35f);
                path.lineTo(cx + radius * .5f, cy - radius * .35f); canvas.drawPath(path, paint);
            }
        }
    }
}
