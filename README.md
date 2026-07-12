# AutoSilent

AutoSilent is a modern, intuitive Android application designed to automatically manage your phone's ringer modes based on your daily routine. Whether you're in a meeting, at school, or sleeping, AutoSilent ensures your phone stays quiet when it needs to be.

## 🚀 Features

### 📅 Smart Scheduling
- Create multiple schedules with custom start and end times.
- Choose between **Silent**, **Vibrate**, or **Do Not Disturb** modes.
- Select specific days of the week for each schedule.
- Set custom media volume levels for each silent period.

### ⚡ Quick Silence (New!)
- Instantly silence your phone for a custom duration (e.g., 15 mins, 1 hour).
- Real-time countdown timer displayed on the dashboard.
- Automatically restores your previous ringer mode once the timer expires.

### 🎨 Modern UI/UX
- **iOS-inspired Design:** Clean, card-based interface using Material Components.
- **Dynamic Dashboard:** View your current ringer mode and active timers at a glance.
- **Dark Mode:** Built-in toggle to switch between Light and Dark themes seamlessly.

### 🛠 System Integration
- **DND Access:** Smooth handling of "Do Not Disturb" permissions.
- **Battery Optimization:** Guidance to ensure schedules run reliably on all devices.
- **Boot Recovery:** Automatically re-schedules all alarms after a phone reboot.

## 📸 Screenshots
![Dashboard](AutoSilent.jpeg)

## 🛠 Tech Stack
- **Language:** Kotlin
- **Architecture:** MVVM (Clean Code principles)
- **UI:** ViewBinding, Material 3, CoordinatorLayout
- **Background Tasks:** AlarmManager, BroadcastReceivers
- **Storage:** SharedPreferences (JSON Serialization)

## 📦 Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/middepremkumar/AutoSilent.git
   ```
2. Open the project in **Android Studio**.
3. Build and run on your device (Target API 34, Min API 24).

## 📄 License
This project is licensed under the MIT License.
