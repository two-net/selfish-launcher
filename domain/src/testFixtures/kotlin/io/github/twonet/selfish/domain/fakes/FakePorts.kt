package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.ports.AppCatalog
import io.github.twonet.selfish.domain.ports.BingFeed
import io.github.twonet.selfish.domain.ports.LocalStorage
import io.github.twonet.selfish.domain.ports.NotificationSource
import io.github.twonet.selfish.domain.ports.OsSettings
import io.github.twonet.selfish.domain.ports.Ports
import io.github.twonet.selfish.domain.ports.SystemWallpaper
import io.github.twonet.selfish.domain.ports.UsageSource
import io.github.twonet.selfish.domain.ports.WallpaperScheduler

/**
 * A [Ports] made of fakes. The optional sources default to their `Disabled`
 * implementations, so store tests run without them (AR-09, DESIGN §8.3).
 */
fun fakePorts(
    appCatalog: AppCatalog = FakeAppCatalog(),
    localStorage: LocalStorage = FakeLocalStorage(),
    systemWallpaper: SystemWallpaper = FakeSystemWallpaper(),
    bingFeed: BingFeed = FakeBingFeed(),
    wallpaperScheduler: WallpaperScheduler = FakeWallpaperScheduler(),
    osSettings: OsSettings = FakeOsSettings(),
    notificationSource: NotificationSource = NotificationSource.Disabled,
    usageSource: UsageSource = UsageSource.Disabled,
) = Ports(
    appCatalog = appCatalog,
    localStorage = localStorage,
    systemWallpaper = systemWallpaper,
    bingFeed = bingFeed,
    wallpaperScheduler = wallpaperScheduler,
    osSettings = osSettings,
    notificationSource = notificationSource,
    usageSource = usageSource,
)
