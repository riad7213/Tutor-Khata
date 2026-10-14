# 📚 Tutor Khata (টিউটর খাতা)

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(M3)-4285F4.svg?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Backend](https://img.shields.io/badge/Cloud%20Sync-Firebase%20Firestore-FFA000.svg?style=flat&logo=firebase&logoColor=white)](https://firebase.google.com/)
[![License](https://img.shields.io/badge/License-Proprietary-blue.svg)](#license)

**Tutor Khata (টিউটর খাতা)** is an all-in-one digital tuition ledger and student attendance tracking system built natively for Android. Designed specifically for private tutors, home instructors, and coaching centers, it replaces paper notebooks with an automated, transparent, and accurate accounting engine tailored for Bangladeshi Taka (৳ BDT) billing workflows.

---

## 📑 Table of Contents

- [Overview & Value Proposition](#overview--value-proposition)
- [Core Features](#core-features)
- [Comprehensive User Guide](#comprehensive-user-guide)
  - [1. Adding and Managing Students](#1-adding-and-managing-students)
  - [2. Logging Classes & Attendance](#2-logging-classes--attendance)
  - [3. Recording Tuition Payments](#3-recording-tuition-payments)
  - [4. Reading the Ledger & Monthly Statements](#4-reading-the-ledger--monthly-statements)
  - [5. Sending WhatsApp & SMS Due Reminders](#5-sending-whatsapp--sms-due-reminders)
  - [6. Data Backup, Export & Cloud Sync](#6-data-backup-export--cloud-sync)
  - [7. Trash & Recovery Bin](#7-trash--recovery-bin)
- [Financial Calculation Engine](#financial-calculation-engine)
- [Architecture & Tech Stack](#architecture--tech-stack)
- [Installation & Getting Started](#installation--getting-started)
- [Security & Privacy](#security--privacy)

---

## 🌟 Overview & Value Proposition

Private tutors often juggle multiple students across different schedules, rates, and billing models. Traditional paper diaries frequently lead to:
- Disagreements over how many classes took place in a given month.
- Missed or unrecorded partial cash or mobile wallet payments.
- Awkward or time-consuming manual calculation of due reminders.

**Tutor Khata** eliminates these friction points with an immutable class-by-class audit trail, automatic rate calculation (flat or duration-based), real-time due tracking, and one-tap formatted payment reminders.

---

## 🚀 Core Features

- **🎓 Smart Student Directory**: Maintain full student profiles including name, guardian information, contact numbers, grade, subject, and customized fee structures.
- **⏱️ Flexible Class Logging**: Log sessions with custom dates, held/cancelled statuses, duration, and notes. Supports both **fixed-rate per class** and **hourly/duration billing**.
- **💳 Multi-Channel Payment Tracking**: Record payments received via **Cash**, **bKash**, **Nagad**, **Bank Transfer**, or other local channels.
- **📊 Real-Time Financial Ledger**: Instant calculations of Total Billed, Total Received, and Current Balance (distinguishing between **Due**, **Settled**, and **Advance Credit**).
- **📅 Monthly Statements**: View month-by-month financial breakdowns with prior balance carry-forward, session lists, payments, and closing balances.
- **📲 One-Tap Due Reminders**: Send professional, polite WhatsApp or SMS messages with dynamic placeholders (`{name}`, `{month}`, `{amount}`, `{classCount}`).
- **☁️ Cloud Sync & Offline Support**: Seamless cloud backup via Firebase Firestore and Google Sign-In, coupled with an offline-first local cache.
- **💾 JSON Backup & Restore**: Export full data snapshots to JSON for local backups or device migration.
- **🗑️ Trash / Recycle Bin**: Soft-delete system allows easy recovery of accidentally deleted students, logs, or payments.

---

## 📖 Comprehensive User Guide

### 1. Adding and Managing Students

1. Open the **Students** tab or tap **Add Student** from the Dashboard.
2. Enter the student's details:
   - **Student Name**: Required (e.g., *Tanvir Ahmed*).
   - **Phone Number**: Primary contact for reminders and calls.
   - **Guardian Name & Contact**: Useful for younger students.
   - **Subject & Grade**: e.g., *Physics — Class 10*.
3. Choose the **Billing Mode**:
   - **Flat Rate per Class**: Enter the fee charged per class (e.g., `৳500`).
   - **Duration / Hourly Billing**: Enable this toggle if tuition is billed based on session duration.
4. Tap **Save Student**. The student now appears with a real-time balance badge (**Due**, **Clear**, or **Advance**).

> **Tip**: You can archive completed students from their profile. Archived profiles retain all historical records without cluttering your active roster.

---

### 2. Logging Classes & Attendance

1. From the **Home** dashboard, tap **Log Class** (or log directly from a student’s profile).
2. Select the **Student**.
3. Choose the **Date** (defaults to today).
4. Select the **Status**:
   - **Held**: Increments the student's class count and adds the class fee to their bill.
   - **Cancelled**: Records the missed session for record-keeping without billing the student.
5. If duration billing is enabled, enter the session length in minutes (e.g., `90` min).
6. Verify the calculated rate (you can manually override it if a discount or extra charge applies to this specific session).
7. Tap **Save Class**.

---

### 3. Recording Tuition Payments

1. On the **Home** or **Payments** tab, tap **Record Payment**.
2. Select the student who made the payment.
3. Enter the **Amount** in BDT (e.g., `৳5,000`).
4. Select the **Payment Method**:
   - 💵 **Cash**
   - 📱 **bKash**
   - 📲 **Nagad**
   - 🏦 **Bank Transfer**
   - 📝 **Other**
5. Add an optional reference note (e.g., *"bKash TrxID: 9X87KL1"*).
6. Tap **Save Payment**. The student's balance updates immediately across the app.

---

### 4. Reading the Ledger & Monthly Statements

Tutor Khata computes the financial status using an automated ledger engine:

| Status Badge | Meaning |
| :--- | :--- |
| 🔴 **Due (বাকি)** | The student owes money for classes held (`Total Billed > Total Paid`). |
| 🟢 **Clear (পরিশোধিত)** | The account has an exact zero balance (`Total Billed == Total Paid`). |
| 🔵 **Advance (অগ্রিম)** | The guardian paid in advance (`Total Paid > Total Billed`). |

**Monthly Statements**:
- Tap on any student and select **Monthly Statement**.
- Select the target month (e.g., `October 2026`).
- Review the carried-forward balance from preceding months, the list of classes held during the month, payments deposited, and the closing balance.

---

### 5. Sending WhatsApp & SMS Due Reminders

Never compose awkward payment reminder texts from scratch:
1. Open the student's detail sheet or locate their card with a **Due** badge.
2. Tap the **Reminder** action icon (or choose SMS / WhatsApp).
3. The app automatically populates your configured template:
   > *"Hello Tanvir Ahmed, this is a gentle reminder that tuition fees for October 2026 (8 classes) amounting to ৳4,000 are due. Thank you!"*
4. Your phone's SMS or WhatsApp app will open with the recipient and text ready to send.

> **Customizing the Template**: Go to **Settings** ➔ **Reminder Template** to modify the wording using placeholders: `{name}`, `{month}`, `{classCount}`, `{currency}`, and `{amount}`.

---

### 6. Data Backup, Export & Cloud Sync

- **Google Cloud Sync**: Sign in with your Google Account in **Settings** to synchronize all records in real time with Firebase Firestore. Your data is backed up automatically and restored if you switch devices.
- **Local JSON Snapshot**:
  - Go to **Settings** ➔ **Export Backup**.
  - Creates a standardized, timestamped JSON snapshot of all students, class logs, payments, and settings.
  - To restore, select **Import Backup** and select your saved JSON file.

---

### 7. Trash & Recovery Bin

- Deleted students, logs, or payment entries are moved to the **Trash** rather than permanently erased.
- Navigate to **Settings** ➔ **Trash** to inspect deleted items.
- Tap **Restore** to bring any item back with its original balances intact, or choose **Empty Trash** to permanently purge.

---

## 🧮 Financial Calculation Engine

Tutor Khata utilizes an exact integer math engine (`LedgerEngine.kt`) to eliminate rounding discrepancies:

$$\text{Total Billed} = \sum_{\text{held}} \text{Session Rate}$$

$$\text{Current Balance} = \text{Total Billed} - \sum \text{Payments}$$

$$\text{Unpaid Class Equivalent} = \frac{\max(0, \text{Current Balance})}{\text{Student Rate Per Class}}$$

---

## 🏗️ Architecture & Tech Stack

```
com.example/
├── auth/           # Google Sign-In & Credential Manager
├── data/           # Firestore Cloud Sync & Offline Local Data Layer
├── model/          # Immutable Domain Models (Student, ClassLog, Payment, Settings)
├── ui/
│   ├── components/ # Reusable M3 Cards, Dialogs, Filter Chips, Bottom Sheets
│   ├── screens/    # Home, Students, Payments, Insights, Settings, Trash
│   └── theme/      # M3 Dynamic Color Schemes, Typography, Shapes
├── utils/          # Date & Format Utilities, Ledger Financial Engine
└── viewmodel/      # Single Source of Truth TuitionLedgerViewModel
```

- **Architecture Pattern**: MVVM (Model-View-ViewModel) + Clean Data Layer
- **UI Framework**: 100% Jetpack Compose with Material Design 3 (M3)
- **Asynchronous Operations**: Kotlin Coroutines & `StateFlow`
- **Data Persistence**: Offline-first caching with bidirectional Firebase Firestore synchronization
- **Authentication**: Jetpack Credential Manager + Firebase Auth (Google Sign-In)

---

## 📥 Installation & Getting Started

### Installing the APK on Android
1. Go to the [Releases](https://github.com) section of this repository.
2. Download the latest `tutor-khata-v1.0.0.apk`.
3. Open the downloaded file on your phone and tap **Install**.
4. *(If asked, grant permission to "Install Unknown Apps" in your browser/file manager settings).*

### Building from Source
Ensure you have **Android Studio Ladybug (or newer)** and **JDK 17+** installed:
```bash
# Clone the repository
git clone https://github.com/<your-username>/<your-repo-name>.git

# Navigate to the project root
cd tutor-khata

# Build the debug APK
./gradlew assembleDebug
```
The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 Security & Privacy

- **Your Records Are Yours**: Your student data, phone numbers, and financial logs are private. They are never shared or made public in repository releases.
- **Zero-Trust Backend**: Cloud-synced data is isolated by user UID via Firebase Security Rules—only you can read or write your own ledger records.
- **No Intrusive Permissions**: Tutor Khata requires zero sensitive hardware permissions (no background location, no storage scraping, no contact-list harvesting).

---

## 📄 License

This software is licensed under the [MIT License](LICENSE) (or proprietary to the project maintainer). All rights reserved.
