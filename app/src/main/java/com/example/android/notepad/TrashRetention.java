package com.example.android.notepad;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

/** Thirty elapsed days from deletion, independent of timezone and list filters. */
final class TrashRetention {
    static final long DAY = 24L * 60 * 60 * 1000;
    static final long RETENTION = 30L * DAY;

    static int purge(SQLiteDatabase db, long now) {
        return db.delete("notes", "deleted_at>0 AND deleted_at<=?",
                new String[] {String.valueOf(now - RETENTION)});
    }

    static int remainingDays(long deletedAt, long now) {
        long age = Math.max(0, now - deletedAt);
        long left = Math.max(0, RETENTION - age);
        return (int) ((left + DAY - 1) / DAY);
    }

    static void schedule(Context context, SQLiteDatabase db, long now) {
        Cursor cursor = db.rawQuery("SELECT MIN(deleted_at) FROM notes WHERE deleted_at>0", null);
        long oldest = 0;
        try { if (cursor.moveToFirst() && !cursor.isNull(0)) oldest = cursor.getLong(0); }
        finally { cursor.close(); }
        Intent intent = new Intent(context, TrashCleanupReceiver.class).setAction("com.example.android.notepad.CLEAN_TRASH");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent alarm = PendingIntent.getBroadcast(context, 30, intent, flags);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (oldest == 0) manager.cancel(alarm);
        else manager.set(AlarmManager.RTC_WAKEUP, Math.max(now + 1000, oldest + RETENTION), alarm);
    }
}
