# Review checklist

Requirements verified by review (level **R** in DESIGN §8.1). Each item starts
with the SRS IDs it covers; `checkTraceability` (#27) will read them. Feature
issues add their items here. Every release PR signs off the whole list.

## Architecture (SRS §7)

- [ ] [AR-02] One store: all launcher state lives in `LauncherStore` in `:domain`; views hold only transient UI state (drag progress, scroll positions, running animations).
- [ ] [AR-04, UI-08] One gesture-and-motion component (`ui/motion/LayerGestureController`) and one token set (`ui/tokens/`) serve every view. `LayerRulesTest` catches literal colors, sizes, radii, alphas and durations, and animation specs or easing curves built outside the tokens; this item covers what it can't, such as a token used for something it wasn't meant for, or an animation left on Compose's default spec.
- [ ] [AR-05] No server, accounts, plugin or extension system, or remote configuration.
- [ ] [AR-06] The merged manifest declares exactly one `JobService`, `WallpaperJobService`, and nothing else runs in the background.
- [ ] [AR-08] Every component on the release runtime classpath is justified in DESIGN §7, and `gradle/runtime-dependencies.txt` changed in the same PR as §7.

## General (SRS §4.1)

- [ ] [FR-GEN-01] The launcher is named Selfish Launcher, with its own icon, in the system app list, the Home-app chooser and Settings' About. The icon resembles no Jolla, Sailfish OS, Microsoft or Bing mark (C-3, C-4), and looks right as a themed (monochrome) icon.
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

## Events (SRS §4.6)

- [ ] [FR-EVT-01, FR-ANIM-01] Swiping left on Home brings Events in from the right and swiping right brings it in from the left, following the finger over a dimming wallpaper; on Events, either swipe brings Home back from the side it is pushed toward, and Back sends Events out the way it came. Checked with gesture and 3-button navigation, and in the `ar-XB` pseudolocale.
- [ ] [FR-EVT-04] With real notifications (a message, an email, a download in progress, a media player), they are grouped under their app's icon and name, newest first; tapping each opens what it points to over the launcher, also on API 34+ devices including Samsung's, and a message that auto-cancels leaves the list.
- [ ] [FR-EVT-05] On a device, swiping a message sideways dismisses it from the system's shade too; swiping an ongoing one (media, a download) switches to Home instead, and a vertical drag on a row scrolls the list.
- [ ] [AC-06, FR-EVT-06, FR-GEST-05, FR-GEST-08] With notifications, pulling Events down slowly highlights "Wallpaper details" (once an image is cached), then "Clear notifications", with a tick for each, and no tick with the system's touch feedback off. Releasing on "Clear notifications" fades the dismissible ones under a 5-second banner; tapping it brings them back, and letting it run clears only those, not ongoing ones or ones that arrived meanwhile.
- [ ] [FR-EVT-07, FR-ONB-04] On a fresh install, "Turn on" on the notifications card opens the launcher's own notification-access screen; granting it and pressing Back lists the notifications. Revoking access in system settings turns the feature off and the card returns. "Hide" hides the card for good, also after a restart.
- [ ] [NFR-PRIV-05] Nothing in `adapters/notifications`, `ui/events` or the store logs, writes or sends what notifications say; `NotificationItem` and `Posted` print no contents.

## Top Menu (SRS §4.7)

- [ ] [FR-TOP-01, FR-ANIM-01, FR-GEST-04] On a device, swiping down from the middle of Home pulls the Top Menu down with the finger over a dimming wallpaper, and a short slow pull springs back; a pull that starts on the status bar opens the system's notification shade instead. Checked with gesture and 3-button navigation, and with a display cutout.
- [ ] [FR-TOP-04] "System settings" opens the Settings app over the launcher, and Back returns to the open Top Menu. Once an image is cached, "Wallpaper details" opens the page over Home, and Back closes it. "Launcher settings" opens Settings over Home the same way.
- [ ] [FR-TOP-06, FR-GEST-07, FR-GEST-08] Long-pressing beside the covers or on the clock opens the Top Menu with a tick, and with no tick when the system's touch feedback is off; long-pressing a cover still lifts it and opens its menu, and Home's menu never opens with it.
- [ ] [FR-TOP-02, FR-TOP-03, NFR-A11Y-01] With TalkBack, the parts of the menu are read as headings, the appearance options as radio buttons with the current one selected, and each ambience by its image's title. "Show top menu" on Home and "Show Home" on the menu open and close it.

## Gestures (SRS §4.8)

- [ ] [FR-GEST-01, FR-GEST-03] On a device, screen recordings of the App Grid, Events (both sides) and the Top Menu: each stays under the finger from the moment it starts moving, and touching one while it settles stops it under the finger, from where it can be dragged either way. Checked with gesture and 3-button navigation.
- [ ] [FR-GEST-07, FR-GEST-08] On a device, long-pressing a cover, an app, a folder, an app in a folder, a search result and an empty part of Home each ticks once, as it lifts or its menu opens. With Touch & hold delay set to Long in the system's accessibility settings, each waits that long. With the system's touch feedback off, nothing ticks.
- [ ] [FR-GEST-05, NFR-A11Y-06] The bar that shows a pulley menu is there, on Events and on an open folder, is visible but quiet over a bright and a dark wallpaper in both appearances, and fades as the menu is pulled. Each tick comes with the next item lighting up.
- [ ] [AC-10, FR-GEST-09, NFR-A11Y-02] With TalkBack and no swipes: open the App Grid, Events and the Top Menu from Home's actions, launch an app, choose "Clear notifications" from the Events header's actions, and cancel it from the banner, which TalkBack announces as it appears.

## Animation (SRS §4.9)

- [ ] [FR-ANIM-03] On a device, returning to the launcher with Back from an app, with Home, and from the recents screen: what is in view (Home, or the open App Grid) settles from slightly smaller and partly transparent to full over the Medium duration, as it does after unlocking the screen; the launcher's first start doesn't. Checked with a screen recording.
- [ ] [FR-ANIM-04] Pressing a cover, an app, a folder, an app in a folder, a search result, a menu item, a Top Menu option and the remorse banner glows in the highlight color from the first frame, also in the middle of the scrolling App Grid, and fades over the Short duration after release; starting to scroll or swipe instead fades it at once. Checked over a bright and a dark wallpaper, with a screen recording.
- [ ] [FR-ANIM-05, FR-ANIM-07, FR-ANIM-08] In screen recordings, things arriving or settling decelerate into place (reflow, a folder opening, a menu opening) and things leaving accelerate away (a folder closing, a menu closing, items fading out).
- [ ] [FR-ANIM-09] Releasing a pulley menu on an item flashes it in the highlight color for the Short duration before it runs, on Events and in a folder. Checked with a screen recording.
- [ ] [FR-ANIM-13] With no covers, Home's "Swipe up to see your apps" has a glowing point above it that traces a swipe up three times each time Home comes on screen, and then rests; with reduced motion it stays still.
- [ ] [AC-09, FR-ANIM-15, NFR-A11Y-05] On a device with "Remove animations" on in the accessibility settings: opening and closing the App Grid, Events and the Top Menu, a folder and a context menu, launching an app and returning are all instant or quick fades, and drags still follow the finger; turning it off brings the animations back without restarting the launcher. With the system's animations on, Animations set to Reduced in Settings does the same at once, and Follow system undoes it.

## Bing wallpaper (SRS §4.10)

- [ ] [FR-ANIM-10, AC-03] On a device, a new image crossfades in over the Ambient duration with no flash of black or of the old image, and only once the finger lifts: holding a finger on Home while it arrives (for example after "Refresh now") keeps the old one until release. With the App Grid open it changes under the dim. Checked with a screen recording, also with "Remove animations" on (a fade of at most 100 ms).
- [ ] [AC-03, FR-WP-01, IF-WP-01, IF-WP-04] On a device with a network, after `adb shell pm clear io.github.twonet.selfish` and choosing the launcher as Home: today's image becomes the wallpaper within a minute, `adb shell run-as io.github.twonet.selfish ls no_backup/wallpapers/images` lists up to 8 images, and Wallpaper details shows the title, copyright text, date and market of today's image in the device's market.
- [ ] [FR-WP-04] The image fills the screen, centered and undistorted, on the reference device and on two other OEM devices (R-5), for a portrait image and for one with only a landscape version, with gesture and 3-button navigation.
- [ ] [FR-WP-05] With "Also use on lock screen" on in Settings, the lock screen shows the image too, and turning it on puts the current one there at once; off, the lock screen keeps what it had.
- [ ] [FR-WP-09, FR-WP-10, NFR-A11Y-03] Wallpaper details is legible over a bright and a dark wallpaper in both appearances, shows the title, copyright text, date, market and last update, and its pulley menu offers "Refresh now", then "Keep this wallpaper" or "Resume daily", with a tick for each. With TalkBack, the header offers them as actions and "Checking for a new image…" is announced.
- [ ] [AR-06, NFR-PWR-02] `adb shell dumpsys jobscheduler` shows the launcher's one periodic job (3 hours, a network required, persisted across a reboot) and at most one retry; it holds no wake lock, alarm or foreground service, and turning Daily off removes both jobs.
- [ ] [FR-WP-13] Turning Daily Bing wallpaper off in Settings keeps the current system wallpaper, hides the Ambiences row and the Events card, and leaves `noBackupFilesDir/wallpapers/` empty (`adb shell run-as io.github.twonet.selfish ls no_backup`).

## Ambience (SRS §4.11)

- [ ] [FR-AMB-01, FR-AMB-02, AC-03] On a device, when "Refresh now" brings a new image, page headers, press glows, the pulley flash, the remorse bar and the Top Menu's outlines take a prominent color from it, and move there over the Ambient duration as the image crossfades in. Choosing another ambience in the Top Menu does the same. The color stays legible over a bright and a dark image, in both appearances. Checked with a screen recording.
- [ ] [FR-AMB-03] Setting a photo as the wallpaper in the system's picker or a gallery app and returning to Home gives a highlight from the photo's colors; a black-and-white photo gives the default highlight. With Daily Bing wallpaper on, the launcher's next image takes over again.
- [ ] [FR-AMB-04] The highlight colors only headers and states: page headers, press glows, selected and active outlines, the remorse bar, pulley highlights and text cursors. Body text, labels and notification text never take it.

## Appearance (SRS §4.12)

- [ ] [FR-THM-03, AC-01] On a device, with Appearance set to System and the App Grid open, scrolled and with a query typed: switching the device's dark theme from Quick Settings crossfades the launcher within a second, keeping the grid, its scroll position and the query. So does a dark-theme schedule taking effect, and Battery Saver turning dark theme on. Checked with a screen recording.
- [ ] [FR-THM-05, NFR-A11Y-03] In both appearances, over a bright and a dark wallpaper, text stays legible on Home (the clock on its scrim), on glass and on the dim, and the wallpaper itself never changes.
- [ ] [FR-THM-07, FR-THM-09, AC-02] With the device in dark theme, choosing Light in the Top Menu turns every surface Light at once (Home, the App Grid, a folder, a context menu, the remorse banner, Events, the Top Menu, Wallpaper details, Settings and About) and it stays Light after a restart; choosing Dark with the device light does the opposite. The tutorial (#19) is checked as it arrives.
- [ ] [FR-THM-08] On each surface, in both appearances, the status-bar icons and the navigation bar stay legible: with gesture navigation the handle, and with 3-button navigation the buttons on their scrim.

## Settings (SRS §4.13)

- [ ] [FR-SET-17, FR-GEN-01] About shows the launcher's name and version, the image credits with the current image's copyright text, and every shipped component under the Apache License 2.0, whose text opens on request. The non-affiliation notice is worded as #31 (Q-4) settles.
- [ ] [FR-SET-16, FR-HOME-07] On a device, with apps moved, a folder and covers of your own: Reset layout shows its banner, and tapping it keeps everything. Letting it run brings back an alphabetical grid with no folders and the device's default phone, messaging, browser and camera apps on Home, and keeps the settings, the wallpaper and a pinned ambience, also after a restart.
- [ ] [FR-SET-10, FR-GEST-06] On a device, Sort A–Z under its banner puts the grid's apps and folders, and each folder's apps, in alphabetical order for the device language (try one with accents, such as French), and leaves Home as it was.
- [ ] [FR-SET-12, FR-ONB-04] On a fresh install, turning on Notifications in Events in Settings opens the launcher's notification-access screen; granting it and pressing Back shows the switch on and the notifications in Events. Turning it off in Settings hides them at once.
- [ ] [FR-SET-06, FR-AMB-04] Each highlight swatch looks like the highlight it gives, and choosing one recolors the headers, outlines and press glows at once; over a bright and a dark wallpaper, in both appearances.
- [ ] [NFR-A11Y-01, FR-GEST-09] With TalkBack, Settings' switches are read with their state, the options as radio buttons with the chosen one selected, the swatches by their color names, and "About" is an action on the header.

## Bing interface (SRS §5.3)

- [ ] [AC-11, NFR-PRIV-01, IF-WP-02] With Daily Bing wallpaper on, a 24-hour capture of the reference device's traffic (for example PCAPdroid filtered to the launcher, or at the router) shows the launcher connecting only to `bing.com` and `bing.net` hosts, all over TLS on port 443.

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
