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
