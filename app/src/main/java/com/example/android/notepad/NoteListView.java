package com.example.android.notepad;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

/** Observes horizontal gestures even when a finger starts on an overlay action button. */
public class NoteListView extends ListView {
    private View.OnTouchListener swipeListener;
    private boolean consumingSwipe;

    public NoteListView(Context context, AttributeSet attrs) { super(context, attrs); }

    void setSwipeTouchListener(View.OnTouchListener listener) { swipeListener = listener; }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) consumingSwipe = false;
        if (swipeListener != null && swipeListener.onTouch(this, event)) {
            if (!consumingSwipe) {
                // A drag must cancel the underlying button, rather than trigger it on release.
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
            consumingSwipe = event.getActionMasked() != MotionEvent.ACTION_UP
                    && event.getActionMasked() != MotionEvent.ACTION_CANCEL;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) consumingSwipe = false;
        return super.dispatchTouchEvent(event);
    }
}
