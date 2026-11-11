# Larpude

Morphe patch for the Claude Android app (`com.anthropic.claude`). Goal: a **Larpude** button above
*Profile* in Settings that opens a screen saying **Hello**.

## Build (no PC needed)
1. Create a GitHub repo, upload everything in this folder (keep `.github/`).
2. Edit `YOUR_USER` in `patches/build.gradle.kts`.
3. Actions tab > "Build patches" > Run workflow. Download the `larpude-patches` artifact (`.mpp`).
4. Open Morphe Manager > add the `.mpp` as a local patch source > pick the Claude APK/APKM > patch.
   Morphe Manager merges split APKs (.apkm/.xapk) itself, so give it the whole bundle.

## What it does (v0.2.0)
Adds a **Larpude** row at the top of Claude's Settings. Tapping it opens a screen that says Hello.
Written against Claude 1.261106.19 and 1.261109.19. The hooks find their targets by structure,
not by obfuscated name, so most updates should keep working.

Hooks (see `LarpudePatch.kt`): `MainActivity.onCreate`, `settings.o.b` (builds the row list, a row
is `eea0`), `jkm.d` (row tap handler).

## Updating for a new Claude version
Names like `jkm`, `eea0`, `y2m` change every release. Re-find them: dump the smali, search for
`Lcom/anthropic/claude/settings/SettingsAppScreen$ProfileScreen;` and follow it up to the enum that
lists the rows, then the function that builds `List<row>`, then the tap lambda.

## Termux: inspect the APK
```sh
pkg install aapt2
aapt2 dump badging base.apk | grep launchable-activity
```
