# Hush – Flip to Focus

A minimal Android app that helps you focus using a simple physical interaction:

Flip your phone face down to start a focus session.

---

## Concept

Hush is built around zero-friction productivity.

No buttons. No clutter.
Just flip your phone and enter focus mode.

---

## Features

* Flip-to-start focus mode using device sensors
* Focus and break timer system
* Automatic silent mode (Do Not Disturb)
* Clean landscape UI
* Session tracking (custom subjects like Math, Coding)
* Stopwatch (work in progress)

---

## Screenshots

### Idle Screen

![Idle](screenshots/idle.png)

### Focus Mode

![Focus](screenshots/focus.png)

### Break Mode

![Break](screenshots/break.png)

---

## How to Use

1. Open the app
2. Set your focus duration using the slider
3. Select a focus block (e.g., Math, Coding)
4. Place your phone face down
5. Focus session starts automatically
6. After completion, break session begins
7. Log your session

---

## Installation

### Run via Android Studio

Clone the repository:

```bash
git clone https://github.com/arcanys7/hush-focus-timer-android.git
```

Open the project in Android Studio, let Gradle sync, and run the app on an emulator or physical device.

---

## Requirements

* Android Studio (latest recommended)
* Minimum SDK: 26
* Kotlin with Jetpack Compose

---

## What I Learned

* Jetpack Compose UI
* Android sensors (flip detection)
* State management with ViewModel
* Coroutines for timer logic
* Room database for session tracking

---

## Known Issues

* Flip detection can be improved
* Stopwatch feature is incomplete
* UI can be refined

---

## Future Improvements

* Improve flip detection reliability
* Add statistics dashboard
* UI and UX improvements
* Performance optimization

---

## Contributing

Suggestions and improvements are welcome.
Feel free to fork the repository or open an issue.

---

## License

This project is licensed under the MIT License.
