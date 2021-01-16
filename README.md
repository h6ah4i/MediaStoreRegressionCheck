This repository is a demo project for reproduction of the following MediaStore issue.
https://issuetracker.google.com/issues/177167015

---

:point_right: On API level 30 (Android 11), the result is different from former Android versions.

| API Level 28 | API Level 29 | API Level 30 |
|---|---|---|
| <a href="./pics/API28Result.png?raw=true"><img src="./pics/API28Result.png?raw=true" alt="on API Level 28" /></a> | <a href="./pics/API29Result.png?raw=true"><img src="./pics/API29Result.png?raw=true" alt="on API Level 29" /></a> | <a href="./pics/API30Result.png?raw=true"><img src="./pics/API30Result.png?raw=true" alt="on API Level 30" /></a> |


---


### How to check?

1. Clone this repository
2. Open the project with Android Studio
3. Build and install to Device or Emulator
4. Press the "1. SETUP MEDIASTORE" button
    - :point_right: Copy some audio files to the device storage and register them to MediaStore
5. Press the "2. QUERY MEDIASTORE" button
    - :point_right: Query MediaStore and print the result to the TextView

### Test audio files

| File name | Title | Album | Artist |
|---|---|---|---|
| `track 1.mp3` | Track 01 | Album 01 | Artist **A** |
| `track 2.mp3` | Track 02 | Album 01 | Artist **A** |
| `track 3.mp3` | Track 03 | Album 01 | Artist **B** |
| `track 4.mp3` | Track 04 | Album 01 | Artist **B** |
| `track 5.mp3` | Track 05 | Album 01 | Artist **B** |
| `track 6.mp3` | Track 06 | Album 01 | Artist **C** |
