# TopLevel

A level-up style life tracker for Android (built for the Galaxy Z Fold), with Claude built in as your coach.

```
[ SYSTEM ]  Daily quests have arrived.
```

## What it does

| Area | What it does |
|---|---|
| **Status** | Your level, rank (E→S), title and EXP bar. Five stats: STR, VIT, INT, DIS, SPI |
| **Daily quests** | Each morning Claude reads your plans, your calendar and yesterday's check-in, then builds 4–7 concrete quests. They adapt to you: difficulty follows your completion rate, quests you delete aren't suggested again, and ↻ asks for your wishes ("little time today", "more learning"). Finishing one earns EXP; clearing them all adds a bonus |
| **Learn: AI quiz** | Type what you're learning (and your level/goal). Claude writes questions for your level, quizzes you every day, checks your typed answer with feedback, and asks more about what you get wrong. Spaced repetition decides what's due. A daily quiz quest completes itself |
| **Evening report** | Mood, energy, sleep, how the day went, what went well, what you'd change. Claude replies with a reflection and adds a quest for tomorrow |
| **Nutrition** | Type "200g chicken + rice" and Claude estimates the macros. Progress bars track your kcal and macro goals, and hitting your protein goal earns VIT |
| **Journal** | Work, learned, training and note logs, each feeding a stat (Learn tab) |
| **Skills** | Things to learn: Locked → In training → Acquired. Claude can suggest your next skills (menu) |
| **Coach** | Chat with Claude, who sees all your data. Paste a plan and it's saved. Say "I ate X" and it's logged. It can add quests, skills, memories and **Google Calendar events** |
| **Discovery** | 3 songs picked from your music taste (open in **YouTube Music**) and 2 educational videos for your SRE and learning goals (open in YouTube), refreshed daily |
| **Health Connect** | Steps, sleep, workouts and weight from Samsung Health or your watch, synced hourly. Measurable quests (e.g. "8,000 steps") complete themselves |
| **Any sport** | Setup asks what you do (gym, running, cycling, football, yoga… or your own). Train only shows what fits: log a session in two taps (sport, minutes, km). Logged sessions count for workout quests, and quests use your sports |
| **Weekly plan** | Train → "Your week": the AI builds a Mon–Sun plan from your sports, or paste the plan you already have (coach message, table, notes) and it's converted. Tap a day to edit. Today's quest is today's session; "Done" logs it in one tap |
| **Calisthenics tracking** (if you pick Calisthenics) | Built for advanced athletes. Planche, front lever, back lever, handstand, muscle-up and HSPU, each with 7–8 stages up to elite (full planche push-up, maltese, one-arm FL…). You can rename the stages to your own progressions. Tap a stage to set your level; **Advance ▲** marks a new stage and pays EXP + Gold. Log holds/reps with +kg and notes (parallettes, RPE…), and see per-stage PRs, weekly volume and time-under-tension charts |
| **Coach-built sessions** (optional, off by default) | Switch on to have Claude program a session from your stages, volume and recovery |
| **Vault: Gold & rewards** | Quests, sessions, stage-ups, bosses, achievements and saving earn **Gold**. Add real rewards (e.g. "New desk €250, unlock at straddle planche"). To claim one you need the Gold, the € in your savings pot, its condition met, and a 72h cool-down (anti-impulse) |
| **Savings** | Monthly savings goal, quick +€5/10/20/50 deposits (earns DIS EXP + Gold), and "I almost bought something": skipped purchases go to your pot. The coach adds no-spend and saving quests |
| **Workout tracker** | Log sets (kg × reps), see an estimated-1RM chart per exercise and your PRs, and get Claude's target for your next session (progressive overload) |
| **Streaks & penalties** | Clearing ≥80% of quests keeps your streak going, worth +5% EXP per day (max ×1.5). Missing over half spawns a penalty quest, and ignoring that costs 50 EXP |
| **Weekly boss** | Every Monday: a report on last week, plus a big goal for this week. Defeating it pays +150–300 EXP |
| **Body** | Weight and waist with a trend chart. "Adjust my macros" updates your targets from your trend |
| **Focus blocks** | 25/50/90-minute deep-work timer that pays Discipline EXP |
| **Voice input** | 🎙 on the check-in, journal, food and coach fields |
| **Fold layout** | Unfolded, the Coach stays open on the right next to any screen |
| **Achievements & titles** | 23 achievements (incl. planche/front-lever milestones and savings); equip the titles they unlock on your status card (tap the player card) |
| **Backup** | Export/restore everything as a file. Choose Google Drive in the picker for a cloud backup |
| **Widgets** | **Daily Quests** (tap a quest to complete it from the home screen), **Quiz** (a due question: show the answer, then "knew it" / "again"), **Food** and **Status** (level, stats, progress) |
| **Notifications** | Morning quest briefing and an evening check-in reminder, at times you choose |

All data is stored locally on your phone in a SQLite/Room database. The API key is stored only in the app's private storage.

## Install on your phone (no Android Studio needed)

1. Push this project to your GitHub repo (`BoXuan21/TopLevel`):
   ```bash
   git clone https://github.com/BoXuan21/TopLevel.git
   cd TopLevel
   # copy all files from this folder in (including the hidden .github folder)
   git add -A && git commit -m "TopLevel v1" && git push
   ```
2. GitHub Actions builds the APK (about 5 minutes). Check progress in the **Actions** tab.
3. On your Fold, open `github.com/BoXuan21/TopLevel/releases/latest` and download **TopLevel.apk**.
4. Open the file and allow "Install unknown apps" for your browser when asked.
5. Every later push builds a new release, which installs over the old one and keeps your data. Signing uses the fixed key in `keystore/`.

> Keep the repo **private**: it contains the signing key. That's fine for a personal app.

## First run

1. Allow **Calendar** and **Notifications**.
2. In **Setup → Health Connect**, tap **Connect**. In Samsung Health, also enable *Settings → Health Connect* sharing.
3. In **Setup**, paste your **Anthropic API key** (from console.anthropic.com) and tap **Test**.
4. Fill in **Plans & context**: goals, training plan, eating plan, work, learning goals and music taste. You can also paste them into the Coach chat.
5. Set your nutrition goals, or ask the coach to calculate them.
6. Long-press the home screen → Widgets → **TopLevel** → add *Daily Quests* and *Status*.
7. Go to **Status** → ↻ to generate today's quests.

## Tech

Kotlin · Jetpack Compose · Room · Glance (widgets) · WorkManager · Health Connect · OkHttp · Anthropic Messages API with tool use · Android CalendarContract (reads and writes the Google Calendar synced on the phone, no Google sign-in needed).

The default model is `claude-sonnet-5-5`; you can change it in Setup.

Typical API cost: a few cents per day (quests + discovery + check-in + some chat).

## Building locally (optional)

Open the folder in Android Studio and press Run, or run `./gradlew assembleRelease`.
