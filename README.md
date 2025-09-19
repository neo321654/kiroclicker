# Android AutoClicker

An Android application that allows users to automate clicks on the screen using image template matching.

## Project Structure

```
app/
├── src/main/java/com/autoclicker/android/
│   ├── ui/                 # UI components (Activities, Fragments, ViewModels)
│   ├── service/           # Background services (AutoClickService)
│   ├── repository/        # Data management (ConfigRepository)
│   ├── model/            # Data models (ClickConfig, AutoClickState, MatchResult)
│   └── utils/            # Utility classes (ImageMatcher, AccessibilityUtils)
├── src/main/res/         # Android resources (layouts, strings, etc.)
└── src/test/            # Unit tests
opencv/                   # OpenCV Android SDK module
```

## Requirements

- Android API 28+ (Android 9.0)
- OpenCV Android SDK (to be integrated)
- Accessibility Service permissions

## Setup Instructions

1. Clone the repository
2. Open in Android Studio
3. Download OpenCV Android SDK and integrate it into the `opencv` module
4. Build and run the project

## Features (To be implemented)

- Image template selection from gallery or screenshot
- Click coordinate configuration
- Automated clicking with customizable intervals
- Configuration saving and loading
- Real-time status monitoring

## Permissions Required

- `SYSTEM_ALERT_WINDOW` - For overlay functionality
- `FOREGROUND_SERVICE` - For background service operation
- `READ_EXTERNAL_STORAGE` - For accessing gallery images
- Accessibility Service - For performing automated clicks

## Note

This is the initial project setup. Individual features will be implemented in subsequent tasks according to the implementation plan.