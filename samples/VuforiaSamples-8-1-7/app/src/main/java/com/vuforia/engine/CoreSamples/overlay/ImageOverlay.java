package com.vuforia.engine.CoreSamples.overlay;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;

public class ImageOverlay extends OverlayElement {

    /**
     * This bitmap holds the image to display
     */
    private Bitmap image;

    /**
     * These integers hold the width and the height of the image (separate from the bounds)
     */
    private int imageWidth, imageHeight;

    /**
     * This matrix will be used to apply transformations to the image
     */
    private Matrix matrix;

    /**
     * Constructs an instance of this class using the given bitmap, setting the rest of the variables to their defaults
     *
     * @param bitmapIn the bitmap (image) to display
     */
    public ImageOverlay(Bitmap bitmapIn) {
        super(TYPE_IMAGE);
        processImage(bitmapIn);
        matrix = new Matrix();
    }

    /**
     * Constructs an instance of this class using the given bitmap and paint instance, setting the rest of the variables to their defaults
     *
     * @param bitmapIn the bitmap (image) to display
     * @param paintIn the paint instance
     */
    public ImageOverlay(Bitmap bitmapIn, Paint paintIn) {
        super(TYPE_IMAGE, paintIn);
        processImage(bitmapIn);
        matrix = new Matrix();
    }

    /**
     * Constructs an instance of this class using the given bitmap, paint instance, bounds, and position, setting the rest of the variables to their defaults
     *
     * @param bitmapIn the bitmap (image) to display
     * @param paintIn the paint instance
     * @param boundsIn the bounds to set
     * @param position the position to set
     */
    public ImageOverlay(Bitmap bitmapIn, Paint paintIn, RectF boundsIn, Point position) {
        super(TYPE_IMAGE, paintIn, boundsIn, position);
        processImage(bitmapIn);
        matrix = new Matrix();
    }

    /**
     * This method returns the image displayed on the screen
     *
     * @return the bitmap of the image
     */
    public Bitmap getImage(){
        return image;
    }

    /**
     * This method returns the transform matrix
     *
     * @return the matrix of this object
     */
    public Matrix getMatrix(){
        return matrix;
    }

    public int getImageWidth(){
        return imageWidth;
    }

    public int getImageHeight(){
        return imageHeight;
    }

    /**
     * This method sets a new matrix to this object
     *
     * @param matrixIn the new matrix to set
     */
    public void setMatrix(Matrix matrixIn){
        matrix = matrixIn;
    }

    /**
     * This method sets a new image to be displayed
     *
     * @param imageIn the bitmap to set
     */
    public void setImage(Bitmap imageIn){
        processImage(imageIn);
    }

    /**
     * This method sets a new image width. It it recommended to use the setScale() method to apply transformations, to keep the same aspect ratio.
     * It is used to keep track of the bitmap's width
     *
     * @param widthIn the new width to set
     */
    public void setImageWidth(int widthIn){
        imageWidth = widthIn;
    }

    /**
     * This method sets a new image height. It is recommended to use the setScale() method to apply transformations, to keep the same aspect ratio.
     * It is used to keep track of the bitmap's height
     *
     * @param heightIn the new height to set
     */
    public void setImageHeight(int heightIn){
        imageHeight = heightIn;
    }

    /**
     * This method takes in a new image (bitmap), and modifies the bounds to set them to the image's dimensions
     *
     * @param img the new image to process
     */
    public void processImage(Bitmap img){
        image = img;
        imageWidth = image.getWidth();
        imageHeight = image.getHeight();
        getBounds().set(0,0, imageWidth, imageHeight);
    }

    /**
     * This method draws this object's image to the given canvas, applying the desired transformations at the same time
     *
     * @param canvas the canvas to draw the image onto
     */
    public void drawImage(Canvas canvas){

        //Reset the matrix
        getMatrix().reset();

        //Apply scale to the matrix
        getMatrix().postScale(getScale(), getScale());

        //Apply translate (to move center of image at origin) to the matrix
        getMatrix().postTranslate(-getBounds().width()/2, -getBounds().height()/2);

        //If rotation is enabled, apply rotation to the matrix
        if(getIsRotated()){
            getMatrix().postRotate(getRotation());
        }

        //Apply translate to the matrix (move the image to the position coordinates)
        getMatrix().postTranslate(getPosition().x, getPosition().y);

        //Draw the image (bitmap) using the matrix
        canvas.drawBitmap(image, getMatrix(), getPaint());
    }
}
