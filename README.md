# Prism

Android social-news app (Kotlin + Jetpack Compose + Firebase). Social MVP: accounts, interests, Home/Interests feeds, article likes, friends, and DMs (**text + article shares**). No Play Store required — install via APK.

**Storage / image DMs are deferred** so you can stay on Firebase’s free Spark plan (Storage often requires Blaze). Easy to add later.

## What you need to do (Firebase)

Do these once so the app can talk to your backend:

### 1. Create a Firebase project
1. Open [Firebase Console](https://console.firebase.google.com/)
2. Click **Add project** → name it `Prism` (or similar) → continue
3. You can disable Google Analytics for now

### 2. Add an Android app
1. In the project overview, click the **Android** icon
2. Android package name: `com.prism.app` (must match exactly)
3. Register app → download **`google-services.json`**
4. Replace `app/google-services.json` in this repo with your downloaded file

### 3. Enable Email/Password Auth
1. Firebase Console → **Build** → **Authentication** → **Get started**
2. Sign-in method → **Email/Password** → Enable → Save

### 4. Create Firestore
1. **Build** → **Firestore Database** → Create database
2. Location: pick closest region (e.g. `nam5` / United States)
3. Start in **test mode** for first bring-up, then publish rules from `firebase/firestore.rules` (Firestore → Rules → paste → Publish)

### 5. Seed starter articles
1. Firestore → Start collection `articles`
2. For each object in `seed/articles.json`, add a document (auto-ID) with those fields
3. Field types: strings for text fields, array for `interests`, number for `createdAt`

Composite indexes: Home/Interests sort on the **database** (`where` + `orderBy`). Create these once in Firebase:

1. Firebase Console → **Firestore** → **Indexes** → **Composite** → **Create index**
2. Add these two for collection `articles`:

| Fields | Query scope |
|---|---|
| `scope` Ascending, `createdAt` Descending | Collection |
| `interests` Arrays, `createdAt` Descending | Collection |

3. Wait until status is **Enabled** (can take a few minutes)

Also create this index so DMs load after accepting a friend:

| Collection | Fields | Query scope |
|---|---|---|
| `conversations` | `participantIds` Arrays, `updatedAt` Descending | Collection |

Definitions are also in `firebase/firestore.indexes.json`. After Run, if Logcat shows a Firebase index URL, you can click that link to create the index automatically.

Optional later: Storage (image DMs)
Only when you want photo messages (often requires Blaze pay-as-you-go; free quotas usually cover demos):
1. **Build** → **Storage** → Get started
2. Publish rules from `firebase/storage.rules`
3. In the app, set `EnableChatImages = true` in `ChatScreen.kt` (or ask me to turn it on)

## Run on your machine

1. Install [Android Studio](https://developer.android.com/studio) (includes Android SDK + emulator)
2. Open this folder (`c:\dev\Prism`) in Android Studio
3. Let Gradle sync finish
4. Plug in a phone (USB debugging on) or start an emulator
5. Click **Run**

### Build an installable APK
In Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**  
APK path is usually `app/build/outputs/apk/debug/app-debug.apk`.

On your phone: allow install from that source, open the APK, install.

## Project map

- `app/src/main/java/com/prism/app/ui/` — screens
- `app/src/main/java/com/prism/app/data/` — Firebase repositories + models
- `firebase/` — security rules
- `seed/articles.json` — starter news cards

## Deferred (v2)

Image DMs (Firebase Storage), voice calls, GIFs, article comment threads, Play Store listing, automated news ingest.
