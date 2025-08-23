# 🎹 Melodigram

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

An interactive **MIDI file player and visualizer**, built with Java.  
Watch musical pieces come to life with an animated piano display — or connect your own MIDI keyboard to learn new pieces.  

---

## 🎥 Demo

![Melodigram Demo](https://github.com/user-attachments/assets/d9eed213-ef31-4631-b8b9-b2bb5e266267)

---

## 📖 About The Project

I’ve always wanted to create an **open-source, free alternative to Synthesia**.  
This project aims to be a tool for people who have no musical background and want to start learning piano.  

---

## ✨ Features

- **MIDI Playback** – Load and play any standard `.mid` file  
- **Real-time Visualization** – Notes light up on a virtual piano as they are played  
- **Waterfall Animation** – Animated falling notes for easier practice  
- **Live MIDI Input** – Connect your own MIDI keyboard/controller to learn interactively  
- **Playback Controls** – Functional seek bar with full playback control  

---

## 🚀 Getting Started

This application is self-contained and does not require you to install Java separately.

1.  Go to the [**Releases Page**](https://github.com/Tbence132545/Melodigram/releases).
2.  Download the `release.zip` file from the latest release.
3.  Unzip the downloaded file.
4.  Run `Melodigram.exe` from inside the unzipped folder.

---

## 🛠️ Building from Source (for Developers)

If you want to build the project yourself, follow these steps.

### Prerequisites

* JDK 21 or newer.
* Git.

### Installation

1.  **Clone the repository:**
    ```sh
    git clone [https://github.com/Tbence132545/Melodigram.git](https://github.com/Tbence132545/Melodigram.git)
    ```
2.  **Navigate to the project directory:**
    ```sh
    cd Melodigram
    ```
3.  **Build the project using the Gradle wrapper:**
    * On Windows:
        ```sh
        .\gradlew build
        ```
    * On Mac/Linux:
        ```sh
        ./gradlew build
        ```
4.  **Package the application:**
    After building, you can create the runnable application using the `jpackage` command:
    ```cmd
    jpackage --type app-image --name "Melodigram" --input "build/libs" --main-jar "Melodigram.jar" --main-class "com.Tbence132545.Melodigram.Main" --dest "release"
    ```

---

## 💻 Technologies Used

* [Java](https://www.java.com/)
* [Java Swing](https://docs.oracle.com/javase/tutorial/uiswing/) (for the graphical user interface)
* [Gradle](https://gradle.org/) (for dependency management and building)

---

## 📄 License

This project is distributed under the MIT License. See the `LICENSE` file for more information.
