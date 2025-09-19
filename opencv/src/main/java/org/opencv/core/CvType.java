package org.opencv.core;

/**
 * OpenCV data types
 */
public class CvType {
    public static final int CV_8UC1 = 0;
    public static final int CV_8UC3 = 16;
    public static final int CV_8UC4 = 24;
    public static final int CV_32FC1 = 5;
    
    public static int makeType(int depth, int channels) {
        return (channels - 1) << 3 | depth;
    }
}