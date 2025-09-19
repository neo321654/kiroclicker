package org.opencv.android;

/**
 * Interface for OpenCV loader callbacks
 */
public interface LoaderCallbackInterface {
    int SUCCESS = 0;
    int INIT_FAILED = -1;
    int INSTALL_CANCELED = 2;
    int INCOMPATIBLE_MANAGER_VERSION = 3;
    int MARKET_ERROR = 4;
}