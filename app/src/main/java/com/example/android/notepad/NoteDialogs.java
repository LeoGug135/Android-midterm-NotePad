package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.graphics.Rect;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.PopupWindow;
import java.util.ArrayList;

/** Small, lifecycle-owned sheets; category changes use the provider's transaction. */
final class NoteDialogs {
    interface Choice { void select(int index); }
    interface CategoryChoice { void select(long id); }
    private final Activity activity;
    private PopupWindow morePopup;
    private final ArrayList<Dialog> dialogs = new ArrayList<Dialog>();

    NoteDialogs(Activity activity) { this.activity = activity; }

    void show(AlertDialog dialog) {
        show(dialog, false);
    }

    private void show(AlertDialog dialog, boolean centered) {
        dialogs.add(dialog);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface value) { dialogs.remove(value); }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            if (centered) {
                window.setBackgroundDrawable(NoteFeatures.rounded(activity, 0xFFFFFFFF, 22));
                window.setGravity(Gravity.CENTER);
                int width = Math.min(NoteFeatures.dp(activity, 360),
                        activity.getResources().getDisplayMetrics().widthPixels - NoteFeatures.dp(activity, 48));
                window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            } else {
                window.setBackgroundDrawableResource(R.drawable.sort_sheet_background);
                window.setGravity(Gravity.BOTTOM);
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }
    }

    void items(String title, String[] names, final Choice callback) {
        show(new AlertDialog.Builder(activity).setTitle(title)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { callback.select(which); }
                }).setNegativeButton("取消", null).create());
    }

    void dismissMoreMenu() {
        if (morePopup != null) morePopup.dismiss();
    }

    void moreMenu(View anchor, final Choice callback) {
        dismissMoreMenu();
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        Rect frame = new Rect(); anchor.getWindowVisibleDisplayFrame(frame);
        int margin = NoteFeatures.dp(activity, 16);
        int width = Math.min(NoteFeatures.dp(activity, 196), frame.width() - margin * 2);
        final PopupWindow popup = new PopupWindow(content, width,
                WindowManager.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(NoteFeatures.rounded(activity, 0xFFFFFFFF, 22));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        if (Build.VERSION.SDK_INT >= 21) popup.setElevation(NoteFeatures.dp(activity, 14));
        String[] labels = { "移动", activity.getString(R.string.note_importance_title) };
        String[] icons = { "move", "importance" };
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.HORIZONTAL); item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(NoteFeatures.dp(activity, 24), 0, NoteFeatures.dp(activity, 20), 0);
            item.setContentDescription(labels[i]); item.setFocusable(true); item.setClickable(true);
            StateListDrawable background = new StateListDrawable();
            android.graphics.drawable.GradientDrawable pressed = NoteFeatures.rounded(activity, 0xFFF1F2F4, 22);
            float radius = NoteFeatures.dp(activity, 22);
            pressed.setCornerRadii(i == 0 ? new float[] {radius,radius,radius,radius,0,0,0,0}
                    : new float[] {0,0,0,0,radius,radius,radius,radius});
            background.addState(new int[] { android.R.attr.state_pressed }, pressed);
            background.addState(new int[] { android.R.attr.state_focused }, pressed);
            background.addState(new int[] {}, NoteFeatures.rounded(activity, 0x00000000, 0));
            item.setBackgroundDrawable(background);
            NoteIconView icon = new NoteIconView(activity); icon.setKind(icons[i]);
            if (Build.VERSION.SDK_INT >= 16) icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(icon, new LinearLayout.LayoutParams(NoteFeatures.dp(activity, 30), NoteFeatures.dp(activity, 30)));
            TextView label = new TextView(activity); label.setText(labels[i]);
            label.setTextSize(18); label.setTextColor(0xFF292C2E); label.setSingleLine(true);
            if (Build.VERSION.SDK_INT >= 16) label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams text = new LinearLayout.LayoutParams(-2, -2);
            text.leftMargin = NoteFeatures.dp(activity, 16); item.addView(label, text);
            content.addView(item, new LinearLayout.LayoutParams(-1, NoteFeatures.dp(activity, 66)));
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { popup.dismiss(); callback.select(which); }
            });
            if (i == 0) {
                View divider = new View(activity); divider.setBackgroundColor(0xFFE7E8EA);
                content.addView(divider, new LinearLayout.LayoutParams(-1, NoteFeatures.dp(activity, 0.5f)));
            }
        }
        morePopup = popup;
        popup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override public void onDismiss() { if (morePopup == popup) morePopup = null; }
        });
        int[] location = new int[2]; anchor.getLocationOnScreen(location);
        int left = Math.max(frame.left + margin,
                Math.min(location[0] + anchor.getWidth() - width, frame.right - width - margin));
        // Drop-down positioning follows the button and flips above it when space below is tight.
        popup.showAsDropDown(anchor, left - location[0], NoteFeatures.dp(activity, 8));
    }

    void importancePicker(int current, final Choice callback) {
        show(new AlertDialog.Builder(activity).setTitle(R.string.note_importance_title)
                .setSingleChoiceItems(R.array.note_importance_levels, current,
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                callback.select(which);
                            }
                        }).setNegativeButton(R.string.button_cancel, null).create());
    }

    void categoryPicker(long current, final CategoryChoice callback) {
        final ArrayList<NoteFeatures.Category> categories = NoteFeatures.categories(activity);
        String[] names = new String[categories.size() + 2];
        names[0] = "未分类";
        int checked = current == 0 ? 0 : -1;
        for (int i = 0; i < categories.size(); i++) {
            names[i + 1] = categories.get(i).name;
            if (categories.get(i).id == current) checked = i + 1;
        }
        names[names.length - 1] = "＋ 新建分类";
        show(new AlertDialog.Builder(activity).setTitle("选择分类")
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (which == categories.size() + 1) editCategory(null, callback);
                        else callback.select(which == 0 ? 0 : categories.get(which - 1).id);
                    }
                }).setNegativeButton("取消", null).create());
    }

    void editCategory(final NoteFeatures.Category category, final CategoryChoice callback) {
        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("请输入分类名称");
        input.setTextSize(16);
        input.setTextColor(0xFF25282D);
        input.setHintTextColor(0xFF999DA3);
        input.setPadding(NoteFeatures.dp(activity, 14), NoteFeatures.dp(activity, 12),
                NoteFeatures.dp(activity, 14), NoteFeatures.dp(activity, 12));
        input.setBackgroundDrawable(NoteFeatures.rounded(activity, 0xFFF5F6F8, 12));
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(20) });
        if (category != null) { input.setText(category.name); input.selectAll(); }
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int inset = NoteFeatures.dp(activity, 24);
        content.setPadding(inset, 0, inset, NoteFeatures.dp(activity, 4));
        content.addView(input, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = new TextView(activity);
        hint.setText("最多 20 个字符"); hint.setTextSize(12); hint.setTextColor(0xFF8A8F96);
        hint.setPadding(NoteFeatures.dp(activity, 2), NoteFeatures.dp(activity, 9), 0, 0);
        content.addView(hint);
        TextView title = new TextView(activity);
        title.setText(category == null ? "新建分类" : "重命名分类");
        title.setTextSize(20); title.setTypeface(null, 1); title.setTextColor(0xFF25282D);
        title.setPadding(inset, NoteFeatures.dp(activity, 22), inset, NoteFeatures.dp(activity, 18));
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(title).setView(content)
                .setNegativeButton("取消", null).setPositiveButton("确定", null).create();
        show(dialog, true);
        input.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String name = input.getText().toString().trim();
                if (name.length() == 0 || "未分类".equals(name) || "全部".equals(name)
                        || "最近删除".equals(name)) { input.setError("请使用其他分类名称"); return; }
                for (NoteFeatures.Category value : NoteFeatures.categories(activity)) {
                    if (name.equals(value.name) && (category == null || category.id != value.id)) {
                        input.setError("该分类已存在"); return;
                    }
                }
                try {
                    ContentValues values = new ContentValues(); values.put("name", name);
                    long id;
                    if (category == null) id = ContentUris.parseId(activity.getContentResolver()
                            .insert(NoteFeatures.CATEGORIES_URI, values));
                    else {
                        id = category.id;
                        if (activity.getContentResolver().update(ContentUris.withAppendedId(
                                NoteFeatures.CATEGORIES_URI, id), values, null, null) == 0) {
                            input.setError("分类已不存在，请重新选择"); return;
                        }
                    }
                    dialog.dismiss();
                    callback.select(id);
                } catch (RuntimeException error) { input.setError("保存失败，请检查名称后重试"); }
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView view, int action, android.view.KeyEvent event) {
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    return true;
                }
                return false;
            }
        });
    }

    void manageCategories(final Runnable changed) {
        final ArrayList<NoteFeatures.Category> categories = NoteFeatures.categories(activity);
        String[] names = new String[categories.size() + 1];
        for (int i = 0; i < categories.size(); i++) names[i] = categories.get(i).name;
        names[categories.size()] = "＋ 新建分类";
        items("管理分类", names, new Choice() {
            @Override public void select(int index) {
                if (index == categories.size()) {
                    editCategory(null, new CategoryChoice() {
                        @Override public void select(long id) { changed.run(); }
                    });
                    return;
                }
                final NoteFeatures.Category category = categories.get(index);
                items(category.name, new String[] { "重命名", "删除分类" }, new Choice() {
                    @Override public void select(int action) {
                        if (action == 0) editCategory(category, new CategoryChoice() {
                            @Override public void select(long id) { changed.run(); }
                        });
                        else show(new AlertDialog.Builder(activity).setTitle("删除分类？")
                                .setMessage("其中的笔记将归入“未分类”，笔记内容会保留。")
                                .setNegativeButton("取消", null).setPositiveButton("删除分类",
                                        new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            activity.getContentResolver().delete(ContentUris.withAppendedId(
                                                    NoteFeatures.CATEGORIES_URI, category.id), null, null);
                                            changed.run();
                                        } catch (RuntimeException error) {
                                            android.widget.Toast.makeText(activity, "删除分类失败，请重试", 0).show();
                                        }
                                    }
                                }).create());
                    }
                });
            }
        });
    }

    void destroy() {
        dismissMoreMenu();
        for (Dialog dialog : new ArrayList<Dialog>(dialogs)) dialog.dismiss();
        dialogs.clear();
    }
}
