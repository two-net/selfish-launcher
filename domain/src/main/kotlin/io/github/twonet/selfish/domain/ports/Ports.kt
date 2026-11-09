package io.github.twonet.selfish.domain.ports

/**
 * Every external dependency of the launcher, each reached through exactly one
 * port (AR-03). The composition root fills this with Android adapters; tests
 * fill it with the fakes from the test fixtures.
 */
class Ports(
    val appCatalog: AppCatalog,
    val localStorage: LocalStorage,
    val systemWallpaper: SystemWallpaper,
    val bingFeed: BingFeed,
    val wallpaperScheduler: WallpaperScheduler,
    val osSettings: OsSettings,
    /** Optional (AR-09): [NotificationSource.Disabled] while off. */
    val notificationSource: NotificationSource,
    /** Optional (AR-09): [UsageSource.Disabled] while off. */
    val usageSource: UsageSource,
)
