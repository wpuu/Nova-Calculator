package org.solovyev.android.calculator;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Commercial placeholder for the inherited ad slot.
 *
 * The original Calculator++ AdMob identifiers must never receive Nova traffic. Until a
 * Nova-owned advertising configuration and consent flow are introduced, this view remains
 * permanently hidden and performs no network requests.
 */
public class AdView extends FrameLayout {

    public AdView(Context context) {
        super(context);
        init();
    }

    public AdView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AdView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setVisibility(GONE);
        setId(R.id.cpp_ad);
    }

    public void destroy() {
    }

    public void pause() {
    }

    public void resume() {
    }

    public void show() {
        setVisibility(GONE);
    }

    public void hide() {
        setVisibility(GONE);
    }
}
