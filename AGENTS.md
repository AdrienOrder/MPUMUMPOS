# Mobile App - Project Summary

## Current Status
✅ **BUILD SUCCESSFUL** - Project compiles and runs correctly.

## Project Structure
- **Activity Count**: 3 (`MainActivity`, `LogActivity`, `DeviceListActivity`)
- **Manager Classes**: 3 (`BluetoothManager`, `DialogManager`, `LogManager`)
- **MainActivity.kt**: 355 lines (refactored to use managers)
- **LogActivity.kt**: 123 lines (updated to use LogManager)
- **DeviceListActivity.kt**: 209 lines (unchanged)
- **BluetoothManager.kt**: 120 lines (new)
- **DialogManager.kt**: 130 lines (new)
- **LogManager.kt**: 40 lines (new)

## Features Implemented
1. **Bluetooth Communication** (in BluetoothManager)
   - Connect/Disconnect HC-05 device
   - Send commands (SET interval, SET start, SET time, GET data)
   - Reading thread for incoming data
   - Permission handling for Android 12+

2. **UI/UX** (in MainActivity and DialogManager)
   - App Icon: Blue gradient (#0D47A1 to #1E88E5) with white foreground
   - Dialog buttons centered
   - Time picker with 5 NumberPickers (Hours:Minutes | Day.Month.Year)
   - Bottom navigation (Logs, Home, Files)
   - Button labels: "Очистить логи", "Скопировать логи"
   - Button height increased for two-line text

3. **Dialogs** (in DialogManager)
   - Edit parameters confirmation
   - Sync time confirmation
   - Log clearing confirmation
   - All dialogs have centered blue buttons

4. **State Management** (in MainActivity)
   - Connection state tracking
   - Edit mode for parameters
   - Previous values stored for cancel operation

5. **Log Management** (in LogManager)
    - Centralized log storage
    - Real-time updates to LogActivity
    - Clipboard copy functionality
    - **Detailed logging of all Bluetooth events:**
        - Connection attempts and status
        - Commands sent (SET/GET)
        - Data received from device
        - Error messages
        - Settings synchronization

## Technical Details
- **Gradle**: 8.2
- **AGP**: 8.1.1
- **Kotlin**: 1.9.0
- **JDK**: 17 (or higher)
- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)

## Known Deprecation Warnings
- `BluetoothAdapter.getDefaultAdapter()` - deprecated in Android 13
- `startActivityForResult()` - replaced by Activity Result API
These are expected and don't affect functionality.

## Files Modified
- `app/src/main/kotlin/com/mobileapp/MainActivity.kt` (refactored)
- `app/src/main/kotlin/com/mobileapp/LogActivity.kt` (updated)
- `app/src/main/kotlin/com/mobileapp/BluetoothManager.kt` (new)
- `app/src/main/kotlin/com/mobileapp/DialogManager.kt` (new)
- `app/src/main/kotlin/com/mobileapp/LogManager.kt` (new)
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/colors.xml` (cleaned up: fixed duplicates, removed unused colors)
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_logs.xml`
- `app/src/main/res/drawable/button_background.xml`
- `app/src/main/res/drawable/button_background_rounded.xml`
- `app/src/main/res/drawable/rounded_bottom_navigation.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`

## Build Commands
```bash
# Build debug APK
export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew assembleDebug

# Build release APK
export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew assembleRelease

# Clean build
export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew clean assembleDebug
```

## Running the App
1. Ensure Bluetooth is enabled on the Android device
2. Pair with HC-05 module (default password: 1234)
3. Run the app and tap "ПОДКЛЮЧИТЬ"
4. Select the HC-05 device from the list
5. Use the interface to edit parameters and sync time

## Notes
- The project has been refactored from a "God Activity" pattern to a more modular structure
- Business logic is now separated into manager classes
- All strings and colors are extracted to resources for easy localization
- **All colors in Kotlin files reference `R.color.*` from `colors.xml`** - no hardcoded colors
- The app is fully functional and ready for use with HC-05 Bluetooth modules

## Refactoring Summary
The original `MainActivity.kt` (547 lines) has been refactored into:
1. **BluetoothManager.kt**: Handles all Bluetooth operations
2. **DialogManager.kt**: Handles all dialog creation and display
3. **LogManager.kt**: Handles log storage and display
4. **MainActivity.kt**: Now focuses on UI coordination and state management (355 lines)

This separation makes the code more maintainable and easier to understand while keeping all functionality intact.
