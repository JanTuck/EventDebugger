package me.jantuck.eventdebugger.core

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

internal class DebuggerController(private val plugin: JavaPlugin) : Listener {
    val remapper = EventRemapper(plugin.logger, plugin.name)
    private val subscriptions = SubscriptionService(plugin, remapper)

    var enabled: Boolean = true
        private set

    var historyEnabled: Boolean = false
        private set

    var historyLimit: Int = 100
        private set

    var listenerRefreshTicks: Long = 200L
        private set

    var uiEnabled: Boolean = true
        private set

    var preferModernDialogs: Boolean = true
        private set

    var cancellableNamespaces: List<String> = emptyList()
        private set

    private var includedPlugins = emptySet<String>()
    private var excludedPlugins = emptySet<String>()
    private var refreshTask: BukkitTask? = null

    fun start() = reload(reloadFile = false)

    fun stop() {
        refreshTask?.cancel()
        refreshTask = null
        remapper.restoreOriginalExecutors()
        remapper.clearSubscriptions()
    }

    fun reload(reloadFile: Boolean = true) {
        refreshTask?.cancel()
        refreshTask = null
        remapper.restoreOriginalExecutors()
        remapper.clearSubscriptions()
        if (reloadFile) plugin.reloadConfig()

        readConfiguration()
        remapper.configure(
            RemapperSettings(
                includedPlugins = includedPlugins,
                excludedPlugins = excludedPlugins,
                logRemaps = plugin.config.getBoolean("settings.log-remaps", false),
                maxValueLength = plugin.config.getInt("settings.max-value-length", 200).coerceAtLeast(20),
                historyEnabled = historyEnabled,
                historyLimit = historyLimit
            )
        )
        subscriptions.load()

        if (enabled) {
            refreshListeners()
            scheduleRefreshes()
        }
    }

    fun refreshListeners(): Int = if (enabled) remapper.remapSubscribedEvents() else 0

    fun setEnabled(value: Boolean): Boolean {
        if (enabled == value) return false
        enabled = value
        plugin.config.set("enabled", value)
        plugin.saveConfig()
        if (value) {
            refreshListeners()
            scheduleRefreshes()
        } else {
            refreshTask?.cancel()
            refreshTask = null
            remapper.restoreOriginalExecutors()
        }
        return true
    }

    fun toggleHistory() {
        plugin.config.set("history.enabled", !historyEnabled)
        plugin.saveConfig()
        reload()
    }

    fun applySettings(debugging: Boolean, history: Boolean, limit: Int, refreshTicks: Long) {
        plugin.config.set("enabled", debugging)
        plugin.config.set("history.enabled", history)
        plugin.config.set("history.max-entries", limit.coerceIn(10, 500))
        plugin.config.set("settings.refresh-interval-ticks", refreshTicks.coerceAtLeast(0L))
        plugin.saveConfig()
        reload()
    }

    fun configuredSubscriptions(): List<ConfiguredSubscription> = subscriptions.configured()

    fun toggleSubscription(key: String) {
        subscriptions.toggle(key)
        reload()
    }

    fun resolveEventClass(className: String) = subscriptions.resolveEventClass(className)

    fun addSubscription(className: String, methods: List<String>): String? =
        subscriptions.add(className, methods).also { error -> if (error == null) reload() }

    fun isPluginWatched(pluginName: String): Boolean {
        val normalized = pluginName.lowercase()
        return normalized !in excludedPlugins && (includedPlugins.isEmpty() || normalized in includedPlugins)
    }

    fun togglePlugin(pluginName: String) {
        val normalized = pluginName.lowercase()
        val included = includedPlugins.toMutableSet()
        val excluded = excludedPlugins.toMutableSet()
        if (isPluginWatched(pluginName)) {
            excluded += normalized
        } else {
            excluded -= normalized
            if (included.isNotEmpty()) included += normalized
        }
        plugin.config.set("settings.include-plugins", included.sorted())
        plugin.config.set("settings.exclude-plugins", excluded.sorted())
        plugin.saveConfig()
        reload()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginEnabled(event: PluginEnableEvent) {
        if (!enabled || event.plugin === plugin) return
        plugin.server.scheduler.runTask(plugin, Runnable(::refreshListeners))
    }

    private fun readConfiguration() {
        enabled = plugin.config.getBoolean("enabled", true)
        historyEnabled = plugin.config.getBoolean("history.enabled", false)
        historyLimit = plugin.config.getInt("history.max-entries", 100).coerceIn(1, 1000)
        listenerRefreshTicks = plugin.config.getLong("settings.refresh-interval-ticks", 200L).coerceAtLeast(0L)
        uiEnabled = plugin.config.getBoolean("ui.enabled", true)
        preferModernDialogs = plugin.config.getBoolean("ui.prefer-modern-dialogs", true)
        cancellableNamespaces = plugin.config.getStringList("other.cancellable-namespaces")
            .takeIf { plugin.config.getBoolean("other.listen-to-all-cancellable", false) }
            .orEmpty()
        includedPlugins = normalizedSet("settings.include-plugins")
        excludedPlugins = normalizedSet("settings.exclude-plugins")
    }

    private fun scheduleRefreshes() {
        refreshTask?.cancel()
        if (!enabled || listenerRefreshTicks == 0L) return
        refreshTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable(::refreshListeners),
            listenerRefreshTicks,
            listenerRefreshTicks
        )
    }

    private fun normalizedSet(path: String): Set<String> = plugin.config.getStringList(path)
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(String::lowercase)
        .toSet()
}
