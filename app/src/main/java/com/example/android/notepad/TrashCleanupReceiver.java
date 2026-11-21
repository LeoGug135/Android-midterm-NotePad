package com.example.android.notepad;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reschedules after reboot/time changes; the provider also purges on access. */
public final class TrashCleanupReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context context, Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try { context.getContentResolver().call(NotePad.Notes.CONTENT_URI, "purgeExpiredTrash", null, null); }
                finally { pending.finish(); }
            }
        }, "NotePad-trash-cleanup").start();
    }
}
