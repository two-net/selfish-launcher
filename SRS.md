# Selfish Launcher — Software Requirements Specification

| | |
|---|---|
| **Product** | Selfish Launcher, a home-screen app for Android phones |
| **Document** | Software Requirements Specification (SRS) |
| **Version** | 1.0 (draft) |
| **Date** | 2026-11-09 |
| **Note** | Technology-neutral: this document says *what* the launcher must do, not *how* or *with what* it is built. |

**Contents**

1. Introduction
2. Overall description
3. Sailfish OS reference model
4. Functional requirements
5. External interface requirements
6. Non-functional requirements
7. Architecture constraints
8. Data requirements
9. Out of scope for 1.0
10. Acceptance criteria
11. Open questions
- Appendix A. Traceability to the brief

---

## 1. Introduction

### 1.1 Purpose

This SRS defines the functional and non-functional requirements for Selfish Launcher 1.0. It is the reference for design, implementation, testing and acceptance. It deliberately prescribes no programming language, framework, library, service or tool; those choices belong in a separate technical design.

### 1.2 Product scope

Selfish Launcher replaces the Android home screen with a gesture-driven experience modeled on Sailfish OS: a calm Home with app covers, an App Grid one swipe up, an Events view one swipe sideways and a Top Menu one swipe down, all drawn as translucent layers over a wallpaper that changes every day to Bing's image of the day.

| ID | Goal |
|---|---|
| G-1 | The whole launcher can be navigated with three gestures (up, sideways, down), as in Sailfish OS. |
| G-2 | Motion follows the finger, can be interrupted at any time and feels physical. |
| G-3 | A fresh, properly attributed Bing image of the day as wallpaper, with no effort from the user. |
| G-4 | Light, Dark and System appearance, with System as the default. |
| G-5 | A small, simple and private product: no accounts, no backend, no tracking. |

### 1.3 Definitions

| Term | Meaning |
|---|---|
| Ambience | The current wallpaper together with the highlight color derived from it (a Sailfish OS concept). |
| App Grid | The full list of installed apps, opened by swiping up on Home. |
| Cover | A large card on Home that represents a favorite app. In Sailfish OS, covers represent running apps. |
| Events | The view beside Home that shows today's wallpaper story and, optionally, notifications. |
| Highlight color | The accent color used for headers and for pressed, selected and active states. |
| Image of the day | The photograph Bing publishes on its home page each day, per market. |
| Market | A Bing region and language code, such as en-US, that decides which image of the day is shown. |
| Peek | A partial swipe that previews the next view; releasing early returns to where the user started. |
| Pulley menu | A menu revealed by pulling a page down past its top. Items are highlighted one by one as the pull deepens, and releasing selects the highlighted item. |
| Remorse timer | A short countdown shown instead of a confirmation dialog; tapping it cancels the action. |
| Top Menu | A panel pulled down over Home with quick launcher controls. |
| dp | Density-independent pixel, Android's standard unit for UI sizes. |

### 1.4 Conventions

- Requirements use "shall". Each has an ID and a priority (column **P**):
  - **M** Must: required for release 1.0.
  - **S** Should: expected in 1.0; may slip with agreement.
  - **C** Could: desirable if time allows.
  - **W** Won't: explicitly not in 1.0.
- ID prefixes: `FR-` functional, `UI-` user interface, `IF-` external interface, `NFR-` non-functional, `AR-` architecture, `AC-` acceptance criteria.

### 1.5 References

- Sailfish OS documentation: https://docs.sailfishos.org
- Sailfish OS Lipstick (home screen) reference: https://docs.sailfishos.org/Reference/Core_Areas_and_APIs/Apps_and_MW/Lipstick/
- Bing (image of the day): https://www.bing.com
- Web Content Accessibility Guidelines (WCAG) 2.1: https://www.w3.org/TR/WCAG21/

---

## 2. Overall description

### 2.1 Product perspective

Selfish Launcher is a standalone Android app that the user selects as the default Home app. Everything runs on the device; its only network traffic is fetching the Bing image of the day. It is inspired by Sailfish OS (by Jolla) but is an independent product: it reuses no Sailfish OS code, artwork or trademarks and does not claim any affiliation.

### 2.2 Design principles (from Sailfish OS)

1. **Gestures over buttons.** Navigation needs no on-screen buttons, and every gesture has an accessible alternative.
2. **Content first.** The wallpaper is always visible; the UI floats on translucent glass with no toolbars or action bars.
3. **Ambience.** The wallpaper sets the mood and the highlight color of the whole UI.
4. **Direct manipulation.** Views follow the finger, can be peeked at and cancel if released early.
5. **One-handed.** Menus are pulled toward the thumb, not reached for.
6. **Forgiving.** Destructive actions use a remorse timer instead of "Are you sure?" dialogs.
7. **Calm.** No ads, no nagging; notification dots are off by default.

### 2.3 User classes

| User | Description | Key needs |
|---|---|---|
| Everyday Android user | Wants a beautiful, simple home screen. | Fast access to apps, search, good defaults. |
| Sailfish OS enthusiast | Knows Sailfish OS, uses an Android phone. | Faithful gestures, covers, pulley menus, ambiences. |
| Privacy-conscious user | Often runs Android without Google services. | No tracking, few permissions, no Google dependency. |
| User relying on accessibility | Uses a screen reader, large text or reduced motion. | Alternatives to gestures, strong contrast, motion control. |

### 2.4 Operating environment

- Android phones in portrait orientation, screens of roughly 5 to 7 inches, all common pixel densities.
- Gesture navigation and 3-button navigation.
- Works without Google Play Services and when installed outside an app store.
- The minimum Android version is to be decided (Q-1); where platform features vary, requirements state the fallback.

### 2.5 Constraints

| ID | Constraint |
|---|---|
| C-1 | This document prescribes no technology stack. |
| C-2 | Android does not let a launcher replace the lock screen, capture gestures inside other apps, show or close other apps' running tasks, or toggle most system settings. Sailfish OS features that depend on these are adapted (§3) or excluded (§9). |
| C-3 | Sailfish OS is a reference for interaction and visual patterns only. No Jolla code, icons, fonts, sounds, wallpapers or trademarks may be used, and nothing may suggest an affiliation with Jolla. |
| C-4 | Bing images are copyrighted by their owners. The launcher shows attribution and offers no saving, sharing or export. Use of the image feed is subject to legal review (Q-3). Nothing may suggest an affiliation with Microsoft. |
| C-5 | Screen edges reserved by the system (Back, Home, notification shade) belong to the OS; launcher gestures must work without them. |

### 2.6 Assumptions and dependencies

| ID | Assumption |
|---|---|
| A-1 | The Bing image-of-the-day feed remains publicly reachable. If it is not, the launcher keeps working with its cached images (FR-WP-11). |
| A-2 | Users have network access at least occasionally. |
| A-3 | Notification and usage features depend on the user granting those special accesses in system settings. |
| A-4 | On devices whose system colors follow the wallpaper, the daily image also changes system colors. This is expected and is explained in the wallpaper setting's description. |

---

## 3. Sailfish OS reference model

### 3.1 Concept mapping

| Sailfish OS | Selfish Launcher | Adaptation |
|---|---|---|
| Home with live covers of running apps | Home with covers of favorite apps | Android doesn't share other apps' live content, so covers show the icon, the name and optional quick actions. |
| Events view beside Home | Events: today's wallpaper story plus optional notifications | Notifications need user-granted notification access. |
| App Grid, opened by swiping up | App Grid, opened by swiping up anywhere on Home | The bottom edge belongs to the system's navigation gestures. |
| Top Menu, pulled from the top edge | Top Menu, opened by swiping down on Home | The top edge belongs to the system's notification shade. |
| Ambiences | Daily Bing image plus derived highlight color | Recent images serve as quick-switch ambiences. |
| Light and dark ambiences | Light, Dark and System appearance | System is the default. |
| Pulley menus | Pulley menus in Events, folders, Wallpaper details and Settings | Each change of highlighted item gives a haptic tick. |
| Remorse timers | Remorse timers for destructive actions | 5-second countdown. |
| Peek | Peek on every view transition | Release before the threshold to cancel. |
| Context menus that open beneath the item | The same, and also the entry point to rearranging | — |
| Right-aligned page headers in highlight color | The same | Mirrored in right-to-left languages. |
| Gesture hints and tutorial | First-run tutorial and contextual hints | Can be replayed from Settings. |
| Edge swipes inside apps, closing apps, lock screen | Not possible for a launcher | Out of scope (§9). |

### 3.2 Navigation model

```
                        ┌────────────┐
                        │  Top Menu  │
                        └─────▲──────┘
                              │ swipe down
┌────────────┐  swipe   ┌─────┴──────┐
│   Events   │◄────────►│    Home    │
└────────────┘ ← or →   └─────┬──────┘
                              │ swipe up
                        ┌─────▼──────┐
                        │  App Grid  │──► Folders, Search
                        └────────────┘
```

- From Home: swipe up for the App Grid, swipe left or right for Events, swipe down for the Top Menu.
- From Events: swipe left or right to return to Home.
- From the App Grid, scrolled to the top: drag down to return to Home. From the Top Menu: swipe up.
- System Back closes the topmost layer (keyboard, menu, folder, then the App Grid, Events, Top Menu or a settings page); on Home it does nothing.
- System Home returns to Home from anywhere in the launcher and closes every layer.

---

## 4. Functional requirements

### 4.1 General

| ID | Requirement | P |
|---|---|---|
| FR-GEN-01 | The app shall be named **Selfish Launcher** in the system app list, the Home-app chooser and its About screen, with an original app icon. | M |
| FR-GEN-02 | The app shall be selectable as the device's default Home app. On first launch, if it is not the default, it shall offer to become the default through the system's standard prompt. | M |
| FR-GEN-03 | The system Home button or gesture shall return to Home and close every open launcher layer. On Home with nothing open, it shall do nothing. | M |
| FR-GEN-04 | System Back shall close the topmost launcher layer; on Home it shall do nothing. | M |
| FR-GEN-05 | Apps shall launch with a single tap. Repeated taps while an app is launching shall not launch it twice. | M |
| FR-GEN-06 | Only enabled, launchable apps shall be shown. Installs, removals, updates (including name and icon changes) and enable or disable events shall be reflected while the launcher is running. | M |
| FR-GEN-07 | Layout and settings shall persist across launcher restarts, device reboots and app updates. | M |
| FR-GEN-08 | If the system ends the launcher's process, the launcher shall reopen on Home with the saved layout. | M |
| FR-GEN-09 | The launcher shall use portrait orientation. | M |
| FR-GEN-10 | Apps in a work profile shall be shown with the system's work badge. | C |

### 4.2 Home

| ID | Requirement | P |
|---|---|---|
| FR-HOME-01 | Home shall show the wallpaper edge to edge, with a light scrim behind the status bar where needed to keep it legible. | M |
| FR-HOME-02 | Home shall show the time and date in large type at the top. The user can hide them. | S |
| FR-HOME-03 | Home shall show up to 9 covers of favorite apps: portrait cards on translucent glass, each with the app's icon and name. With 1 to 4 covers they shall be arranged in 2 columns; with 5 to 9, in 3 columns. | M |
| FR-HOME-04 | Tapping a cover shall launch its app, animating from the cover (FR-ANIM-02). | M |
| FR-HOME-05 | Users shall add an app to Home from its App Grid context menu and remove it from the cover's context menu. When Home already has 9 covers, "Add to Home" shall explain that Home is full. | M |
| FR-HOME-06 | Long-pressing a cover shall open its context menu and let the user drag the cover to a new position; other covers shall make room with animation. | M |
| FR-HOME-07 | On first run, Home shall be pre-filled with up to 4 covers: the default phone, messaging, browser and camera apps, where present. | S |
| FR-HOME-08 | With no covers, Home shall show the hint "Swipe up to see your apps". | M |
| FR-HOME-09 | A cover may show up to 2 quick actions taken from the app's published shortcuts, like Sailfish OS cover actions. | C |
| FR-HOME-10 | With "Covers show: Recently used" selected and usage access granted, Home shall show the most recently used apps instead of favorites. | C |

### 4.3 App Grid

| ID | Requirement | P |
|---|---|---|
| FR-GRID-01 | Swiping up anywhere on Home, outside system gesture areas, shall open the App Grid, which slides up over a dimmed (and, where supported, blurred) wallpaper. | M |
| FR-GRID-02 | The App Grid shall show all launchable apps and folders as a vertically scrolling grid of icons with labels on one line (two lines at large font sizes). It shall have 4 columns by default, adjustable from 3 to 5. | M |
| FR-GRID-03 | The App Grid shall open scrolled to the top, with a search field above the first row (§4.5). | M |
| FR-GRID-04 | When the App Grid is scrolled to the top, dragging down shall move it with the finger and close it to Home, with peek-cancel. | M |
| FR-GRID-05 | On first run, apps shall be sorted alphabetically. Users can drag items to rearrange them, and the custom order is kept. New apps are added at the end. | M |
| FR-GRID-06 | Long-pressing an app shall lift it and open a context menu beneath its row, pushing later rows down, with: Add to Home or Remove from Home, App info, and Uninstall (which opens the system's confirmation). Dragging the lifted icon closes the menu and starts moving it. | M |
| FR-GRID-07 | While an item is dragged, other items shall reflow with animation, and holding it near the top or bottom of the grid shall auto-scroll. | M |
| FR-GRID-08 | Dropping an app onto another app shall create a folder (§4.4). | M |
| FR-GRID-09 | An uninstalled app shall disappear from the App Grid, Home and folders without leaving gaps. | M |
| FR-GRID-10 | The context menu shall also list the app's published shortcuts; tapping one opens it. | C |
| FR-GRID-11 | With notification access granted and "Notification dots" on (off by default), icons and covers shall show a dot for apps with notifications. | C |

### 4.4 Folders

| ID | Requirement | P |
|---|---|---|
| FR-FOLD-01 | Dropping an app onto another app shall create a folder named "Folder" that contains both, in the target's position. | M |
| FR-FOLD-02 | A folder's tile shall preview up to 4 of its apps' icons. | M |
| FR-FOLD-03 | Tapping a folder shall expand it from its tile into a panel that shows its name and apps over a dimmed background. Back, Home or tapping outside closes it. | M |
| FR-FOLD-04 | Tapping the folder's name shall let the user rename it. | M |
| FR-FOLD-05 | Apps can be dragged into a folder, and out of an open folder into the grid. | M |
| FR-FOLD-06 | A folder left with one app shall dissolve, leaving that app in the folder's place. A folder can never be empty. | M |
| FR-FOLD-07 | The folder panel shall have a pulley menu with "Rename" and "Remove folder". Removing returns its apps to the grid after a remorse timer. | S |
| FR-FOLD-08 | Folders cannot be nested or placed on Home. | M |

### 4.5 Search

| ID | Requirement | P |
|---|---|---|
| FR-SRCH-01 | Tapping the search field shall open the keyboard and filter the App Grid as the user types. | M |
| FR-SRCH-02 | Matching shall ignore case and accents. Apps whose name, or any word in it, starts with the query rank first, followed by other matches anywhere in the name; ties are alphabetical. Apps inside folders are included. | M |
| FR-SRCH-03 | The keyboard's action key shall launch the first result. | S |
| FR-SRCH-04 | When nothing matches, the grid shall show "No apps found". | M |
| FR-SRCH-05 | Closing the App Grid shall clear the query and hide the keyboard. | M |
| FR-SRCH-06 | Search shall be local only: no web results and no suggestions from the network. | M |

### 4.6 Events

| ID | Requirement | P |
|---|---|---|
| FR-EVT-01 | Swiping left or right on Home shall open Events; swiping left or right on Events shall return to Home. | M |
| FR-EVT-02 | Events shall use today's date as its page header. | M |
| FR-EVT-03 | Events shall show a "Today's wallpaper" card with a thumbnail, the title, the copyright text and "Learn more", which opens the image's information link in the default browser. Tapping the card opens Wallpaper details (FR-WP-09). | M |
| FR-EVT-04 | With "Notifications in Events" on and notification access granted, Events shall list current notifications grouped by app, each showing the app's icon and name, the title, text and time. Tapping one opens what the notification points to. | S |
| FR-EVT-05 | Swiping a dismissible notification sideways shall dismiss it. Sideways swipes that start outside a notification switch views. | S |
| FR-EVT-06 | Events shall have a pulley menu with "Wallpaper details" and, when notifications are shown, "Clear notifications" (with a remorse timer; only dismissible notifications are cleared). | S |
| FR-EVT-07 | Without notification access, Events shall show one card that explains the feature and offers to turn it on. Dismissing the card hides it for good. | S |
| FR-EVT-08 | When there is nothing besides the wallpaper card, Events shall show "No new events". | S |

### 4.7 Top Menu

| ID | Requirement | P |
|---|---|---|
| FR-TOP-01 | Swiping down anywhere on Home, below the status bar, shall pull the Top Menu down from the top, following the finger, with peek-cancel. | M |
| FR-TOP-02 | The Top Menu shall show an appearance switch: Light, Dark, System. | M |
| FR-TOP-03 | The Top Menu shall show an Ambiences row: a "Daily" tile followed by thumbnails of the cached images, newest first. Tapping a thumbnail applies and pins that image; tapping "Daily" resumes daily updates. The active tile is outlined in the highlight color. The row is hidden when Bing wallpaper is off. | M |
| FR-TOP-04 | The Top Menu shall offer shortcuts to Launcher settings, the system Settings app and Wallpaper details. | M |
| FR-TOP-05 | Swiping up, Back, Home or tapping outside shall close the Top Menu. | M |
| FR-TOP-06 | Long-pressing an empty area of Home shall also open the Top Menu. | S |
| FR-TOP-07 | The setting "Swipe down on Home opens" shall offer the Top Menu (default) or the system notification shade. | C |

### 4.8 Gestures and interaction

| ID | Requirement | P |
|---|---|---|
| FR-GEST-01 | Every view transition (Home and App Grid, Home and Events, Home and Top Menu) shall follow the finger 1:1 from the moment the gesture is recognized. | M |
| FR-GEST-02 | On release, a transition shall complete if the drag covered at least 30% of its distance or was flung in that direction; otherwise it shall return to where it started (peek). | M |
| FR-GEST-03 | A transition in progress shall be interruptible: touching the screen catches it, and the user can drag it either way. | M |
| FR-GEST-04 | Gestures shall ignore touches that start in system gesture areas, and shall lock to one direction once the finger has clearly moved. | M |
| FR-GEST-05 | Pulley menu: pulling a page down past its top reveals the items one at a time; the item at the current pull depth is highlighted, with a haptic tick whenever the highlight moves; releasing selects it; releasing before the first item cancels. A subtle indicator at the top of the page shows that a pulley menu is available. | M |
| FR-GEST-06 | Remorse timer: destructive actions (clear notifications, remove folder, sort A–Z, reset layout) shall show a banner with the action's name and a 5-second countdown bar. Tapping the banner cancels; otherwise the action runs when the countdown ends, even if the user has navigated away. | M |
| FR-GEST-07 | Long-press shall use the system's long-press duration and be confirmed with a haptic tick. | M |
| FR-GEST-08 | Haptic feedback shall follow the system's touch-feedback setting and can be turned off in Settings. | M |
| FR-GEST-09 | Every gesture-only action shall also be available to screen-reader users as an accessibility action: open the App Grid, Events and the Top Menu, choose any pulley menu item, and cancel a remorse timer. | M |

### 4.9 Animation

Motion principles: physical, continuous, interruptible, quick and purposeful. Nothing animates only to decorate, and no animation blocks input.

| Token | Value | Used for |
|---|---|---|
| Short | 150 ms | Press feedback, small fades, search results |
| Medium | 250 ms | Settling after release, folders, context menus, reflow |
| Long | 400 ms | Appearance changes, large layout changes |
| Ambient | 800 ms | Wallpaper and highlight-color crossfades |
| Enter easing | Decelerate | Elements arriving or settling |
| Exit easing | Accelerate | Elements leaving |
| Release | Critically damped spring, no visible overshoot | After a drag is released |
| Rubber band | Resistance that grows with distance | Pulley menus, overscroll |

| ID | Requirement | P |
|---|---|---|
| FR-ANIM-01 | Opening and closing the App Grid, Events and the Top Menu shall be continuous slides driven by the gesture. The wallpaper dims (and blurs, where supported) in proportion to the App Grid's progress. | M |
| FR-ANIM-02 | Launching an app shall show its window growing from the tapped icon or cover. | M |
| FR-ANIM-03 | Returning to the launcher shall settle Home into place (from 96% scale and partly transparent to full, Medium duration). | S |
| FR-ANIM-04 | Pressed items shall show a glow in the highlight color within one frame of touch, fading out over the Short duration after release. | M |
| FR-ANIM-05 | Covers and grid items shall animate to their new positions whenever items are added, removed or moved, or the column count changes. | M |
| FR-ANIM-06 | A dragged item shall grow slightly (about 110%) and cast a shadow while other items make room. | M |
| FR-ANIM-07 | Folders shall expand from their tile and collapse back into it. | M |
| FR-ANIM-08 | Context menus shall slide open beneath their item, pushing later rows down, and close in reverse. | M |
| FR-ANIM-09 | Pulley menus shall stretch with rubber-band resistance; the chosen item flashes in the highlight color before the menu closes. | M |
| FR-ANIM-10 | A new wallpaper shall crossfade in over the Ambient duration while the highlight color moves to its new value. It shall never be applied in the middle of a gesture. | M |
| FR-ANIM-11 | Appearance changes shall crossfade all launcher colors over the Long duration without reloading the screen. | M |
| FR-ANIM-12 | The remorse countdown bar shall shrink smoothly; cancelling restores the affected item with a reverse animation. | M |
| FR-ANIM-13 | Gesture hints (in the tutorial and in context) shall show a glowing touch point tracing the gesture. | S |
| FR-ANIM-14 | Moving between Home and Events shall shift the wallpaper slightly (parallax of at most 5% of the screen width). | C |
| FR-ANIM-15 | When the system's "remove animations" setting is on, or the launcher's Animations setting is Reduced, transitions shall be instant or simple fades of at most 100 ms. Drags still follow the finger. | M |

### 4.10 Bing wallpaper

| ID | Requirement | P |
|---|---|---|
| FR-WP-01 | With "Daily Bing wallpaper" on (the default), the launcher shall set the Bing image of the day for the selected market as the device's home-screen wallpaper. | M |
| FR-WP-02 | The launcher shall check for a new image when Home is shown and the last check is more than 3 hours old, and in the background at most every 3 hours while a network is available. An image shall be downloaded only when it differs from the current one. | M |
| FR-WP-03 | The market setting shall default to Automatic, which maps the device's language and region to the closest supported Bing market, falling back to en-US. The user can choose a specific market instead. | M |
| FR-WP-04 | The launcher shall use the image size that best fits the screen: a portrait version when one is available, otherwise the landscape image cropped around its center to the screen's shape. Images shall never be stretched or distorted. | M |
| FR-WP-05 | "Also use on lock screen" (off by default) shall apply the image to the lock screen as well. | S |
| FR-WP-06 | "Download on: Wi-Fi only" (the default is any network) shall limit downloads to unmetered networks. Downloads shall wait while the system's Data Saver or Battery Saver is on. | S |
| FR-WP-07 | The launcher shall cache the 8 most recent images with their details; older images are deleted unless pinned. | M |
| FR-WP-08 | Pinning an image, from the Top Menu or Wallpaper details, shall stop daily changes until the user chooses "Daily" again. | M |
| FR-WP-09 | Wallpaper details shall show the image, title, copyright text, date, market and "Learn more", plus the time of the last successful update. Its pulley menu shall offer "Refresh now", "Keep this wallpaper" or "Resume daily", and "Wallpaper settings". | M |
| FR-WP-10 | The title and copyright text shall be shown wherever the image is identified: the Events card and Wallpaper details. | M |
| FR-WP-11 | If a check or download fails, the current wallpaper shall stay. Retries use increasing delays, at most 3 per day, and no error dialogs are shown. | M |
| FR-WP-12 | Until the first image has been downloaded, the existing system wallpaper shall be used. | M |
| FR-WP-13 | Turning "Daily Bing wallpaper" off shall stop all checks and downloads, delete the cache, hide the Ambiences row and leave the current system wallpaper as it is. | M |
| FR-WP-14 | A downloaded file shall be confirmed to be a valid image within the size limit (IF-WP-05) before it is applied. | M |
| FR-WP-15 | Saving, sharing or exporting Bing images (not offered; see C-4). | W |

### 4.11 Ambience and highlight color

| ID | Requirement | P |
|---|---|---|
| FR-AMB-01 | The highlight color shall be derived automatically from the current wallpaper, as a prominent, saturated color from the image, in the manner of Sailfish OS ambiences. | M |
| FR-AMB-02 | The derived color shall be adjusted for each appearance (lighter in Dark, darker in Light) to meet the contrast targets in NFR-A11Y-03. | M |
| FR-AMB-03 | When the wallpaper was not set by the launcher, the highlight color shall come from the colors the system reports for the wallpaper, or a default color when none is available. | M |
| FR-AMB-04 | The highlight color shall be used for page headers, pressed glows, selected and active states, the remorse bar and pulley highlights, but not for body text. | M |
| FR-AMB-05 | Users can replace the automatic highlight color with one of 8 preset colors, and switch back to Automatic. | C |

### 4.12 Appearance: Light, Dark, System

| ID | Requirement | P |
|---|---|---|
| FR-THM-01 | The launcher shall offer three appearance modes: Light, Dark and System. | M |
| FR-THM-02 | The default mode shall be System. | M |
| FR-THM-03 | In System mode, the launcher shall follow the device's dark-theme setting, including scheduled and Battery Saver changes. It shall switch within 1 second, without restarting and without losing the current view, scroll position or search text. | M |
| FR-THM-04 | If the device has no system-wide dark-theme setting, System mode shall look like Light. | M |
| FR-THM-05 | Dark shows light text and icons on dark translucent glass; Light shows dark text and icons on light translucent glass. The wallpaper itself is never altered, but scrims adapt so text stays legible. | M |
| FR-THM-06 | Choosing Light or Dark explicitly shall override the device setting until the user changes it. | M |
| FR-THM-07 | The mode shall apply to every launcher surface: Home, App Grid, Events, Top Menu, folders, menus, remorse banners, Settings, Wallpaper details and the tutorial. | M |
| FR-THM-08 | Status bar and navigation bar icons shall switch between light and dark to stay legible over the current surface. | M |
| FR-THM-09 | The mode can be changed in Settings and in the Top Menu, and takes effect immediately (FR-ANIM-11). | M |

### 4.13 Settings

Settings is a launcher page opened from the Top Menu. Its header is right-aligned in the highlight color, and its pulley menu offers "Replay tutorial" and "About". Changes take effect immediately; there is no Save button.

| ID | Setting | Options | Default | P |
|---|---|---|---|---|
| FR-SET-01 | Appearance | System, Light, Dark | System | M |
| FR-SET-02 | Daily Bing wallpaper | On, Off | On | M |
| FR-SET-03 | Bing market | Automatic, or a specific supported market | Automatic | M |
| FR-SET-04 | Also use on lock screen | On, Off | Off | S |
| FR-SET-05 | Download on | Any network, Wi-Fi only | Any network | S |
| FR-SET-06 | Highlight color | Automatic, 8 presets | Automatic | C |
| FR-SET-07 | Time and date on Home | Show, Hide | Show | S |
| FR-SET-08 | Covers show | Favorites, Recently used | Favorites | C |
| FR-SET-09 | App Grid columns | 3, 4, 5 | 4 | S |
| FR-SET-10 | Sort App Grid A–Z | Action, with remorse timer | — | S |
| FR-SET-11 | Swipe down on Home opens | Top Menu, Notification shade | Top Menu | C |
| FR-SET-12 | Notifications in Events | On, Off | Off | S |
| FR-SET-13 | Notification dots | On, Off | Off | C |
| FR-SET-14 | Haptic feedback | Follow system, Off | Follow system | M |
| FR-SET-15 | Animations | Follow system, Reduced | Follow system | M |
| FR-SET-16 | Reset layout | Action, with remorse timer: restores the first-run App Grid and Home; settings and wallpaper are kept | — | M |
| FR-SET-17 | About | Version, licenses, image attribution, "Not affiliated with Jolla or Microsoft" | — | M |

### 4.14 First run and tutorial

| ID | Requirement | P |
|---|---|---|
| FR-ONB-01 | First run shall offer to make Selfish Launcher the default Home app (FR-GEN-02), sort the App Grid alphabetically, pre-fill Home (FR-HOME-07) and start downloading the wallpaper in the background. | M |
| FR-ONB-02 | A skippable three-step tutorial shall teach: swipe up for apps, swipe sideways for Events, swipe down for the Top Menu. Each step completes when the user performs the gesture. | S |
| FR-ONB-03 | The tutorial can be replayed from Settings. | S |
| FR-ONB-04 | Features that need special access (notifications in Events, notification dots, recently used covers) shall ask for it only when the user turns them on, after a one-screen explanation, and shall turn themselves off cleanly if access is later revoked. | M |

---

## 5. External interface requirements

### 5.1 User interface

| ID | Requirement | P |
|---|---|---|
| UI-01 | Every launcher surface shall float over the wallpaper as translucent glass; there are no opaque full-screen backgrounds. | M |
| UI-02 | Page headers (Events, Settings, Wallpaper details, folders) shall be large, aligned to the trailing edge (right in left-to-right languages) and drawn in the highlight color. | M |
| UI-03 | There shall be no toolbars, action bars, floating buttons or tab bars. Actions live in pulley menus, context menus and the Top Menu. | M |
| UI-04 | Typography shall use one clean sans-serif family in four sizes (display for the clock, header, body, label) with two emphasis levels (primary, secondary). | M |
| UI-05 | App icons shall be shown at one uniform size, with a consistent rounded shape for icons that support it. | M |
| UI-06 | Spacing shall follow a 4 dp scale, and touch targets shall be at least 48 × 48 dp. | M |
| UI-07 | Layouts shall respect display cutouts, rounded corners and system bars; only the wallpaper runs edge to edge. | M |
| UI-08 | Colors, sizes, corner radii and durations shall come from one shared set of design tokens (AR-04). | M |
| UI-09 | The launcher shall make no sounds of its own. | M |

### 5.2 Platform capabilities

Described as capabilities, not as platform APIs.

| Capability | Used for | Need |
|---|---|---|
| Default Home app role | Being the home screen | Required |
| List of launchable apps, with change events | App Grid, Home, folders | Required |
| Launching apps; opening app info and the system uninstall flow | App actions | Required |
| Setting the home (and lock) screen wallpaper | Bing wallpaper | Required |
| Wallpaper colors reported by the system | Highlight color (FR-AMB-03) | Required |
| Device dark-theme setting and its changes | System appearance mode | Required |
| Haptics and the system touch-feedback setting | Gesture feedback | Required |
| System animation setting | Reduced motion | Required |
| Network state (connected, metered), Data Saver, Battery Saver | Wallpaper downloads | Required |
| Background scheduling with a network condition | Wallpaper checks | Required |
| Notification access, granted by the user | Notifications in Events, notification dots | Optional |
| Usage access, granted by the user | Recently used covers | Optional |
| Apps' published shortcuts | Cover actions, context menus | Optional |
| Opening the system notification shade | "Swipe down on Home opens" setting | Optional |

### 5.3 Network interface: Bing image of the day

| ID | Requirement | P |
|---|---|---|
| IF-WP-01 | The launcher shall read Bing's image-of-the-day details for a market and use: the image's identity (a hash, or start date plus market), start date, image address, title, copyright text and "learn more" link. | M |
| IF-WP-02 | All requests shall use HTTPS, and redirects to non-HTTPS addresses shall be rejected. | M |
| IF-WP-03 | Requests shall carry no user or device identifiers and no stored cookies; only the market and image size vary. | M |
| IF-WP-04 | One request may return details for up to 8 recent days, used to fill the cache on first run and after gaps. | S |
| IF-WP-05 | Image downloads shall be limited to 5 MB; larger or unreadable responses are discarded. | M |
| IF-WP-06 | Missing optional fields shall be tolerated; for example, without a title the copyright text is used. | M |

> **Informative.** Third-party apps commonly use `https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US`, which returns JSON image entries with `startdate`, `urlbase`, `url`, `title`, `copyright`, `copyrightlink` and `hsh`; size variants such as a portrait `_1080x1920.jpg` can be requested from `urlbase`. This feed is undocumented and unofficial, so confirm availability and terms of use before relying on it (Q-3).

---

## 6. Non-functional requirements

### 6.1 Performance

Targets apply to the reference device (Q-2).

| ID | Requirement | P |
|---|---|---|
| NFR-PERF-01 | Home shall be usable within 200 ms of a Home press when the launcher is in memory, and within 1.5 s of a cold start. | M |
| NFR-PERF-02 | Transitions, drags and App Grid scrolling with 300 installed apps shall render at the display's refresh rate, with at least 95% of frames on time. | M |
| NFR-PERF-03 | Touch feedback shall appear within one frame, and an app's launch animation shall start within 50 ms of the tap. | M |
| NFR-PERF-04 | Search results shall update within 50 ms of each keystroke with 300 installed apps. | M |
| NFR-PERF-05 | Steady-state memory use shall stay below 150 MB with 300 installed apps (target to be confirmed on the reference device). | S |
| NFR-PERF-06 | Downloading, decoding and applying a wallpaper shall not cause dropped frames in the visible UI. | M |
| NFR-PERF-07 | The app's download size shall be under 10 MB. | S |

### 6.2 Reliability

| ID | Requirement | P |
|---|---|---|
| NFR-REL-01 | The launcher shall pass the full acceptance suite and an 8-hour automated random-input stress test without crashing or freezing. | M |
| NFR-REL-02 | The launcher shall never show an empty or black background; the last good wallpaper is always available. | M |
| NFR-REL-03 | Unreadable or corrupt saved data shall fall back to defaults without crashing, keeping whatever is still valid. | M |
| NFR-REL-04 | Everything except wallpaper updates shall work offline. | M |
| NFR-REL-05 | App changes while a view is open shall not reset its scroll position or close it. | M |

### 6.3 Battery, data and storage

| ID | Requirement | P |
|---|---|---|
| NFR-PWR-01 | The only background work shall be the wallpaper check: at most 8 checks and, normally, 1 image download per day. | M |
| NFR-PWR-02 | The launcher shall not keep the device awake or do work while idle beyond that check. | M |
| NFR-PWR-03 | Typical mobile data use shall stay under 40 MB per month. | S |
| NFR-PWR-04 | The wallpaper cache shall use less than 25 MB of storage. | M |

### 6.4 Privacy and security

| ID | Requirement | P |
|---|---|---|
| NFR-PRIV-01 | The launcher's only network connections shall be to Bing's image feed and image servers. Links the user opens go to the browser. | M |
| NFR-PRIV-02 | There shall be no analytics, telemetry, advertising, crash-reporting services or accounts. | M |
| NFR-PRIV-03 | All data shall stay on the device, in storage private to the app. | M |
| NFR-PRIV-04 | The launcher shall use only the capabilities listed in §5.2, asking for optional ones as described in FR-ONB-04. | M |
| NFR-PRIV-05 | Notification contents shall only be displayed: never stored or sent anywhere. | M |
| NFR-PRIV-06 | Other apps shall not be able to change the launcher's layout, settings or wallpaper choice. | M |
| NFR-PRIV-07 | The launcher shall not require Google Play Services. | M |

### 6.5 Accessibility

| ID | Requirement | P |
|---|---|---|
| NFR-A11Y-01 | Every element shall have a screen-reader label: covers and icons read the app's name; folders read "Folder", their name and the number of apps. | M |
| NFR-A11Y-02 | All gesture-only actions shall be available as accessibility actions (FR-GEST-09). | M |
| NFR-A11Y-03 | Text and controls shall meet WCAG 2.1 AA contrast (4.5:1 for body text, 3:1 for large text and UI components) on glass, in both appearances and over any wallpaper, adding scrim where needed. | M |
| NFR-A11Y-04 | Layouts shall stay usable at the largest system font size, with no clipped or overlapping text. | M |
| NFR-A11Y-05 | Reduced motion shall be honored as described in FR-ANIM-15. | M |
| NFR-A11Y-06 | Haptics shall never be the only feedback for an action. | M |

### 6.6 Localization

| ID | Requirement | P |
|---|---|---|
| NFR-L10N-01 | All user-visible text shall be translatable; English is the source language for 1.0. | M |
| NFR-L10N-02 | Dates, times (12- or 24-hour) and numbers shall follow the device's regional settings. | M |
| NFR-L10N-03 | Right-to-left languages shall mirror layouts and header alignment. Sideways gestures already work in both directions. | S |

### 6.7 Compatibility

| ID | Requirement | P |
|---|---|---|
| NFR-COMP-01 | Gesture navigation and 3-button navigation shall both be fully supported. | M |
| NFR-COMP-02 | Display cutouts, rounded corners and refresh rates from 60 to 144 Hz shall be supported. | M |
| NFR-COMP-03 | Where background blur is not available, a stronger dim or tint shall keep text legible and look intentional. | M |

### 6.8 Maintainability

| ID | Requirement | P |
|---|---|---|
| NFR-MNT-01 | Domain rules (layout operations, wallpaper scheduling and selection, appearance resolution, search ranking) shall have automated tests that run without a device. | M |
| NFR-MNT-02 | Each M and S requirement shall be traceable to at least one test or review checklist item. | S |

---

## 7. Architecture constraints

The brief asks for a simple architecture. The constraints below keep the design small, understandable and testable without choosing any technology.

```
┌──────────────────────────────────────────────────────────────┐
│ PRESENTATION                                                 │
│ Home · App Grid · Events · Top Menu · Folders · Search ·     │
│ Wallpaper details · Settings · Tutorial                      │
│ shared: Gesture & Motion · Design tokens & Ambience          │
└──────────────────────▲─────────────────────┬─────────────────┘
                state  │                     │  actions
┌──────────────────────┴─────────────────────▼─────────────────┐
│ DOMAIN                                                       │
│ Launcher state · Layout rules · Wallpaper policy ·           │
│ Theme resolver · Search ranking                              │
└──────────────────────▲─────────────────────┬─────────────────┘
                 data  │                     │  commands
┌──────────────────────┴─────────────────────▼─────────────────┐
│ ADAPTERS (one per external dependency)                       │
│ App catalog · Local storage · System wallpaper · Bing feed · │
│ Scheduler · OS settings (theme, motion, haptics) ·           │
│ Notifications (optional) · Usage (optional)                  │
└──────────────────────────────────────────────────────────────┘
```

| ID | Constraint |
|---|---|
| AR-01 | There shall be exactly three layers: Presentation, Domain and Adapters. Dependencies point downward only; Presentation never talks to the OS or the network directly. |
| AR-02 | There shall be one source of truth: a single launcher state assembled by the Domain. Presentation renders that state and sends user actions back (one-way data flow). |
| AR-03 | Each external dependency (app catalog, storage, system wallpaper, Bing feed, scheduler, OS settings, notifications, usage) shall be reached through exactly one adapter with a narrow interface that tests can replace with a fake. |
| AR-04 | One shared gesture-and-motion component and one set of design tokens shall serve all views, so thresholds, durations, curves, colors and sizes are defined once. |
| AR-05 | There shall be no server, no accounts, no plugin or extension system and no remote configuration. |
| AR-06 | There shall be one kind of background job: the wallpaper check. |
| AR-07 | Saved data shall carry a schema version and be migrated forward on upgrade; unreadable data falls back to defaults (NFR-REL-03). |
| AR-08 | Third-party components shall be kept to a minimum, and each one shall be justified in the technical design. |
| AR-09 | Optional features (notifications, usage, app shortcuts) shall be isolated so that disabling or removing one doesn't affect the rest. |

---

## 8. Data requirements

| Data | Contents | Storage and lifetime |
|---|---|---|
| Layout | App Grid order (apps and folders), folder names and contents, Home covers in order, schema version. Apps are identified by package, launch entry and user profile. | On the device until reset or uninstall; included in the platform's standard backup. |
| Settings | The values in §4.13. | On the device; included in backup. |
| Wallpaper cache | Up to 8 images plus any pinned ones, each with identity, date, market, title, copyright text, link and derived highlight color. | On the device, private; excluded from backup because it can be downloaded again; deleted when Bing wallpaper is turned off. |
| Notifications | The current list only. | In memory only; never saved. |
| Recently used apps | The current list only, if enabled. | In memory only; never saved. |

No personal data leaves the device.

---

## 9. Out of scope for 1.0

| Item | Reason |
|---|---|
| Replacing the lock screen | Not possible for a launcher (C-2). |
| Sailfish OS edge swipes inside apps, closing apps, live covers of running apps | Not possible for a launcher (C-2). |
| Toggling Wi-Fi, Bluetooth and other system settings from the Top Menu | Restricted by the OS; the Top Menu links to system Settings instead. |
| Home-screen widgets | Not part of Sailfish OS's Home, and keeps the launcher simple. |
| Icon packs and custom icons | Simplicity. |
| Wallpaper sources other than Bing, or choosing your own image in the launcher | Simplicity; with Bing turned off, the system wallpaper picker still works. |
| Saving or sharing Bing images | Copyright (C-4). |
| Web search, weather, calendar and news in Events | Privacy and simplicity. |
| Landscape, tablets and foldables | Phones first. |
| Syncing the layout across devices | No accounts or backend (AR-05). |
| Hiding or locking apps | Focus for 1.0. |

---

## 10. Acceptance criteria

| ID | Scenario | Verifies |
|---|---|---|
| AC-01 | On a fresh install with the device in dark theme, Home appears in Dark (mode: System). Switching the device to light theme crossfades the launcher to Light within 1 s without leaving the current view. | FR-THM-02, FR-THM-03 |
| AC-02 | With Appearance set to Light and the device in dark theme, every launcher surface stays Light. | FR-THM-06, FR-THM-07 |
| AC-03 | With Bing wallpaper on and a network available, a newly published image is applied within 3 hours, or at the next return to Home after 3 hours, with a crossfade; the highlight color updates and Events shows the image's title and copyright. | FR-WP-01, FR-WP-02, FR-WP-10, FR-AMB-01, FR-ANIM-10 |
| AC-04 | After 3 days without a network, the last image is still shown, no error dialog has appeared, and Wallpaper details shows the time of the last update. | FR-WP-09, FR-WP-11, NFR-REL-02 |
| AC-05 | On Home, dragging up 20% of the distance and releasing slowly returns to Home; dragging past 30%, or flinging up, opens the App Grid. | FR-GEST-02 |
| AC-06 | In Events with notifications, pulling down slowly highlights the menu items one by one with a haptic tick each; releasing on "Clear notifications" shows a 5-second remorse banner; tapping it cancels and nothing is cleared. | FR-GEST-05, FR-GEST-06, FR-EVT-06 |
| AC-07 | Installing an app while the App Grid is open adds it at the end of the grid with an animation, without changing the scroll position. | FR-GRID-05, NFR-REL-05 |
| AC-08 | Dropping app A onto app B creates "Folder" containing both; dragging B back out dissolves the folder and leaves A in its place. | FR-FOLD-01, FR-FOLD-06 |
| AC-09 | With the system's animations removed, view changes are instant or fades of at most 100 ms, and drags still follow the finger. | FR-ANIM-15 |
| AC-10 | With a screen reader on, a user can open the App Grid, Events and the Top Menu and launch an app without performing any swipe. | FR-GEST-09, NFR-A11Y-02 |
| AC-11 | Monitoring network traffic for 24 hours shows connections only to Bing's image feed and image servers. | NFR-PRIV-01 |

---

## 11. Open questions

| ID | Question | Affects |
|---|---|---|
| Q-1 | What is the minimum Android version? | FR-THM-04, NFR-COMP-03, permission flows |
| Q-2 | Which device is the performance reference? | §6.1 |
| Q-3 | May a third-party launcher use Bing images and the unofficial feed, and on what terms? | §4.10, §5.3, distribution |
| Q-4 | Is "Selfish Launcher" clear of trademark conflicts, and how should the non-affiliation notice be worded? | FR-GEN-01, FR-SET-17 |
| Q-5 | Which market should Automatic fall back to (currently en-US)? | FR-WP-03 |
| Q-6 | Should work-profile apps be in 1.0 (currently Could)? | FR-GEN-10 |
| Q-7 | Which distribution channels (app stores, direct download)? | Updates, backup behavior |

---

## Appendix A. Traceability to the brief

| Brief item | Covered by |
|---|---|
| Create an Android launcher | §2.1, §4.1, §5.2 |
| Named Selfish Launcher | FR-GEN-01 |
| Animation | §4.9, FR-GEST-01 to FR-GEST-03, NFR-PERF-02, NFR-PERF-03, NFR-A11Y-05 |
| Bing wallpaper | §4.10, §4.11, §5.3 |
| Dark / light / system, system by default | §4.12, FR-TOP-02, FR-SET-01 |
| Simple architecture | §7, NFR-MNT-01 |
| Sailfish OS as the reference for UX and UI | §2.2, §3, §4.2 to §4.8, §5.1 |
