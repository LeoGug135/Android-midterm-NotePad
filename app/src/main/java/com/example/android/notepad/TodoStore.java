package com.example.android.notepad;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;

/** Independent storage keeps tasks separate from notes and their recycle bin. */
final class TodoStore extends SQLiteOpenHelper {
    static final class Item {
        long id;
        String text;
        boolean done;
        int color;
    }

    TodoStore(Context context) { super(context, "todos.db", null, 2); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE todos (_id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, "
                + "done INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, color INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE todos ADD COLUMN color INTEGER NOT NULL DEFAULT 0");
    }

    ArrayList<Item> items() {
        ArrayList<Item> result = new ArrayList<Item>();
        Cursor cursor = getReadableDatabase().query("todos", null, null, null, null, null,
                "done ASC, created DESC, _id DESC");
        try {
            while (cursor.moveToNext()) {
                Item item = new Item();
                item.id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                item.text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                item.done = cursor.getInt(cursor.getColumnIndexOrThrow("done")) != 0;
                item.color = cursor.getInt(cursor.getColumnIndexOrThrow("color"));
                result.add(item);
            }
        } finally { cursor.close(); }
        return result;
    }
    long add(String text) { return add(text, 0); }
    long add(String text, int color) {
        ContentValues values = new ContentValues();
        values.put("text", text); values.put("created", System.currentTimeMillis());
        values.put("color", validColor(color));
        return getWritableDatabase().insertOrThrow("todos", null, values);
    }
    void setText(long id, String text) {
        ContentValues values = new ContentValues(); values.put("text", text);
        getWritableDatabase().update("todos", values, "_id=?", new String[] {"" + id});
    }
    void setDone(long id, boolean done) {
        ContentValues values = new ContentValues(); values.put("done", done ? 1 : 0);
        getWritableDatabase().update("todos", values, "_id=?", new String[] {"" + id});
    }
    private static int validColor(int color) {
        if (color < 0 || color >= NoteFeatures.COLORS.length) throw new IllegalArgumentException("Invalid task color");
        return color;
    }
    void setColor(long id, int color) {
        ContentValues values = new ContentValues(); values.put("color", validColor(color));
        getWritableDatabase().update("todos", values, "_id=? AND done=0", new String[] {"" + id});
    }
    void delete(long id) {
        getWritableDatabase().delete("todos", "_id=?", new String[] {"" + id});
    }
}
