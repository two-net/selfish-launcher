package io.github.twonet.selfish.adapters.apps

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import io.github.twonet.selfish.adapters.ForegroundActivity
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.AppCatalog
import io.github.twonet.selfish.domain.ports.AppIconImage
import io.github.twonet.selfish.domain.ports.AppShortcut
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.ports.SystemScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * [AppCatalog] over `LauncherApps` and `UserManager` (DESIGN §5.1).
 *
 * [excludedPackage] is left out of the list; it is the launcher itself
 * (DESIGN §5.1). Tests pass `null` to see their own stub activity.
 * [foreground] supplies the view a launched app's window grows from
 * (FR-ANIM-02).
 */
class LauncherAppsCatalog internal constructor(
    context: Context,
    private val excludedPackage: String?,
    private val foreground: ForegroundActivity = ForegroundActivity(),
) : AppCatalog {
    constructor(context: Context, foreground: ForegroundActivity) :
        this(context, context.applicationContext.packageName, foreground)

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    /**
     * How many times each package has changed in this process, per profile.
     * It becomes [AppEntry.version], so Presentation reloads an icon after
     * an update (FR-GEN-06).
     */
    private val versions = ConcurrentHashMap<PackageKey, Long>()

    /** Each profile's last successfully read list. */
    private val lastLists = ConcurrentHashMap<UserHandle, List<AppEntry>>()

    /** Each profile's badge layer by size; an empty array means no badge. */
    private val badges = ConcurrentHashMap<Pair<UserHandle, Int>, IntArray>()

    private data class PackageKey(val packageName: String, val user: UserHandle)

    override fun apps(): Flow<List<AppEntry>> =
        changes()
            .conflate()
            .map { query() }
            .flowOn(Dispatchers.IO)

    /**
     * Emits once right away, then after every package event in any profile
     * (install, removal, update, enable or disable), after a profile is
     * added, removed, paused or resumed (FR-GEN-10), and after a locale
     * change, which changes the apps' names.
     */
    private fun changes(): Flow<Unit> = callbackFlow {
        fun changed(user: UserHandle, vararg packageNames: String) {
            packageNames.forEach { versions.merge(PackageKey(it, user), 1L) { old, one -> old + one } }
            trySend(Unit)
        }

        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed(user, packageName)

            override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(user, packageName)

            override fun onPackageChanged(packageName: String, user: UserHandle) = changed(user, packageName)

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                changed(user, *packageNames)

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                changed(user, *packageNames)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply { BROADCASTS.forEach(::addAction) }

        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        // After registering, so no change between the first query and the
        // registration is missed.
        trySend(Unit)
        awaitClose {
            launcherApps.unregisterCallback(callback)
            appContext.unregisterReceiver(receiver)
        }
    }

    /**
     * The launchable apps of every profile. A profile that can't be read
     * keeps its last list, or is left out if it was never read: a passing
     * error must not empty a profile, because the Domain would then remove
     * its apps from the layout, and one unreadable profile must not hide
     * the others.
     */
    private fun query(): List<AppEntry> =
        userManager.userProfiles.flatMap { user ->
            try {
                queryProfile(user).also { lastLists[user] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // For example a SecurityException for a profile that is
                // being removed, or one the launcher may not see.
                lastLists[user].orEmpty()
            }
        }

    private fun queryProfile(user: UserHandle): List<AppEntry> {
        val serial = userManager.getSerialNumberForUser(user)
        return launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != excludedPackage }
            .map { info ->
                val component = info.componentName
                AppEntry(
                    id = AppId(component.packageName, component.className, serial),
                    label = info.label.toString(),
                    version = versions[PackageKey(component.packageName, user)] ?: 0L,
                    // System apps can't be uninstalled, only have their
                    // updates removed, so the menu doesn't offer it
                    // (FR-GRID-06), as the platform's launcher does.
                    removable = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0,
                )
            }
    }

    override suspend fun icon(app: AppId, sizePx: Int): AppIconImage? = withContext(Dispatchers.IO) {
        require(sizePx > 0) { "sizePx must be positive" }
        val user = userManager.getUserForSerialNumber(app.userSerial) ?: return@withContext null
        val info = find(app, user) ?: return@withContext null
        val drawable = info.getIcon(appContext.resources.displayMetrics.densityDpi) ?: return@withContext null
        AppIconImage(
            width = sizePx,
            height = sizePx,
            argb = render(sizePx) { canvas -> draw(drawable, canvas, sizePx) },
            shaped = drawable is AdaptiveIconDrawable,
            badge = badge(user, sizePx),
        )
    }

    /** Looked up in the list rather than resolved, so it also finds the system's stand-in entries for apps without a launcher activity. */
    private fun find(app: AppId, user: UserHandle): LauncherActivityInfo? =
        launcherApps.getActivityList(app.packageName, user).firstOrNull { it.componentName.className == app.activityName }

    /**
     * Draws [drawable] to fill a [size] square. An adaptive icon is drawn
     * full-bleed, without the device's mask, so Presentation can give every
     * icon the same shape (UI-05). Other icons keep their aspect ratio.
     */
    private fun draw(drawable: Drawable, canvas: Canvas, size: Int) {
        if (drawable is AdaptiveIconDrawable) {
            // Setting the bounds lays out both layers 50% larger, centred,
            // so the canvas shows exactly the icon's visible viewport.
            drawable.setBounds(0, 0, size, size)
            drawable.background?.draw(canvas)
            drawable.foreground?.draw(canvas)
        } else {
            drawable.bounds = fit(drawable.intrinsicWidth, drawable.intrinsicHeight, size)
            drawable.draw(canvas)
        }
    }

    /**
     * The badge the system adds to [user]'s icons, alone on a transparent
     * layer of [size], or `null` for the main profile (FR-GEN-10).
     */
    private fun badge(user: UserHandle, size: Int): IntArray? {
        if (user == Process.myUserHandle()) return null
        val layer = badges.getOrPut(user to size) {
            val blank = BitmapDrawable(appContext.resources, Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888))
            val badged = appContext.packageManager.getUserBadgedIcon(blank, user)
            if (badged === blank) {
                IntArray(0)
            } else {
                render(size) { canvas -> badged.setBounds(0, 0, size, size); badged.draw(canvas) }
                    .takeIf { pixels -> pixels.any { it != 0 } } ?: IntArray(0)
            }
        }
        return layer.takeIf { it.isNotEmpty() }
    }

    override suspend fun launch(app: AppId, from: Bounds?) {
        val user = userManager.getUserForSerialNumber(app.userSerial) ?: return
        val component = ComponentName(app.packageName, app.activityName)
        val bounds = from?.let { Rect(it.left, it.top, it.right, it.bottom) }
        withContext(Dispatchers.Main) {
            launcherApps.startMainActivity(component, user, bounds, growFrom(bounds))
        }
    }

    /**
     * Options that grow the app's window from [bounds], the tapped icon or
     * cover (FR-ANIM-02), or `null` without bounds or a visible launcher.
     * The bounds are in the launcher window's coordinates, which is what
     * the scale-up animation expects relative to the window's root view.
     * Call on the main thread, which [ForegroundActivity] needs.
     */
    private fun growFrom(bounds: Rect?): Bundle? {
        if (bounds == null || bounds.isEmpty) return null
        val root = foreground.current?.window?.decorView ?: return null
        return ActivityOptions.makeScaleUpAnimation(root, bounds.left, bounds.top, bounds.width(), bounds.height()).toBundle()
    }

    /**
     * The apps the device opens for calls, messages, web links and photos,
     * for the first-run Home (FR-HOME-07). Each is looked up on its own, so
     * one that fails doesn't lose the others.
     */
    override suspend fun defaultApps(): DefaultApps = withContext(Dispatchers.IO) {
        DefaultApps(
            phone = defaultFor(Intent(Intent.ACTION_DIAL)),
            messaging = defaultFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            browser = defaultFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE)),
            camera = defaultFor(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        )
    }

    /**
     * The launchable app of the main profile that the system starts for
     * [intent] without asking, or `null` if there is none. With several
     * handlers and no default, the system answers with its own resolver,
     * which has no launchable entry, so it is skipped too. The intents are
     * declared in the manifest's `<queries>` (DESIGN §5.6).
     */
    private fun defaultFor(intent: Intent): AppId? = try {
        val packageName = appContext.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.takeUnless { it == SYSTEM_PACKAGE || it == excludedPackage }
        val user = Process.myUserHandle()
        packageName
            ?.let { launcherApps.getActivityList(it, user).firstOrNull() }
            ?.let { AppId(it.componentName.packageName, it.componentName.className, userManager.getSerialNumberForUser(user)) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** The system's app-info screen for [app], growing from nothing (FR-GRID-06). */
    override suspend fun openAppInfo(app: AppId) {
        val user = userManager.getUserForSerialNumber(app.userSerial) ?: return
        withContext(Dispatchers.Main) {
            launcherApps.startAppDetailsActivity(ComponentName(app.packageName, app.activityName), user, null, null)
        }
    }

    /**
     * The system's uninstall confirmation for [app], for its own profile
     * (FR-GRID-06). Started over the visible launcher when there is one,
     * so Back returns to it; needs `REQUEST_DELETE_PACKAGES` (DESIGN §5.6).
     * The removal itself arrives as a catalog change (FR-GRID-09).
     */
    override suspend fun requestUninstall(app: AppId) {
        val user = userManager.getUserForSerialNumber(app.userSerial) ?: return
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, user)
        withContext(Dispatchers.Main) {
            val activity = foreground.current
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    // Shortcuts arrive with FR-HOME-09 and FR-GRID-10 (both Could, deferred).

    override suspend fun shortcuts(app: AppId): List<AppShortcut> = emptyList()

    override suspend fun launchShortcut(app: AppId, shortcutId: String, from: Bounds?) = Unit

    /**
     * Opens [screen] over the launcher, so Back returns to it. A link opens
     * in the default browser, and only if it is `https` (FR-EVT-03); the
     * Top Menu's shortcut opens the system Settings app (FR-TOP-04). The
     * usage access screen arrives with #19; notification access is the
     * notification source's own (DESIGN §5.5).
     */
    override suspend fun open(screen: SystemScreen) {
        val intent = when (screen) {
            is SystemScreen.Link -> {
                val uri = Uri.parse(screen.url)
                if (uri.scheme != "https") return
                Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            }
            SystemScreen.SystemSettings -> Intent(Settings.ACTION_SETTINGS)
            SystemScreen.NotificationAccess, SystemScreen.UsageAccess -> return
        }
        withContext(Dispatchers.Main) {
            val activity = foreground.current
            try {
                if (activity != null) {
                    activity.startActivity(intent)
                } else {
                    appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (_: ActivityNotFoundException) {
                // No browser, or no Settings app: nothing to open it in.
            }
        }
    }

    private companion object {
        /** The platform's own package, which holds the intent resolver. */
        const val SYSTEM_PACKAGE = "android"

        /** Broadcasts that change the list without a package event. Only registered receivers get the profile ones. */
        val BROADCASTS = listOf(
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MANAGED_PROFILE_ADDED,
            Intent.ACTION_MANAGED_PROFILE_REMOVED,
            Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
        )

        /** Renders into a fresh [size] square and returns its ARGB pixels. */
        fun render(size: Int, draw: (Canvas) -> Unit): IntArray {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                draw(Canvas(bitmap))
                return IntArray(size * size).also { bitmap.getPixels(it, 0, size, 0, 0, size, size) }
            } finally {
                bitmap.recycle()
            }
        }

        /** The largest rectangle with the aspect ratio [width]:[height] centred in a [size] square. */
        fun fit(width: Int, height: Int, size: Int): Rect {
            if (width <= 0 || height <= 0 || width == height) return Rect(0, 0, size, size)
            val scale = size.toFloat() / maxOf(width, height)
            val w = (width * scale).roundToInt()
            val h = (height * scale).roundToInt()
            val left = (size - w) / 2
            val top = (size - h) / 2
            return Rect(left, top, left + w, top + h)
        }
    }
}
