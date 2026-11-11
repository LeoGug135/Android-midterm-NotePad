package com.example.android.notepad;

import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import java.util.ArrayList;

/** Persisted metadata shared by the list, editor and category dialogs. */
final class NoteFeatures {
    static final String PINNED = "pinned", BACKGROUND = "background";
    static final String CATEGORY = "category_id", DELETED = "deleted_at";
    static final Uri CATEGORIES_URI = Uri.parse("content://" + NotePad.AUTHORITY + "/categories");
    static final Uri TRASH_URI = NotePad.Notes.CONTENT_URI.buildUpon()
            .appendQueryParameter("trash", "1").build();
    static final int[] COLORS = { 0xFFFFFFFF, 0xFFFFF6B8, 0xFFEAF4E7,
            0xFFE8F1FC, 0xFFFFEAF0, 0xFFF5EFE5 };
    static final String[] COLOR_NAMES = { "纯白", "浅黄", "浅绿", "浅蓝", "浅粉", "米色" };
    static final int[] FONT_SIZES = { 15, 17, 20, 23 };
    static final String[] FONT_NAMES = { "小", "标准", "大", "特大" };

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("note_appearance", Context.MODE_PRIVATE);
    }

    static int fontIndex(Context context) {
        return Math.max(0, Math.min(3, preferences(context).getInt("font_index", 1)));
    }

    static int color(int index) {
        return COLORS[Math.max(0, Math.min(COLORS.length - 1, index))];
    }

    static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable rounded(Context context, int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radius));
        return shape;
    }

    static Uri noteUri(long id) { return ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, id); }

    static final class Category {
        final long id;
        final String name;
        Category(long id, String name) { this.id = id; this.name = name; }
    }

    static ArrayList<Category> categories(Context context) {
        ArrayList<Category> result = new ArrayList<Category>();
        Cursor cursor = context.getContentResolver().query(CATEGORIES_URI,
                new String[] { "_id", "name" }, null, null, "_id ASC");
        try {
            if (cursor != null) while (cursor.moveToNext())
                result.add(new Category(cursor.getLong(0), cursor.getString(1)));
        } finally { if (cursor != null) cursor.close(); }
        return result;
    }

    static String categoryName(Context context, long id) {
        if (id == 0) return "未分类";
        for (Category category : categories(context)) if (category.id == id) return category.name;
        return "未分类";
    }
}
