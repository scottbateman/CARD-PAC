package com.vuforia.engine.CoreSamples.videochatinterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;

import com.vuforia.engine.CoreSamples.overlay.TextOverlay;

import java.util.HashMap;

/**
 * This class is a custom view that will be used to display bitmap frames onto it (using a canvas).
 * The reason for using a custom view such as this one is to be able to draw our own objects onto the canvas (by overriding the onDraw method).
 * In addition to drawing bitmap frames, we also want to implement QR code detection, which means we need to draw an overlay on top of the frames.
 *
 * @author Nathan Sonier
 */
public class DrawView extends View {

    //The current frame being drawn onto the screen
    private Bitmap currentFrame;

    //The active QR codes (ones that have been detected on the screen)
    private String[] activeValues;

    //A hash map that will map the contents of each QR code to an ID (which means QR codes with the same contents would have the same ID)
    private HashMap<String, Integer> idsMap;

    //A sparse array containing bounds for each QR code, placed at rectsMap[ID](which is why we map the contents of the codes to an ID)
    private SparseArray<Rect> rectsMap;

    //A sparse array containing the text overlays to draw on the screen for each QR code, placed at rectsMap[ID] (which again is why we map string -> integer)
    private SparseArray<TextOverlay> textOverlayMap;

    //The paint instance used for general drawing in this class
    private Paint paint;

    //The matrix to transform the bitmap before drawing
    private Matrix m;

    //The number of ticks the view currently has (this is to time certain actions in the class)
    private int ticks;

    //The current ID number that is to be assigned (incremented every time we assign a new ID)
    private int globalIDs;

    //The current orientation at which to display the frame
    private int orientation;

    public DrawView(Context context) {
        super(context);
        setup();
    }

    public DrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public DrawView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    /**
     * This method will setup all the instance variables and defaults
     */
    private void setup(){
        paint = new Paint();
        paint.setColor(Color.WHITE);
        m = new Matrix();

        //Initialize Hash Map and Sparse Arrays
        activeValues = null;
        idsMap = new HashMap<>();
        rectsMap = new SparseArray<>();
        textOverlayMap = new SparseArray<>();

        orientation = 0;

        globalIDs = 0;
    }

    /**
     * This method will render a new bitmap frame onto this view (taking the orientation provided into account)
     *
     * @param bitmap the frame to display
     * @param orientationIn the orientation of the frame
     */
    public void renderOnView(Bitmap bitmap, int orientationIn){
        currentFrame = bitmap;
        orientation = orientationIn;
        invalidate();
    }

    /**
     * This method will set various data in this class.
     * It is called externally, and is currently being called by the QR code scanner to move the data from that class to this one (in order to display)
     *
     * @param overlays the text overlays to display on top of the screen
     * @param valuesIn the contents of each QR code found on the screen
     * @param rectsIn the bounds of each QR code found on the screen
     */
    public void setData(TextOverlay[] overlays, String[] valuesIn, Rect[] rectsIn){
        //Increment ticks
        ticks++;

        //Assume there is no new data by default
        boolean newData = false;

        /*
            The following if statement is used to minimize the flickering of the QR codes
            It does so by only changing the active QR codes every 30 ticks (or 60 if none are found for a while), which is a few seconds
            This means that the code updates only the position (and other transform properties) every tick
         */
        //If QR codes were found, and ticks are bigger than 30, add the new QR codes
        if(valuesIn != null && ticks >= 30){
            activeValues = valuesIn;
            ticks = 0;
            newData = true;
        //If there were no QR codes found, but the ticks are bigger than 60, reset the active QR codes to null (none)
        }else if(valuesIn == null && ticks >= 60){
            activeValues = null;
            newData = true;
            ticks = 0;
        }

        //If there are QR codes on the screen
        if(valuesIn != null){
            //Refresh data
            for(int i=0;i<valuesIn.length;i++){
                int tempID = 0;

                //If the qr code doesn't exist yet, register it
                if(!idsMap.containsKey(valuesIn[i])){
                    tempID = globalIDs++;
                    idsMap.put(valuesIn[i], tempID);
                //If it does exist, retrieve its ID
                }else{
                    tempID = idsMap.get(valuesIn[i]);
                }

                //Update the data for that item
                rectsMap.put(tempID, rectsIn[i]);
                textOverlayMap.put(tempID, overlays[i]);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //Clear the view by filling it with a transparent color
        canvas.drawColor(Color.TRANSPARENT);

        //If the current frame is available (not null)
        if(currentFrame != null){
            //Reset the transform matrix
            m.reset();

            //Translate the frame to the middle
            m.postTranslate(-currentFrame.getWidth() / 2, -currentFrame.getHeight() / 2);

            //Rotate the frame based on orientation
            m.postRotate(orientation);

            //Scale the frame based on the current ratio
            m.postScale(2, 2);

            //Translate the frame back to its position
            m.postTranslate(canvas.getWidth()/2, canvas.getHeight()/2);

            //Draw the frame onto the canvas
            canvas.drawBitmap(currentFrame, m, paint);
        }

        //If there are any active QR codes on the screen
        if(activeValues != null){
            //For each active QR code
            for(int i=0;i<activeValues.length;i++){

                //Store index (or ID)
                int index = idsMap.get(activeValues[i]);

                /*m.reset();

                m.postTranslate(-50, -50);

                m.postRotate(anglesMap.get(index));

                m.postScale(ratiosMap.get(index),ratiosMap.get(index));

                m.postTranslate(centersMap.get(index).x, centersMap.get(index).y);
                */

                //Get the associated overlay and draw it to the canvas
                textOverlayMap.get(index).drawText(canvas);

                //Matrix is ready to be used - Draw anything (relative to the QR Code's position) below
                //canvas.drawBitmap(IMAGE, m, paint);

                //RectF rect = new RectF(0,0,100,100);
                //m.mapRect(rect);
                //canvas.drawRect(rect, paint);
            }
        }


    }

    public void setOrientation(int orientationIn){
        orientation = orientationIn;
    }

    public int[] getActiveData(){
        if(activeValues == null){
            return null;
        }
        int[] res = new int[activeValues.length];

        for(int i = 0; i< res.length;i++){
            res[i] = idsMap.get(activeValues[i]);
        }

        return res;
    }

    public TextOverlay getOverlayFromMap(int index){
        return textOverlayMap.get(index);
    }
}
