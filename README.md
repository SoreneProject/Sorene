# Sorene

[![Version](https://img.shields.io/badge/version-0.5.8-blue.svg)](https://github.com/SoreneProject/Sorene/releases)
[![License](https://img.shields.io/badge/license-GPLv3-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-5.0%2B-blue.svg)](https://www.android.com)

Sorene is an Android application that provides QR code scanning and generation capabilities, along with file management features.

## Features

- QR Code scanning and generation
- File management and handling
- Modern Android UI
- Support for Android 5.0 (API level 24) and above

## Requirements

- Android 5.0 (API level 24) or higher
- Internet connection for certain features

## Installation

1. Download the latest release APK from the [Releases](https://github.com/SoreneProject/Sorene/releases) page
2. Install the APK on your Android device
3. Grant necessary permissions when prompted

## Building from Source

### Prerequisites

- Android Studio (latest version recommended)
- Android SDK with API level 30
- JDK 8 or higher

### Build Steps

1. Download and install [Android Studio](https://developer.android.com/studio)

2. Clone the repository:
   - Open Android Studio
   - Click on "File" → "New" → "Project from Version Control"
   - Enter the repository URL: `https://github.com/SoreneProject/Sorene.git`
   - Click "Clone"

3. Wait for the project to sync and download dependencies

4. Build the project:
   - Click on "Build" → "Make Project" (or press Ctrl+F9)
   - Wait for the build to complete

5. Run the app:
   - Connect an Android device or start an emulator
   - Click on "Run" → "Run 'app'" (or press Shift+F10)
   - Select your target device and click "OK"

The debug APK will be generated at `app/build/outputs/apk/debug/Sorene-0.5.8.apk`

## Dependencies

- androidx.documentfile:documentfile:1.0.1
- org.apache.commons:commons-io:1.3.2
- androidx.core:core:1.6.0
- com.journeyapps:zxing-android-embedded:4.3.0
- com.google.zxing:core:3.4.1

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
