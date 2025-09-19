package org.opencv.core;

/**
 * OpenCV Size class
 */
public class Size {
    public double width;
    public double height;
    
    public Size() {
        this.width = 0;
        this.height = 0;
    }
    
    public Size(double width, double height) {
        this.width = width;
        this.height = height;
    }
    
    public Size clone() {
        return new Size(width, height);
    }
    
    @Override
    public String toString() {
        return "Size{" + "width=" + width + ", height=" + height + '}';
    }
}