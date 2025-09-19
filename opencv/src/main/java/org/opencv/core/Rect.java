package org.opencv.core;

/**
 * OpenCV Rect class
 */
public class Rect {
    public int x;
    public int y;
    public int width;
    public int height;
    
    public Rect() {
        this.x = 0;
        this.y = 0;
        this.width = 0;
        this.height = 0;
    }
    
    public Rect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public Rect(Point topLeft, Point bottomRight) {
        this.x = (int) topLeft.x;
        this.y = (int) topLeft.y;
        this.width = (int) (bottomRight.x - topLeft.x);
        this.height = (int) (bottomRight.y - topLeft.y);
    }
    
    public Point tl() {
        return new Point(x, y);
    }
    
    public Point br() {
        return new Point(x + width, y + height);
    }
    
    public Size size() {
        return new Size(width, height);
    }
    
    public double area() {
        return width * height;
    }
    
    public Rect clone() {
        return new Rect(x, y, width, height);
    }
    
    @Override
    public String toString() {
        return "Rect{" + "x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + '}';
    }
}