package com.example.android.notepad;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;
import android.text.TextUtils;

/** Reads clipboard text while the list is focused, before launching the editor. */
final class NoteClipboard {
    private NoteClipboard() { }

    static Bundle read(Context context) {
        ClipboardManager manager = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return null;
        ClipData.Item item = clip.getItemAt(0);
        String title = null;
        String text = null;
        Uri uri = item.getUri();
        try {
            if (uri != null && NotePad.AUTHORITY.equals(uri.getAuthority())) {
                Cursor cursor = context.getContentResolver().query(uri,
                        new String[] { NotePad.Notes.COLUMN_NAME_TITLE,
                                NotePad.Notes.COLUMN_NAME_NOTE }, null, null, null);
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        title = cursor.getString(0);
                        text = cursor.getString(1);
                    }
                } finally {
                    if (cursor != null) cursor.close();
                }
            }
            if (text == null) {
                CharSequence value = item.coerceToText(context);
                text = value == null ? "" : value.toString();
            }
        } catch (RuntimeException exception) {
            return null;
        }
        if (TextUtils.isEmpty(text.trim()) && TextUtils.isEmpty(title)) return null;
        Bundle result = new Bundle();
        result.putString(Intent.EXTRA_TEXT, text);
        result.putString(Intent.EXTRA_TITLE, title);
        return result;
    }
}
