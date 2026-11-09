# Review checklist

Requirements verified by review (level **R** in DESIGN §8.1). Each item starts
with the SRS IDs it covers; `checkTraceability` (#27) will read them. Feature
issues add their items here. Every release PR signs off the whole list.

## Architecture (SRS §7)

- [ ] [AR-02] One store: all launcher state lives in `LauncherStore` in `:domain`; views hold only transient UI state (drag progress, scroll positions, running animations).
- [ ] [AR-04] One gesture-and-motion component (`ui/motion/LayerGestureController`) and one token set (`ui/tokens/`) serve every view. `LayerRulesTest` catches literal colors, sizes and durations; this item covers what it can't, such as copied easing curves.
- [ ] [AR-05] No server, accounts, plugin or extension system, or remote configuration.
- [ ] [AR-06] The merged manifest declares exactly one `JobService`, `WallpaperJobService`, and nothing else runs in the background.
- [ ] [AR-08] Every component on the release runtime classpath is justified in DESIGN §7, and `gradle/runtime-dependencies.txt` changed in the same PR as §7.
