package com.vuforia.engine.CoreSamples.overlay;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

/**
 * This class is a child of OverlayElement, and represents a graphical element that will be drawn. Specifically, it will draw a String (text).
 * Currently, it uses the paint instance (parent class) to decide the color the of the background of the text.
 * Use this class if you need to draw or write text to a canvas. The text can be of any length (dependent on String class), and the user only needs to enter the size of the max width (text).
 * Once the width is provided, the height necessary to fit all the text in the box is computed, and the bounds are set.
 * A width of 0 will result in a text that is invisible
 *
 * Once the proper settings are set, the user can draw this overlay by calling drawText(), and passing in a canvas (on which to draw the text).
 * For a list of methods and settings, see documentation of parent class, OverlayElement.
 *
 * @author Nathan Sonier
 *
 */
public class TextOverlay extends OverlayElement {

    //Instance variables
    /**
     * The text to be displayed on the canvas
     */
    private String content;

    /**
     * The text paint instance that will determine how the text will look on the canvas (separate from the paint instance)
     */
    private TextPaint textPaint;

    /**
     * The static layout that will hold both the content and the dimensions to draw
     */
    private StaticLayout staticLayout;

    /**
     * Constructs an instance of this class with the text (String), paint instance, and width, setting the rest of the settings to their defaults
     *
     * @param contentIn the text to display
     * @param paintIn the paint instance to use
     * @param width the maximum width of the text box
     */
    public TextOverlay(String contentIn, Paint paintIn, float width){
        super(OverlayElement.TYPE_TEXT, paintIn, new RectF(0,0, width, 0));
        setupTextPaint();
        processContent(contentIn);
    }

    /**
     * Constructs an instance of this class with the text (String), paint instance, width, and position, setting the rest of the settings to their defaults
     *
     * @param contentIn the text to display
     * @param paintIn the paint instance to use
     * @param width the maximum width of the text box
     * @param position the position of where to draw the text
     */
    public TextOverlay(String contentIn, Paint paintIn, float width, Point position){
        super(OverlayElement.TYPE_TEXT, paintIn, new RectF(0,0, width, 0), position);
        setupTextPaint();
        processContent(contentIn);
    }

    /**
     * This method sets up the text paint instance, with default settings (Color: black, Text Size: 35)
     */
    public void setupTextPaint(){
        textPaint = new TextPaint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(35);
    }

    /**
     * This method returns the text displayed on the screen
     *
     * @return the text displayed
     */
    public String getContent(){
        return content;
    }

    /**
     * This method returns the text paint instance
     *
     * @return the text paint instance
     */
    public TextPaint getTextPaint(){
        return textPaint;
    }

    /**
     * This method returns the static layout used to draw this object
     *
     * @return the static layout object
     */
    public StaticLayout getStaticLayout(){
        return staticLayout;
    }

    /**
     * This method sets a new text paint instance for this object
     *
     * @param textPaintIn the new text paint to set
     */
    public void setTextPaint(TextPaint textPaintIn){
        textPaint = textPaintIn;
    }

    /**
     * This method sets the content (text), and returns the new object with the updated text
     *
     * @param newContent the new text to display
     *
     * @return the instance with the updated text
     */
    public TextOverlay setContent(String newContent){
        processContent(newContent);
        return this;
    }

    /**
     * This method takes in a string (text to display), and computes the dimensions of the box necessary to fit all of the text in it (and changes this object's bounds)
     *
     * @param c the text to display
     */
    private void processContent(String c){
        content = c;
        staticLayout = new StaticLayout(content,textPaint,(int)getBounds().width(), Layout.Alignment.ALIGN_CENTER,1.0f,0.0f,false);
        getBounds().set(getBounds().left, getBounds().top, getBounds().right, staticLayout.getLineCount() * (textPaint.descent() - textPaint.ascent()));
    }

    /**
     * This method takes in a canvas, and draws the text and text box to it
     *
     * @param canvas the canvas to draw onto
     */
    public void drawText(Canvas canvas){
        canvas.save();

        //Check whether or not to interpret the coordinates as the top-left corner, and translate the canvas accordingly
        if(getDrawAtCenter()){
            canvas.translate(getPosition().x - (getBounds().width()/2), getPosition().y - (getBounds().height()/2));
        }else{
            canvas.translate(getPosition().x, getPosition().y);
        }

        //Draw the text box to the canvas
        canvas.drawRect(new RectF(0, 0, getBounds().width(), getBounds().height()), getPaint());

        //Draw the static layout (which contains the text) to the canvas
        staticLayout.draw(canvas);

        canvas.restore();
    }

    /**
     * This method sets the scale factor of the object. Additionally, it scales the text size as well.
     *
     * @param scaleIn the new scale factor to set
     */
    @Override
    public void setScale(float scaleIn){
        super.setScale(scaleIn);
        textPaint.setTextSize(textPaint.getTextSize() * scaleIn);
        staticLayout = new StaticLayout(content,textPaint,(int)getBounds().width(), Layout.Alignment.ALIGN_CENTER,1.0f,0.0f,false);
    }
}
