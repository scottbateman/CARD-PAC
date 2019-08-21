package com.vuforia.engine.CoreSamples.overlay;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;

/**
 * This class represents an element that is designed to be displayed on the screen (usually over another view/image).
 * While this class could be used directly in a program, it is recommended to only use it as a parent class for your own child class.
 * Extending this class will allow you to make graphical overlay of any type (the choice is yours).
 * Currently, there are two classes ready to be used (that extend this class):
 * - TextOverlay (see docs)
 * - ImageOverlay (see docs)
 *
 * This class only delivers basic functions of a drawn object:
 *  - Position, rotation, and scale of the object
 *  - Bounds of the object (as a RectF)
 *  - Type (currently one of TYPE_TEXT, TYPE_IMAGE, or TYPE_OTHER)
 *  - The Paint instance used to draw the object onto a canvas
 *  - drawAtCenter (whether or not the coordinates represent the top-left corner of the rectangle, or its center)
 *  - isRotated (whether or not the object should have rotation applied to it - enable or disable)
 *
 *  Additional information is present in the methods' documentation
 *
 * @author Nathan Sonier
 */
public class OverlayElement {

    /**
     * These integers represent a unique ID for each type of overlay that can be created
     */
    public static final int TYPE_TEXT = 0,
                            TYPE_IMAGE = 1,
                            TYPE_OTHER = 2;

    /**
     * The drawn object's position on the screen
     */
    private Point position;

    /**
     * The bounds of the drawn object (rectangle)
     */
    private RectF bounds;

    /**
     * The type of the overlay (one of TYPE_*)
     */
    private int type;

    /**
     * The rotation of the object on the screen
     */
    private float rotation;

    /**
     * The scale of the object (also scales the bounds)
     */
    private float scale;

    /**
     * The paint instance that contains drawing data about how to draw the overlay
     */
    private Paint paint;

    /**
     * drawAtCenter - whether or not the class should draw the object's top-left corner or its center at the coordinates
     * isRotated - whether or not the object should have rotation applied to it
     */
    private boolean drawAtCenter, isRotated;


    /**
     * Constructs an instance of this class with only a specific type, the rest of the variables being set to defaults.
     *
     * @param typeIn
     */
    public OverlayElement(int typeIn){
        type = typeIn;
        paint = new Paint();
        paint.setColor(Color.WHITE);
        bounds = new RectF();
        position = new Point();
        rotation = 0;


        drawAtCenter = true;
        isRotated = false;
    }

    /**
     * Constructs an instance of this class with a type and a paint instance, setting the other variables to their defaults
     *
     * @param typeIn
     * @param paintIn
     */
    public OverlayElement(int typeIn, Paint paintIn){
        type = typeIn;
        paint = paintIn;
        bounds = new RectF();
        position = new Point();
        rotation = 0;

        drawAtCenter = true;
        isRotated = false;
    }

    /**
     * Constructs an instance of this class with a type, paint instance, and rectangle bounds, setting the other variables to their defaults
     *
     * @param typeIn
     * @param paintIn
     * @param boundsIn
     */
    public OverlayElement(int typeIn, Paint paintIn, RectF boundsIn){
        type = typeIn;
        paint = paintIn;
        bounds = boundsIn;
        position = new Point();
        rotation = 0;

        drawAtCenter = true;
        isRotated = false;
    }

    /**
     * Constructs an instance of this class with a type, paint instance, rectangle bounds, and position, setting the other variables to their defaults
     *
     * @param typeIn
     * @param paintIn
     * @param boundsIn
     * @param positionIn
     */
    public OverlayElement(int typeIn, Paint paintIn, RectF boundsIn, Point positionIn){
        type = typeIn;
        paint = paintIn;
        bounds = boundsIn;
        position = positionIn;
        rotation = 0;

        drawAtCenter = true;
        isRotated = false;
    }

    /**
     * This method returns this object's bounds
     *
     * @return the rectangle bounds
     */
    public RectF getBounds(){
        return bounds;
    }

    /**
     * This method returns this object's type
     *
     * @return the type
     */
    public int getType(){
        return type;
    }

    /**
     * This method returns this object's paint instance containing the drawing settings
     *
     * @return
     */
    public Paint getPaint(){
        return paint;
    }

    /**
     * This method returns this object's position
     *
     * @return the position coordinates
     */
    public Point getPosition(){
        return position;
    }

    /**
     * This method returns this object's rotation
     *
     * @return the rotation
     */
    public float getRotation(){
        return rotation;
    }

    /**
     * This method returns whether the object's coordinates represent its top-left corner or center
     *
     * @return true or false depending on what the coordinates represent
     */
    public boolean getDrawAtCenter(){
        return drawAtCenter;
    }

    /**
     * This method returns whether the object should be rotated when drawn or not
     *
     * @return true or false depending on whether this object should be rotated
     */
    public boolean getIsRotated(){
        return isRotated;
    }

    /**
     * This method returns the scale factor of this object
     *
     * @return the scale factor
     */
    public float getScale(){
        return scale;
    }

    /**
     * This method sets new rectangle bounds
     *
     * @param newBounds the new bounds to set
     */
    public void setBounds(RectF newBounds){
        bounds = newBounds;
    }

    /**
     * This method sets the paint instance to a new instance, changing the settings
     *
     * @param newPaint the new instance containing the settings
     */
    public void setPaint(Paint newPaint){
        paint = newPaint;
    }

    /**
     * This method sets a new position for this object from two integers
     *
     * @param x the new x value
     * @param y the new y value
     */
    public void setPosition(int x, int y){
        position.set(x,y);
    }

    /**
     * This method sets a new position for this object from a point object
     *
     * @param p the point object containing the x and y values
     */
    public void setPosition(Point p){
        position.set(p.x, p.y);
    }

    /**
     * This method sets a new rotation for this object
     *
     * @param rotationIn the new rotation to set
     */
    public void setRotation(float rotationIn){
        rotation = rotationIn;
    }

    /**
     * This method sets whether the object should be rotated when drawn or not
     *
     * @param isRotatedIn true or false, depending on whether the object should be rotated or not
     */
    public void setIsRotated(boolean isRotatedIn){
        isRotated = isRotatedIn;
    }

    /**
     * This method sets a new scale factor to the object. Calling this method also causes the object bounds to be scaled right away (bounds are modified)
     *
     * @param scaleIn the new scale value to set
     */
    public void setScale(float scaleIn){
        bounds.set(bounds.left*scaleIn, bounds.top*scaleIn, bounds.right*scaleIn, bounds.bottom*scaleIn);
        scale = scaleIn;
    }

    /**
     * This method sets whether the coordinates should represent the top-left corner of the object (false) or the center (true)
     *
     * @param newDrawAtCenter true or false, depending on whether the position is the center or not
     */
    public void setDrawAtCenter(Boolean newDrawAtCenter){
        drawAtCenter = newDrawAtCenter;
    }
}
