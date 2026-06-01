# Nova Calculator

An open-source Android scientific calculator application with a hidden feature set. On the surface, Nova Calculator functions entirely as a standard, fully-featured calculator. However, it contains a highly-concealed "Secret Code Trigger System" designed for specific use cases.

## Features

*   **Fully Functional Calculator:** Provides a complete set of scientific calculator features, serving its primary purpose without compromise.
*   **Secret Code Trigger System:** Users can enter specific numeric sequences during normal calculations to seamlessly trigger background events without disrupting the calculation progress.
*   **Dynamic Suffix Interception:** Replaces traditional "equals sign triggers" or "exact text matches" with dynamic suffix interception (`endsWith`). The secret code works even amidst complex calculations (e.g., `1500 * 30...`), making it extremely natural.
*   **Accidental Touch Prevention:**
    *   **Backspace Immunity:** Prevents triggering if the code is formed by deleting characters.
    *   **Startup Shield:** Ignores codes formed during the loading of calculation history on app startup.
*   **Emergency Stop & Status Indicator:**
    *   Specific actions change the color of the "Backspace" button subtly to indicate active background processes.
    *   A single tap on the Backspace button instantly stops the background process, saves any necessary files, and restores the button color.
*   **Power Optimization & Screen Management:**
    *   `FLAG_KEEP_SCREEN_ON` is enforced to prevent the screen from locking while the app is foregrounded.
    *   Auto-dimming feature drops screen brightness to near zero after 2 minutes of inactivity, saving power while keeping the app active. Touching the screen instantly restores brightness.

## Hardware & Permission Handling

*   **Silent Shutter Strategy:** Attempts to mute the system volume to `0` before capturing media to ensure silence. Includes fail-safes (e.g., `try-catch` blocks) to prevent app crashes on strict OEM skins (like Huawei or Android 10+ devices) if volume modification is blocked by system policies.
*   **CameraX Integration:** Utilizes AndroidX CameraX for seamless capture capabilities with minimal visible UI elements.

## Architecture

This project is built using:
*   Java / Kotlin
*   AndroidX CameraX
*   Otto Bus (with reflection-based subscriber discovery to ensure event delivery)
*   Dagger for Dependency Injection

## Building the Project

1.  Clone this repository.
2.  Open the project in Android Studio.
3.  Sync Gradle dependencies.
4.  Build and run the project on an Android device or emulator.

## License

This project is released under the [Apache License 2.0](LICENSE).
