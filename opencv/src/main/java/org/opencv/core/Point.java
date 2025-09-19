package org.opencv.core;

/**
 * OpenCV Point class
 */
public class Point {
    public double x;
    public double y;
    
    public Point() {
        this.x = 0;
        this.y = 0;
    }
    
    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    public Point clone() {
        return new Point(x, y);
    }
    
    @Override
    public String toString() {
        return "Point{" + "x=" + x + ", y=" + y + '}';
    }
}