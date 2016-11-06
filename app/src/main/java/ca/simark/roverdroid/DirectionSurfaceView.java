package ca.simark.roverdroid;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.SurfaceView;

public class DirectionSurfaceView extends SurfaceView {
    int fSavedColor;

    public DirectionSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setActive() {
        Drawable d = this.getBackground();

        if (d instanceof ColorDrawable) {
            ColorDrawable c = (ColorDrawable) d;

            fSavedColor = c.getColor();
            c.setColor(Color.RED);
        }
    }

    public void setInactive() {
        Drawable d = this.getBackground();

        if (d instanceof ColorDrawable) {
            ColorDrawable c = (ColorDrawable) d;

            c.setColor(fSavedColor);
        }
    }
}
