package io.github.twonet.selfish

import android.app.Activity
import android.content.Context
import io.github.twonet.selfish.adapters.ForegroundActivity
import io.github.twonet.selfish.adapters.apps.LauncherAppsCatalog
import io.github.twonet.selfish.adapters.bing.HttpsBingFeed
import io.github.twonet.selfish.adapters.notifications.ListenerNotificationSource
import io.github.twonet.selfish.adapters.os.AndroidOsSettings
import io.github.twonet.selfish.adapters.scheduler.JobSchedulerWallpaperScheduler
import io.github.twonet.selfish.adapters.storage.FileStorage
import io.github.twonet.selfish.adapters.usage.UsageStatsSource
import io.github.twonet.selfish.adapters.wallpaper.AndroidSystemWallpaper
import io.github.twonet.selfish.domain.ports.Ports
import io.github.twonet.selfish.domain.state.LauncherStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The composition root (DESIGN §3.4): the only file that sees `:adapters`.
 * It wires one adapter per port into the one store, by plain constructor
 * injection. The store lives as long as the process.
 */
class AppGraph(context: Context) {
    private val foreground = ForegroundActivity()

    private val ports = Ports(
        appCatalog = LauncherAppsCatalog(context),
        localStorage = FileStorage(context),
        systemWallpaper = AndroidSystemWallpaper(context),
        bingFeed = HttpsBingFeed(),
        wallpaperScheduler = JobSchedulerWallpaperScheduler(context),
        osSettings = AndroidOsSettings(context, foreground),
        // Optional features (AR-09): deleting one means deleting its
        // package and this line, and passing its Disabled object instead.
        notificationSource = ListenerNotificationSource(context),
        usageSource = UsageStatsSource(context),
    )

    val store = LauncherStore(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ports = ports,
    )

    /** Called by the Activity once it is created, so adapters can show system prompts over it (DESIGN §5.1). */
    fun attach(activity: Activity) = foreground.attach(activity)

    /** Called by the Activity when it is destroyed. */
    fun detach(activity: Activity) = foreground.detach(activity)
}
