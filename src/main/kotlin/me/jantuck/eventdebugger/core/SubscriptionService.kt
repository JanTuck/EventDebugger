package me.jantuck.eventdebugger.core

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.plugin.java.JavaPlugin
import org.reflections.Reflections
import java.lang.reflect.Modifier

internal class SubscriptionService(
    private val plugin: JavaPlugin,
    private val remapper: EventRemapper
) {
    fun load() {
        loadExactSubscriptions()
        loadCancellableSubscriptions()
        remapper.publishSubscriptions()
    }

    fun configured(): List<ConfiguredSubscription> {
        val section = plugin.config.getConfigurationSection("exact") ?: return emptyList()
        val disabled = plugin.config.getStringList("settings.disabled-subscriptions").toSet()
        return section.getKeys(false).sorted().map { key ->
            ConfiguredSubscription(
                key = key,
                className = section.getString("$key.class") ?: "<missing class>",
                methods = section.getStringList("$key.methods"),
                disabled = key in disabled
            )
        }
    }

    fun toggle(key: String) {
        val disabled = plugin.config.getStringList("settings.disabled-subscriptions").toMutableSet()
        if (!disabled.add(key)) disabled.remove(key)
        plugin.config.set("settings.disabled-subscriptions", disabled.sorted())
        plugin.saveConfig()
    }

    fun add(className: String, methods: List<String>): String? {
        val eventClass = resolveEventClass(className) ?: return "The event class is no longer available."
        val invalidMethod = methods.firstOrNull { method ->
            method.equals("callEvent", ignoreCase = true) ||
                eventClass.methods.none { it.name == method && it.parameterCount == 0 }
        }
        if (invalidMethod != null) {
            return "'$invalidMethod' is not an allowed zero-argument method on ${eventClass.simpleName}."
        }

        val baseKey = "Custom_${eventClass.simpleName}".replace(Regex("[^A-Za-z0-9_-]"), "_")
        var key = baseKey
        var suffix = 2
        while (plugin.config.contains("exact.$key")) key = "${baseKey}_${suffix++}"
        plugin.config.set("exact.$key.class", eventClass.name)
        plugin.config.set("exact.$key.methods", methods.distinct())
        plugin.saveConfig()
        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun resolveEventClass(className: String): Class<out Event>? {
        val classLoaders = sequenceOf(plugin.javaClass.classLoader) +
            plugin.server.pluginManager.plugins.asSequence().map { it.javaClass.classLoader }
        classLoaders.distinct().forEach { classLoader ->
            val type = runCatching { Class.forName(className, false, classLoader) }.getOrNull() ?: return@forEach
            if (Event::class.java.isAssignableFrom(type)) return type as Class<out Event>
        }
        return null
    }

    private fun loadExactSubscriptions() {
        val section = plugin.config.getConfigurationSection("exact") ?: return
        val disabled = plugin.config.getStringList("settings.disabled-subscriptions").toSet()
        section.getKeys(false).forEach { key ->
            if (key in disabled) return@forEach
            val className = section.getString("$key.class")
            if (className.isNullOrBlank()) {
                plugin.logger.warning("Skipping exact subscription '$key': class is missing.")
                return@forEach
            }

            try {
                val eventClass = resolveEventClass(className)
                if (eventClass == null) {
                    plugin.logger.warning(
                        "Skipping exact subscription '$key': '$className' was not found or is not a Bukkit event."
                    )
                    return@forEach
                }
                remapper.subscribe(eventClass, section.getStringList("$key.methods"))
            } catch (exception: Exception) {
                plugin.logger.warning("Skipping exact subscription '$key': ${exception.messageOf()}")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadCancellableSubscriptions() {
        if (!plugin.config.getBoolean("other.listen-to-all-cancellable", false)) return
        val ignoredClasses = plugin.config.getStringList("other.ignore-cancellable").toHashSet()
        plugin.config.getStringList("other.cancellable-namespaces").forEach { namespace ->
            val startedAt = System.nanoTime()
            try {
                val eventClasses = Reflections(namespace)
                    .getSubTypesOf(Cancellable::class.java)
                    .asSequence()
                    .filter { Event::class.java.isAssignableFrom(it) }
                    .filterNot { Modifier.isAbstract(it.modifiers) || it.name in ignoredClasses }
                    .map { it as Class<out Event> }
                    .sortedBy(Class<out Event>::getName)
                    .toList()

                val added = eventClasses.sumOf { remapper.subscribe(it, listOf("isCancelled")) }
                val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
                plugin.logger.info(
                    "Loaded $added cancellable event subscriptions from '$namespace' in ${elapsedMillis}ms."
                )
            } catch (exception: Exception) {
                plugin.logger.warning(
                    "Could not scan cancellable namespace '$namespace': ${exception.messageOf()}"
                )
            }
        }
    }
}
