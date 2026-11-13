# Review checklist

Requirements verified by review (level **R** in DESIGN §8.1). Each item starts
with the SRS IDs it covers; `checkTraceability` (#27) will read them. Feature
issues add their items here. Every release PR signs off the whole list.

## Architecture (SRS §7)

- [ ] [AR-02] One store: all launcher state lives in `LauncherStore` in `:domain`; views hold only transient UI state (drag progress, scroll positions, running animations).
- [ ] [AR-04, UI-08] One gesture-and-motion component (`ui/motion/LayerGestureController`) and one token set (`ui/tokens/`) serve every view. `LayerRulesTest` catches literal colors, sizes, radii, alphas and durations; this item covers what it can't, such as copied easing curves or a token used for something it wasn't meant for.
- [ ] [AR-05] No server, accounts, plugin or extension system, or remote configuration.
- [ ] [AR-06] The merged manifest declares exactly one `JobService`, `WallpaperJobService`, and nothing else runs in the background.
- [ ] [AR-08] Every component on the release runtime classpath is justified in DESIGN §7, and `gradle/runtime-dependencies.txt` changed in the same PR as §7.

## General (SRS §4.1)

- [ ] [FR-GEN-01] The launcher is named Selfish Launcher, with its own icon, in the system app list and the Home-app chooser; and in About once Settings has it (#18). The icon resembles no Jolla, Sailfish OS, Microsoft or Bing mark (C-3, C-4), and looks right as a themed (monochrome) icon.
- [ ] [FR-GEN-06] On a device, with the launcher open: installing an app adds it; updating one that changes its name or icon shows the new name and icon; disabling, re-enabling and uninstalling one remove and restore it. `LauncherAppsCatalogTest` covers only enabling and disabling.
- [ ] [FR-GEN-10] With a work profile, its apps are listed with the system's work badge, and adding or removing the profile updates the list. (Could; scope pending Q-6, #33.)

## Home (SRS §4.2)

- [ ] [FR-HOME-01] Home shows the wallpaper edge to edge, and the status-bar icons and the clock stay legible through the scrim over a bright and a dark wallpaper.
- [ ] [FR-HOME-04, FR-ANIM-02] Tapping a cover, or an app in the list, grows the app's window from what was tapped, with gesture and 3-button navigation.
- [ ] [FR-HOME-06, FR-ANIM-05, FR-ANIM-06, FR-ANIM-08] Long-pressing a cover lifts it and slides its menu open beneath its row; dragging closes the menu, the cover follows the finger at about 110% with a shadow, the others make room, and the dropped cover settles into place. A screen recording is checked against the Medium duration.
- [ ] [FR-HOME-07] On a first run (`adb shell pm clear`), Home shows the device's default phone, messaging, browser and camera apps, in that order, skipping any that are missing or have no default.

## App Grid (SRS §4.3)

- [ ] [FR-GRID-01, FR-ANIM-01] Swiping up on Home slides the App Grid up while the wallpaper dims in step, and blurs on a device with cross-window blur (R-1); without blur (the Galaxy S22, API 29–30, Battery Saver) the dim alone looks intentional. Checked with gesture and 3-button navigation.
- [ ] [FR-GRID-06] On a device, long-pressing an app opens its menu beneath its row; App info opens the system's app details and Uninstall its confirmation (cancelled, then confirmed for a test app), and system apps don't offer Uninstall.
- [ ] [FR-GRID-07, FR-GRID-08] Holding a dragged app near the top or bottom scrolls the grid at a controllable speed; passing over apps doesn't push them away, resting beside one does, and dropping onto one makes a folder. A screen recording is checked against the Medium duration.
- [ ] [AC-07, FR-GRID-09, NFR-REL-05] With the grid open and scrolled, `adb install` of an app adds it at the end without moving what is on screen, and `adb uninstall` removes it without leaving a gap or closing the grid.

## Folders (SRS §4.4)

- [ ] [FR-FOLD-03, FR-ANIM-07] Tapping a folder grows its panel out of its icon over the dimmed grid, and Back, Home and a tap outside shrink it back into the icon. A screen recording is checked against the Medium duration.
- [ ] [FR-FOLD-05, AC-08] An app dragged past the edge of an open folder keeps following the finger as the panel fades, the grid makes room for it, and it can be dropped in the grid, onto an app or onto another folder. A folder left with one app turns back into that app in its place. Checked with a screen recording.
- [ ] [FR-FOLD-07, FR-GEST-05, FR-GEST-08] Pulling an open folder down slowly highlights "Rename", then "Remove folder", with a tick for each, and no tick with the system's touch feedback turned off. Releasing before the first item does nothing.
- [ ] [FR-FOLD-07, FR-GEST-06, FR-ANIM-12] "Remove folder" shows the banner over the grid and fades the folder. Tapping the banner fades it back. Pressing Home and waiting 5 s still returns its apps to the grid.
- [ ] [FR-FOLD-04] Renaming with the on-screen keyboard: Done keeps the new name; Back hides the keyboard first, and the next Back closes the folder and keeps what was typed.

## Search (SRS §4.5)

- [ ] [FR-SRCH-01, FR-SRCH-03, FR-SRCH-05] On a device, with the system keyboard: tapping the search field opens the keyboard; Go launches the first result, and Back from that app returns to the results without the keyboard; Back with the keyboard open hides it first, and the next Back closes the grid; closing the grid with Back, Home or a swipe down clears the query and the keyboard.
- [ ] [FR-SRCH-06] Nothing in `domain/search` or `ui/search` uses a port, the network or a web-search intent, and the field turns autocorrection off.
- [ ] [NFR-A11Y-01] With TalkBack, the empty search field is announced as "Search apps", and "No apps found" is announced when it appears.

## User interface (SRS §5.1)

- [ ] [UI-01] Every surface floats over the wallpaper on the `glass`, `scrim` or `dim` tokens; no surface has an opaque full-screen background. Checked in both appearances over a bright and a dark wallpaper.
- [ ] [UI-02] Every page header (Events, Settings, Wallpaper details, folders) is a `PageHeader`: large, at the trailing edge and in the highlight color.
- [ ] [UI-03] No toolbars, action bars, floating buttons or tab bars. Actions live in pulley menus, context menus and the Top Menu.
- [ ] [UI-04] Text uses only the four `TextRole`s and the two `Emphasis` levels, through `LauncherText` or `PageHeader`; nothing styles text by hand.
- [ ] [UI-05] Every app icon is an `AppIcon`, so all icons share one size, and those that support a shape share the rounded one.
- [ ] [UI-07] Content is padded by `WindowInsets.safeDrawing`; only the wallpaper and the dim and scrim layers that tint it run edge to edge. Checked on a device or emulator with a display cutout and rounded corners, with gesture and 3-button navigation.
- [ ] [UI-09] The launcher makes no sounds of its own, with system touch sounds turned on.

## Data (SRS §8)

- [ ] [NFR-PRIV-03] Only `FileStorage` writes to disk, and only under `filesDir` (layout and settings, backed up) and `noBackupFilesDir` (wallpaper cache). Notifications and recently used apps are never part of `Layout` or `Settings`, and are never written or logged.
