# 💰 Intelligent AI-Powered Android Expense Tracker

An intelligent, reactive native Android application built using **Kotlin**, **Jetpack Compose**, and **Material 3** that automates personal financial tracking through zero-click background notification parsing, provides an interactive human-in-the-loop staging pipeline, optical receipt scanning, and features a conversational financial assistant powered by **Google Gemini AI (`gemini-3.5-flash`)**.

---

## 🌟 Key Features

* **⚡ Automated Real-Time Transaction Capture:**
  * Uses Android's `NotificationListenerService` to passively detect and parse payment alerts with zero manual entry friction.
  * Intercepts and parses alerts from popular UPI applications (**Google Pay**, **PhonePe**, **Paytm**, **BHIM**, **NPCI**), default SMS apps (**Google Messages**, **Samsung Messages**, **Android MMS**), and **Truecaller**.
  * **Package-Agnostic Bank Signature Detection:** Uses a dual-keyword heuristic (`debited`, `credited`, `spent`, `withdrawn` + `a/c`, `account`, `bal`) to detect bank transaction alerts while ignoring casual chats.
  * **Grouped SMS Thread Support:** Inspects `android.textLines` to reliably parse subsequent transactions bundled in the same messaging thread.

* **🔄 Smart Income vs. Expense Classification:**
  * Distinguishes incoming credits from outgoing expenditures automatically.
  * Direct payments received via GPay (*"paid you"*, *"credited"*) are marked as **Income** (🟢 Green, `+₹`).
  * Direct payments sent or debits (*"debited"*, *"spent"*) are marked as **Expenses** (🔴 Red, `-₹`).

* **🛡️ Human-in-the-Loop Review Pipeline:**
  * Auto-captured transactions are safely staged in a `pending_expenses` Room table with a badge counter notification on the Dashboard.
  * Users can review, adjust categories, edit amounts, add personal notes, or discard non-transaction alerts before committing to the permanent ledger.

* **🤖 AI Financial Advisory (Google Gemini 3.5 Flash):**
  * **Conversational AI Assistant:** Chat naturally about your spending habits (*"How much did I spend on Amazon?"*, *"What was my biggest expense this month?"*).
  * **Smart Spending Insights:** Automated financial tips and spending breakdown generated directly by the Gemini LLM.
  * **Offline Rule-Based Fallback:** Instant local category mapping when offline.

* **🧾 Optical Receipt Scanner:**
  * Capture paper receipts using the device camera or gallery.
  * Multi-modal AI processing extracts the merchant name, total amount, category, and date automatically.

* **📊 Modern Reactive Dashboard:**
  * **Time-Aware Dynamic Greeting:** Updates greetings based on local device time (*Good Morning*, *Good Afternoon*, *Good Evening*, *Good Night*).
  * **Dynamic User Identity:** Personalizes the dashboard with the logged-in user's name saved via `SharedPreferences`.
  * **Live Balance Computations:** Instant mathematical updates to Total Income, Total Expenses, and Remaining Balance.
  * **Monthly Budget Tracking:** Configurable budget progress bars with real-time threshold indicators.

---

## 🏗️ System Architecture

The application is engineered adhering to **Modern Android Architecture (MVVM - Model-View-ViewModel)** with Clean Architecture principles:

* **Presentation Layer:** 100% declarative UI built with **Jetpack Compose** and **Material 3**. Single Activity Architecture (`MainActivity`) with Compose Navigation (`NavHost`).
* **ViewModel Layer:** `ExpenseViewModel` exposes state using Kotlin Coroutines and reactive `StateFlow` streams.
* **Repository Layer:** `ExpenseRepository` centralizes and dispatches data operations.
* **Local Data Layer:** **Room Database (SQLite)** with `ExpenseDao` and `PendingExpenseDao` for offline-first persistence, plus `SharedPreferences` for user session and budget preferences.
* **Background Service Layer:** `MyNotificationListenerService` runs as an asynchronous background listener daemon.
* **Cloud AI Layer:** `AiService` integrates the Google Generative AI Android SDK communicating with `gemini-3.5-flash`.

---

## 🛠️ Tech Stack & Tools

* **Language:** [Kotlin](https://kotlinlang.org/) (1.9+)
* **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) & [Material 3](https://m3.material.io/)
* **Architecture:** MVVM + Repository Pattern + Unidirectional Data Flow (UDF)
* **Concurrency:** Kotlin Coroutines & Flow (`StateFlow`, `collectAsState`)
* **Local Database:** [Android Jetpack Room](https://developer.android.com/training/data-storage/room) (SQLite)
* **AI / LLM Engine:** [Google Generative AI Client SDK](https://ai.google.dev/) (`gemini-3.5-flash`)
* **Image Loading:** [Coil Compose](https://coil-kt.github.io/coil/)
* **Build System:** Gradle with Android Gradle Plugin (AGP) and KSP (Kotlin Symbol Processing)

---

## 🚀 Getting Started & Installation

### Prerequisites
* **Android Studio** (Hedgehog | 2023.1.1 or newer recommended)
* **JDK 17** (or JetBrains Runtime 17+)
* **Android Device or Emulator** running **Android 8.0 (API 26)** or higher
* A **Google Gemini API Key** (obtain free from [Google AI Studio](https://aistudio.google.com/))

### Setup Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/akilesh464/Expense-Tracker.git
   cd Expense-Tracker
   ```

2. **Configure `local.properties`:**
   In the root directory of the project, create or open `local.properties` and add your Android SDK path and Gemini API Key:
   ```properties
   sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
   GEMINI_API_KEY=YOUR_ACTUAL_GEMINI_API_KEY
   ```

3. **Open in Android Studio:**
   * Open Android Studio -> **File** -> **Open...** -> select the `ExpenseTracker` folder.
   * Allow Gradle to sync dependencies.

4. **Run the Project:**
   * Select your target device or emulator.
   * Click **Run (Shift + F10)**.

---

## 🔒 Permissions & Setup on Device

To enable automatic transaction detection:
1. When prompted on first launch (or in device **Settings** -> **Apps** -> **Special App Access** -> **Notification Access** / **Device & App Notifications**), enable notification access for **Expense Tracker**.
2. For testing notifications without real transactions, go to **Profile** -> **🛠️ Developer Testing** and click any of the simulation buttons (Starbucks, Amazon, ABC Store, P2P Transfer).

---

## 📂 Project Directory Structure

```text
app/src/main/java/com/example/expensetracker/
├── data/
│   ├── database/       # Room Database (ExpenseDatabase)
│   ├── dao/            # Data Access Objects (ExpenseDao, PendingExpenseDao)
│   ├── entity/         # Data Entities (Expense, PendingExpense)
│   ├── repository/     # Data Repository (ExpenseRepository)
│   └── service/        # Gemini AI Client Service (AiService)
├── service/            # Android NotificationListenerService (MyNotificationListenerService)
├── ui/
│   ├── analytics/      # Financial Analytics & Spending Breakdown
│   ├── chat/           # Gemini AI Assistant Chat Screen
│   ├── dashboard/      # Main Dashboard, Balance & Quick Actions
│   ├── login/          # Modern Authentication Screen
│   ├── pending/        # Pending Transactions Staging Screen
│   ├── profile/        # User Profile & Developer Notification Simulator
│   ├── receipt/        # Optical Receipt Scanner Preview Screen
│   ├── theme/          # Material 3 Colors, Shapes, and Typography
│   └── transactions/   # Complete Transaction History Screen
├── viewmodel/          # MVVM ViewModels & Factory (ExpenseViewModel)
└── MainActivity.kt     # Single Activity Entry Point & Compose Navigation
```

---

## 📄 License

This project is developed as part of academic Project-Based Learning (PBL) / Mobile Application Development coursework. Distributed under the MIT License.
