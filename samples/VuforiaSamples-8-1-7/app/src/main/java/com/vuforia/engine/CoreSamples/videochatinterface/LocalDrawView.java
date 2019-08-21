package com.vuforia.engine.CoreSamples.videochatinterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Size;
import android.view.View;

/**
 * This class is a custom view similar to DrawView, except that this class is designed to render frames in a smaller view, without any overlays.
 *
 * @author Nathan Sonier
 */
public class LocalDrawView extends View {

    //The current frame that is displayed
    private Bitmap currentBitmap;

    //The paint instance that will be used for basic drawing
    private Paint paint;

    //Matrix used to transform the bitmap frame
    private Matrix m;

    //The orientation of the bitmap to draw
    private int orientation;

    //The size of the view we want to draw
    public Size viewSize;

    //The position of the view we want to draw
    public Point viewPosition;

    //The bounds of the view we want to draw
    public Rect viewBounds;

    public LocalDrawView(Context context) {
        super(context);
        currentBitmap = null;
        setup();
    }

    public LocalDrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        currentBitmap = null;
        setup();
    }

    public LocalDrawView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        currentBitmap = null;
        setup();
    }

    /**
     * This method will setup the instance variables and defaults
     */
    private void setup(){
        paint = new Paint();
        m = new Matrix();
        orientation = 0;
        viewSize = new Size(0,0);
        viewPosition = new Point();
    }

    /**
     * This method will render the current frame on the view (based on the orientation), and calculate dimensions
     *
     * @param bitmap the bitmap frame to display
     * @param orientationIn the orientation to display the bitmap frame
     */
    public void renderOnView(Bitmap bitmap, int orientationIn){
        currentBitmap = bitmap;
        orientation = orientationIn;
        viewSize = new Size(currentBitmap.getWidth()/2, currentBitmap.getHeight()/2);
        viewPosition = new Point((-viewSize.getWidth() / 2) + (10 + (viewSize.getWidth()/4) + (viewSize.getHeight()/2)), (-viewSize.getHeight() / 2) + (getHeight() - 10 - (viewSize.getHeight()/2) - (viewSize.getWidth()/2)));
        viewBounds = new Rect(viewPosition.x, viewPosition.y, viewPosition.x + viewSize.getHeight(), viewPosition.y + viewSize.getWidth());
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //Clear the view by filling it with a transparent color
        canvas.drawColor(Color.TRANSPARENT);

        //If there is a bitmap frame available
        if(currentBitmap != null){
            //Reset the matrix
            m.reset();

            //Translate the frame to the middle
            m.postTranslate(-currentBitmap.getWidth() / 2, -currentBitmap.getHeight() / 2);

            //Rotate the frame around angle
            m.postRotate(orientation);

            //Scale the frame based on the current ratio
            m.postScale(0.5f, 0.5f);

            //Translate the frame back to its position
            m.postTranslate(10 + (currentBitmap.getWidth()/4), getHeight() - 10 - (currentBitmap.getHeight()/2));

            //Draw the frame onto the canvas
            canvas.drawBitmap(currentBitmap, m, paint);

        }

        
    }

    /**
     * This method will set the orientation of the frame
     *
     * @param orientationIn the new orientation to use
     */
    public void setOrientation(int orientationIn){
        orientation = orientationIn;
    }
}
