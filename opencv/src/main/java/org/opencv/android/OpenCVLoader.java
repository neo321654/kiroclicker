package org.opencv.android;

import android.content.Context;

/**
 * OpenCV Loader for Android
 * This is a minimal implementation for the autoclicker project
 */
public class OpenCVLoader {
    public static final String OPENCV_VERSION = "4.8.0";
    
    /**
     * Initialize OpenCV in debug mode (using internal libraries)
     * @return true if initialization successful
     */
    public static boolean initDebug() {
        try {
            System.loadLibrary("opencv_java4");
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }
    
    /**
     * Initialize OpenCV asynchronously
     * @param version OpenCV version
     * @param context Application context
     * @param callback Callback for initialization result
     */
    public static void initAsync(String version, Context context, BaseLoaderCallback callback) {
        // For this implementation, we'll try to load the library directly
        if (initDebug()) {
            callback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            callback.onManagerConnected(LoaderCallbackInterface.INIT_FAILED);
        }
    }
}