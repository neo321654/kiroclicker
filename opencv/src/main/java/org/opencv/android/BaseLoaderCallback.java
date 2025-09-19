package org.opencv.android;

import android.content.Context;

/**
 * Base callback for OpenCV loader
 */
public abstract class BaseLoaderCallback {
    protected Context context;
    
    public BaseLoaderCallback(Context context) {
        this.context = context;
    }
    
    /**
     * Called when OpenCV manager connection is established
     * @param status Connection status
     */
    public abstract void onManagerConnected(int status);
}