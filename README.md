# One Pace Player

Προσωπικό "mini Netflix" για Android tablet/κινητό. Δουλεύει 100% offline.

- Αρχική με κάρτα **Συνέχεια** και σειρές ανά arc, με εξώφυλλα από τα επεισόδια
- Θυμάται σε ποιο επεισόδιο και σε ποιο σημείο σταμάτησες
- ✓ για ό,τι έχεις δει, μπάρα προόδου, μετρητής ανά arc (π.χ. 3/4)
- Υπότιτλοι: ενσωματωμένοι στο mp4/mkv **ή** εξωτερικοί `.srt / .ass / .vtt` με ίδιο όνομα
- Αυτόματο επόμενο επεισόδιο, ±10s, κουμπί υποτίτλων, fullscreen landscape
- Long-press σε επεισόδιο: σήμανση ειδωμένο/μη ειδωμένο, **«Ειδωμένα όλα μέχρι εδώ»**, παίξε από την αρχή

## 1. Build το APK

1. Εγκατάσταση [Android Studio](https://developer.android.com/studio) (δωρεάν).
2. **File → Open** → διάλεξε τον φάκελο `OnePacePlayer`.
3. Περίμενε το Gradle sync (την πρώτη φορά κατεβάζει SDK/βιβλιοθήκες, μερικά λεπτά).
   Αν σου προτείνει αναβάθμιση του Android Gradle Plugin, μπορείς να πεις ναι.
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. Το APK βγαίνει στο `app/build/outputs/apk/debug/app-debug.apk`.

Εναλλακτικά από terminal: `./gradlew assembleDebug` (θέλει Android SDK, π.χ. από το Studio).

## 2. Εγκατάσταση στο tablet

1. Πέρασε το `app-debug.apk` στο tablet (καλώδιο, Drive, ό,τι θες).
2. Άνοιξέ το από τον File Manager → επίτρεψε «Εγκατάσταση από άγνωστες πηγές» για εκείνη την εφαρμογή.
3. Εναλλακτικά με USB debugging: `adb install app-debug.apk` ή απλά ▶ Run από το Studio.

## 3. Επεισόδια

Αντέγραψε τα επεισόδια στο tablet (π.χ. `Movies/One Pace`). Προτεινόμενη δομή:

```
One Pace/
  01 Romance Dawn/
    [One Pace][1] Romance Dawn 01 [1080p][xxxx].mp4
    [One Pace][1] Romance Dawn 01 [1080p][xxxx].srt     <- προαιρετικό
  02 Orange Town/
    ...
```

- **Υποφάκελος = arc.** Ο αριθμός μπροστά (01, 02…) ορίζει τη σειρά.
- Αν είναι όλα χύμα σε έναν φάκελο, το app βγάζει το arc από το όνομα αρχείου
  και τα ταξινομεί με βάση τα κεφάλαια `[1]`, `[218-220]` κλπ. του One Pace.
- Υπότιτλοι: ίδιο όνομα με το βίντεο (`X.srt`) ή με γλώσσα (`X.en.srt`, `X.el.ass`).

Άνοιξε το app → **Επιλογή φακέλου** → διάλεξε τον `One Pace`. Τέλος.
Αν προσθέσεις επεισόδια αργότερα: ⚙ → **Ανανέωση βιβλιοθήκης**.

> Η πρόοδος αποθηκεύεται ανά αρχείο. Αν μετονομάσεις/μετακινήσεις ένα αρχείο,
> χάνεται η πρόοδος για αυτό (μπορείς να χρησιμοποιήσεις «Ειδωμένα όλα μέχρι εδώ»).

## Δομή κώδικα

| Αρχείο | Τι κάνει |
|---|---|
| `data/Library.kt` | Σάρωση φακέλου (SAF), ομαδοποίηση σε arcs, ταίριασμα υποτίτλων |
| `data/Naming.kt` | Parsing ονομάτων One Pace, natural sort |
| `data/ProgressStore.kt` | Πρόοδος ανά επεισόδιο (SharedPreferences) + λογική «Συνέχεια» |
| `data/Thumbnails.kt` | Εξώφυλλα από καρέ του βίντεο, cache στον δίσκο |
| `ui/HomeScreen.kt` | Αρχική (Compose) |
| `PlayerActivity.kt` | Player (Media3 / ExoPlayer) + αποθήκευση θέσης κάθε 5s |

Stack: Kotlin 2.0, Jetpack Compose (Material 3), Media3 1.4, minSdk 26 (Android 8+).
