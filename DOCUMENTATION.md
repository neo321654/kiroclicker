# Kiroclicker Documentation

## 1. Project Overview

Kiroclicker is an Android application that allows users to automate screen clicks based on image recognition. The user can select a template image, define a click point relative to it, and configure the clicking behavior (interval, repeat count). The application then uses an accessibility service to capture the screen, find the template image, and perform clicks at the specified location.

The project is structured into two main modules:
- **app**: The main Android application module, containing the UI, business logic, and services.
- **opencv**: A library module that encapsulates the OpenCV functionality for image matching.

## 2. Architecture

The application follows a standard Android architecture with a few key components:

- **UI Layer**: Consists of `MainActivity` and `ConfigFragment`. `ConfigFragment` is the main screen where the user configures the auto-clicking settings. It uses a `ConfigViewModel` to manage the UI state and interact with the business logic.
- **ViewModel**: `ConfigViewModel` acts as a bridge between the UI and the underlying services and repositories. It holds the application state, handles user interactions, and communicates with the `AutoClickService`.
- **Service Layer**: The `AutoClickService` is an `AccessibilityService` that runs in the background to perform the auto-clicking. It's responsible for capturing the screen, finding the template image, and dispatching click gestures. It communicates with the UI through `LocalBroadcastManager`.
- **Repository Layer**: The `ConfigRepository` and its implementation `SimpleConfigRepository` are responsible for persisting the user's click configurations. It saves and loads configurations, including the template images.
- **Utils**: The `utils` package contains helper classes, most notably the `ImageMatcher` interface and its `OpenCVImageMatcher` implementation, which are responsible for the core image recognition functionality.

## 3. How to Build and Run

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/neo321654/kiroclicker.git
    ```
2.  **Open in Android Studio**: Open the cloned project in Android Studio.
3.  **OpenCV Integration**: The project already includes the OpenCV library as a module. Android Studio should handle the integration automatically.
4.  **Build and Run**: Build the project and run it on an Android device or emulator with API level 28 (Android 9.0) or higher.
5.  **Enable Accessibility Service**: After installing the app, you need to manually enable the "Kiroclicker" accessibility service in your device's settings. The app will prompt you to do so.

## 4. Key Classes and Components

### 4.1. `app` Module

#### `ui/MainActivity.kt`
-   **Purpose**: The main entry point of the application. It hosts the `ConfigFragment`.

#### `ui/ConfigFragment.kt`
-   **Purpose**: The primary UI for the user to configure the auto-clicker.
-   **Functionality**:
    -   Allows the user to select a template image from the gallery or take a screenshot.
    -   Displays the selected template image.
    -   Allows the user to tap on the template image to set the click coordinates.
    -   Provides input fields for setting the click interval and repeat count.
    -   Has buttons to start and stop the auto-clicking service.
    -   Manages saving and loading of configurations.
-   **Interaction**: It communicates with the `ConfigViewModel` to manage its state and trigger actions.

#### `ui/ConfigViewModel.kt`
-   **Purpose**: Manages the state of the `ConfigFragment` and handles the business logic.
-   **Functionality**:
    -   Holds the current configuration state (template image, click coordinates, interval, etc.).
    -   Validates the user's configuration.
    -   Communicates with the `AutoClickService` to start and stop the auto-clicking process.
    -   Uses `SimpleConfigRepository` to save and load configurations.
    -   Listens for state updates from the `AutoClickService` via a `BroadcastReceiver`.

#### `service/AutoClickService.kt`
-   **Purpose**: The core of the auto-clicking functionality. It runs as an `AccessibilityService` in the background.
-   **Functionality**:
    -   Captures the screen using the `takeScreenshot` API (for Android 9.0+).
    -   Uses `OpenCVImageMatcher` to find the template image on the screen.
    -   If the template is found, it calculates the click coordinates.
    -   Dispatches click gestures using `dispatchGesture`.
    -   Manages the auto-click loop, including the interval and repeat count.
    -   Broadcasts its state (e.g., `Searching`, `Clicking`, `Waiting`, `Completed`, `Error`) to the UI.
-   **Permissions**: Requires the `AccessibilityService` permission to be enabled by the user.

#### `repository/ConfigRepository.kt` & `repository/SimpleConfigRepository.kt`
-   **Purpose**: Manages the persistence of `ClickConfig` objects.
-   **`ConfigRepository`**: An interface defining the contract for configuration persistence (CRUD operations).
-   **`SimpleConfigRepository`**: An implementation of `ConfigRepository` that saves configurations as JSON files in the app's internal storage. Template images are saved as PNG files.

#### `model/ClickConfig.kt`
-   **Purpose**: A data class that represents a single auto-clicker configuration.
-   **Properties**:
    -   `id`: A unique identifier for the configuration.
    -   `name`: A user-defined name for the configuration.
    -   `templateImagePath`: The path to the template image file.
    -   `clickX`, `clickY`: The coordinates of the click point relative to the template image.
    -   `intervalMs`: The time to wait between clicks.
    -   `repeatCount`: The number of times to repeat the click (-1 for infinite).
    -   `threshold`: The confidence threshold for image matching.

#### `model/AutoClickState.kt`
-   **Purpose**: A sealed class that represents the different states of the auto-clicking process.
-   **States**: `Idle`, `Searching`, `Clicking`, `Waiting`, `Completed`, `Error`.

#### `utils/ImageMatcher.kt` & `utils/OpenCVImageMatcher.kt`
-   **Purpose**: Responsible for the image recognition logic.
-   **`ImageMatcher`**: An interface that defines the contract for template matching.
-   **`OpenCVImageMatcher`**: An implementation of `ImageMatcher` that uses the OpenCV library to perform template matching (`Imgproc.matchTemplate`). It converts `Bitmap` objects to OpenCV's `Mat` format for processing.

### 4.2. `opencv` Module

-   **Purpose**: This module is a wrapper for the OpenCV Android SDK. It provides the necessary native libraries and Java wrappers to perform image processing tasks. The `OpenCVImageMatcher` in the `app` module directly depends on the classes provided by this module.

## 5. Services and Permissions

-   **`AutoClickService` (Accessibility Service)**: This is the main background service. It requires the user to grant accessibility permissions. This permission is powerful and allows the app to see the screen content and perform gestures on behalf of the user.
-   **`FOREGROUND_SERVICE` Permission**: The `AutoClickService` runs as a foreground service to ensure it's not killed by the system while it's active. This requires the `FOREGROUND_SERVICE` permission.
-   **`SYSTEM_ALERT_WINDOW` Permission**: While not currently used, this permission might be needed in the future for displaying overlays.
-   **`READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` Permissions**: These are required for the user to be able to select a template image from their device's gallery.

## 6. Data Flow

1.  **Configuration**:
    -   The user interacts with `ConfigFragment` to set up a `ClickConfig`.
    -   The `ConfigFragment` updates the `ConfigViewModel` with the new settings.
    -   The `ConfigViewModel` validates the configuration.
    -   The user can save the configuration, which is handled by the `ConfigViewModel` and `SimpleConfigRepository`.

2.  **Starting Auto-Click**:
    -   The user clicks the "Start" button in `ConfigFragment`.
    -   `ConfigFragment` calls `startAutoClick()` on `ConfigViewModel`.
    -   `ConfigViewModel` creates a `ClickConfig` object and sends it to the `AutoClickService`.
    -   `AutoClickService` starts its auto-click loop.

3.  **Auto-Click Loop**:
    -   `AutoClickService` captures the screen.
    -   It passes the screenshot and template image to `OpenCVImageMatcher`.
    -   `OpenCVImageMatcher` finds the template and returns the location.
    -   `AutoClickService` calculates the click coordinates and dispatches a click gesture.
    -   It waits for the specified interval and repeats the process.

4.  **State Updates**:
    -   `AutoClickService` broadcasts its state changes using `LocalBroadcastManager`.
    -   `ConfigViewModel` has a `BroadcastReceiver` that listens for these updates.
    -   When a state update is received, `ConfigViewModel` updates its `LiveData`, which in turn updates the UI in `ConfigFragment`.
