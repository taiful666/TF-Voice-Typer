<p align="center"><img src="docs/logo_preview.png" width="160" alt="TF Voice Typer logo"></p>

# টিএফ ভয়েস টাইপার / TF Voice Typer

বাংলায় স্বাভাবিকভাবে কথা বলুন — Google Gemini AI নির্ভুল বাংলা লেখা ও ইংরেজি অনুবাদ তৈরি করে দেবে।
Speak naturally in Bangla — Google Gemini AI turns it into correct Bangla text plus an English translation.

## যা যা আছে (Features)
- **ভাসমান মাইক** — WhatsApp, Messenger, Facebook, Gmail, Chrome সহ যেকোনো অ্যাপের উপরে। টেনে সরানো যায়; নিচের লাল চিহ্নে ছেড়ে দিলে বন্ধ হয়। ট্যাপ = রেকর্ড শুরু, আবার ট্যাপ = থামান।
- **যত লম্বা খুশি রেকর্ড** — কোনো সময়সীমা নেই। লম্বা কথা নিজে থেকে ~১০ মিনিটের অংশে ভাগ হয় (চুপ থাকার মুহূর্তে), কোনো কথা বাদ পড়ে না।
- **Gemini AI** দিয়ে বানান, ব্যাকরণ, যতিচিহ্ন ঠিক করা; আঞ্চলিক উচ্চারণ ও বাংলা-ইংরেজি মিশ্র কথা বোঝা; নাম, টাকা, তারিখ, শতাংশ অপরিবর্তিত রাখা।
- **দুটি কার্ড**: বাংলা সংস্করণ ও English Version — কার্ডে ট্যাপ করলেই কপি, এডিট করা যায়, Copy Bangla / English / Both।
- **হিস্টরি** (Room ডাটাবেস) — তারিখ অনুযায়ী সাজানো, খোঁজা, এডিট, একটা/কয়েকটা/সব মোছা। ব্যর্থ হলে অডিও থাকে, "আবার চেষ্টা" করা যায়।
- **৮টি রঙের থিম** + লাইট/ডার্ক মোড (Settings → থিম ও রং)।
- API key শুধু ফোনে, Android Keystore দিয়ে এনক্রিপ্ট করা।

## ফোনে ইনস্টল (Install)
1. ফোনে এই পেজ খুলুন → ডানদিকে **Releases** → **latest** → **TF-Voice-Typer.apk** চাপুন।
2. ডাউনলোড শেষ হলে ফাইলটি খুলুন। "Install unknown apps" জিজ্ঞেস করলে Chrome-কে অনুমতি দিন → **Install**।
3. অ্যাপ খুলে **সেটিংস** → **Gemini API** → key পেস্ট → **সংরক্ষণ** → **পরীক্ষা**।
   বিনামূল্যে key: <https://aistudio.google.com/apikey> → "Create API key"।
4. হোমে মাইক্রোফোন ও "অন্য অ্যাপের উপরে দেখানো" অনুমতি দিন, তারপর **ভাসমান মাইক** চালু করুন।

## স্থায়ী সাইনিং কী (Permanent signing key — later)
এখন অ্যাপটি একটি **অস্থায়ী টেস্ট কী** দিয়ে সাইন হয়, যা শুধু GitHub-এর প্রাইভেট বিল্ড-ক্যাশে থাকে (রিপোতে নয়)।
টানা অনেকদিন (৭ দিনের বেশি) কোনো বিল্ড না হলে ক্যাশ মুছে নতুন টেস্ট কী তৈরি হতে পারে — তখন নতুন APK বসাতে আগে পুরনো অ্যাপ আনইনস্টল করতে হবে।
এটা চিরতরে ঠিক করতে স্থায়ী কী যোগ করুন (Claude-কে বললে ফাইল বানিয়ে দেবে):

1. রিপো → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**
2. `TF_KEYSTORE_BASE64` = keystore ফাইলের base64 লেখা
3. `TF_KEY_PASSWORD` = keystore-এর পাসওয়ার্ড
4. (ঐচ্ছিক) `TF_KEY_ALIAS` = key alias (না দিলে `tfvoicetyper`)
5. **Actions** → **Build TF Voice Typer APK** → **Run workflow**

> মনে রাখুন: **Gemini API key** আর **APK সাইনিং কী** সম্পূর্ণ আলাদা জিনিস। কোনোটাই কখনো কোডে বা GitHub-এ রাখা হয় না।

## Technical
Kotlin · Jetpack Compose · Material 3 · MVVM · Room · DataStore · Coroutines/Flow · OkHttp · Gemini REST API (`generateContent`, inline AAC audio, JSON schema output) · Foreground service (type `microphone`) · `TYPE_APPLICATION_OVERLAY` windows · Android Keystore AES-GCM. minSdk 26, targetSdk 35.

The Gemini base URL is a single constructor parameter (`GeminiClient(baseUrl)`), so a private proxy server can be added later for public distribution without changing the rest of the app.

Built automatically by GitHub Actions (`.github/workflows/build-apk.yml`) on every push to `main`.
