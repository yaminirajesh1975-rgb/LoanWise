# LoanWise - Smart Loan EMI & Prepayment Advisor

LoanWise is a banking-grade Android application designed for loan management, EMI calculation, and smart prepayment optimization.

This repository features **Custom Backend-Generated Email OTP Authentication** using **Resend** for email delivery and **Firebase Firestore** for user profiles and loan data.

---

## 🏗️ Architecture Overview

The system consists of two parts:
1. **Android Application (`app/`)**: Native Android (Java) frontend providing the user interface, loan calculators, and Firebase Firestore integration.
2. **Authentication Backend (`backend/`)**: Node.js/Express service that generates cryptographically secure 6-digit OTPs, hashes them with SHA-256 and server-side salt, enforces rate limits, delivers emails via the **Resend API**, and issues signed JWT session tokens upon verification.

---

## 🔐 Authentication Flow

```
[ Android App ]                                      [ Node.js Backend ]                     [ Resend API ]
      |                                                      |                                     |
      |--- 1. POST /auth/send-otp { email } --------------->|                                     |
      |                                                      |-- 2. Validate email & cooldown     |
      |                                                      |-- 3. crypto.randomInt(100000, ...)  |
      |                                                      |-- 4. SHA-256 Salted Hash stored    |
      |                                                      |-- 5. Send Email via Resend -------->|
      |<-- 6. 200 OK { success: true } ----------------------|<-- (Resend dispatches OTP email) --|
      |                                                      |
[ User enters 6-digit OTP ]                                  |
      |                                                      |
      |--- 7. POST /auth/verify-otp { email, otp } --------->|
      |                                                      |-- 8. Verify attempt count (< 5)    |
      |                                                      |-- 9. Timing-safe SHA-256 hash match|
      |                                                      |-- 10. Mark OTP as used (single-use)|
      |                                                      |-- 11. Sign JWT Token               |
      |<-- 12. 200 OK { token, user } -----------------------|
      |
[ Store JWT Session + Firestore Sync ]
      |
[ Open MainActivity ]
```

---

## 🚀 1. How to Start the Backend

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Set up environment variables:
   ```bash
   cp .env.example .env
   ```
4. Start the server:
   ```bash
   npm start
   ```
   The backend will run on `http://0.0.0.0:5001`. Health check endpoint: `http://localhost:5001/health`.

---

## 🔑 2. How to Configure RESEND_API_KEY

1. Sign up for a free account at [https://resend.com](https://resend.com).
2. Go to **API Keys** and generate a new API key (`re_...`).
3. Open `backend/.env` in your text editor:
   ```env
   PORT=5001
   RESEND_API_KEY=re_your_actual_key_here
   EMAIL_FROM=LoanWise <onboarding@resend.dev>
   JWT_SECRET=your_long_random_jwt_secret_phrase
   OTP_EXPIRY_MINUTES=5
   MAX_OTP_ATTEMPTS=5
   RESEND_COOLDOWN_SECONDS=60
   ```
   *(Note: Resend's free tier default sender `onboarding@resend.dev` delivers to the email address registered with your Resend account).*

---

## 📱 3. How to Run the Android App

### Option A: Install Generated APK Directly
Install the generated release APK onto an Android device or emulator via adb:
```bash
adb install -r LoanWise-release.apk
```

### Option B: Build & Run from Source
1. Open the project in Android Studio.
2. Select an Android Virtual Device (AVD) or physical phone.
3. Click **Run 'app'** (or build via command line: `./gradlew assembleDebug` or `./gradlew assembleRelease`).

### Configuring Backend Server URL on the Phone
The app connects by default to the production Vercel serverless backend:
* **Production Endpoint (Default)**: `https://loan-wise-sigma.vercel.app`
* **Local Development Override**: In the app's login screen, tap **"Server Settings"** to enter `http://10.0.2.2:5001` (Android Emulator) or your computer's local Wi-Fi IP address (e.g. `http://192.168.1.15:5001`) for testing local changes.

---

## 🧪 4. How to Test the OTP Flow

### Automated Test Suite
You can verify all 10 security constraints and endpoints in one command:
```bash
cd backend
npm test
```
This tests:
* ✅ Health endpoint (200 OK)
* ✅ Email syntax validation
* ✅ 6-digit OTP generation and hashing
* ✅ 60-second resend rate-limit cooldown
* ✅ Rejection of invalid OTP format
* ✅ Rejection of wrong OTP codes
* ✅ Successful OTP verification & JWT generation
* ✅ Single-use enforcement (replay attack prevention)
* ✅ JWT session token verification (`/auth/me`)
* ✅ Max 5 attempt lockout protection

### Manual End-to-End Test
1. Start the backend: `npm start`
2. Open LoanWise on your device.
3. Enter your email address and tap **"Send OTP"**.
4. Check your email for:
   * **Subject**: `LoanWise Login OTP`
   * **Content**: 6-digit verification code
5. Enter the code and tap **"Verify OTP"**.
6. The app verifies the OTP with the backend, receives a JWT, syncs profile to Firestore, and opens `MainActivity`.

---

## 🛡️ 5. Security Practices: What Must NEVER Be Committed

The following files and secrets are strictly excluded in `.gitignore` and **must never be committed**:
1. `backend/.env` (contains `RESEND_API_KEY`, `JWT_SECRET`).
2. Any personal API keys, tokens, or passwords.
3. Android keystores and private release certificates.
4. `local.properties` (contains local SDK paths).
5. Build output directories (`app/build/`, `build/`, `.gradle/`).

Always use `.env.example` as a template for team members to create their own local `.env`.

---

## 🌐 6. Deploying the Backend to Vercel

### Option 1: Via Vercel Web Dashboard (Recommended)
1. Push your repository to GitHub.
2. Sign in to [vercel.com](https://vercel.com) using `yaminirajesh1975@gmail.com`.
3. Click **"Add New..." → "Project"** and import the `LoanWise` repository.
4. Under **Project Settings**:
   * Set **Root Directory** to `backend`.
5. Under **Environment Variables**, add:
   * `RESEND_API_KEY`: Your key from Resend (`re_...`)
   * `EMAIL_FROM`: `LoanWise <onboarding@resend.dev>`
   * `JWT_SECRET`: A long random secret string
   * `OTP_EXPIRY_MINUTES`: `5`
   * `MAX_OTP_ATTEMPTS`: `5`
   * `RESEND_COOLDOWN_SECONDS`: `60`
6. Click **Deploy**.
7. Once deployment is complete, test: `https://<your-project>.vercel.app/health` returns `{"status":"ok"}`.

### Option 2: Via Vercel CLI
```bash
cd backend
npx vercel login
npx vercel --prod
```

