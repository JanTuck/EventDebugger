package me.jantuck.eventdebugger

import com.esotericsoftware.reflectasm.MethodAccess
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.reflections.Reflections
import java.util.logging.Logger

@Suppress("UNCHECKED_CAST")
class EventDebugger : JavaPlugin() {
    companion object {
        lateinit var logger: Logger
    }

    override fun onLoad() {
        EventDebugger.logger = this.logger
        logger.info(
            "If you see a warning after this message it is purely java being a bitch."
        )
        MethodAccess.get(this.javaClass) // Just to have that error message show before remapping and shit.
    }

    override fun onEnable() {
        saveDefaultConfig()
        object : BukkitRunnable() {
            private var subscriptionsLoaded = false

            override fun run() {
                if (!subscriptionsLoaded) {
                    subscriptionsLoaded = true
                    loadSubscriptions()
                }
                EventRemapper.remapSubscribedEvents()
            }
        }.runTaskTimer(this, 20, 20)
    }

    override fun onDisable() {
        EventRemapper.restoreOriginalExecutors()
    }

    private fun loadSubscriptions() {
        subscribeExact()
        subscribeAllCancellable()
    }

    /**
     * Tries to subscribe all the things listed in config
     */
    private fun subscribeExact() {
        val section = config.getConfigurationSection("exact") ?: return
        section.getKeys(false).forEach {
            try {
                val className = section.getString("$it.class")
                if (className.isNullOrBlank()) {
                    logger.warning("Skipping exact subscription '$it': missing class.")
                    return@forEach
                }
                val methods = section.getStringList("$it.methods")
                val clazz = Class.forName(className)
                if (!Event::class.java.isAssignableFrom(clazz)) {
                    logger.warning("Skipping exact subscription '$it': '$className' is not a Bukkit event.")
                    return@forEach
                }
                EventRemapper.subscribe(clazz as Class<out Event>, methods)
            } catch (e: ClassNotFoundException) {
                logger.warning("Skipping exact subscription '$it': class not found (${e.message}).")
            } catch (e: Exception) {
                logger.warning("Skipping exact subscription '$it': ${e.message}")
            }
        }
    }

    /**
     * Tries to subscribe all events implementing Cancellable
     */
    private fun subscribeAllCancellable() {
        val section = config.getConfigurationSection("other") ?: return
        if (!section.getBoolean("listen-to-all-cancellable", false)) return
        val nameSpaces = section.getStringList("cancellable-namespaces")
        val ignored = section.getStringList("ignore-cancellable")
        val isCancelledMethod = listOf("isCancelled")
        nameSpaces.forEach { nameSpace ->
            try {
                val reflections = Reflections(nameSpace)
                reflections
                    .getSubTypesOf(Cancellable::class.java)
                    .filter {
                        it.declaredFields.any { field -> field.type.name.endsWith("HandlerList") }
                                && !ignored.contains(it.name)
                                && Event::class.java.isAssignableFrom(it)
                    }
                    .forEach {
                        EventRemapper.subscribe(it as Class<out Event>, isCancelledMethod)
                    }
            } catch (e: Exception) {
                logger.warning("Could not scan cancellable namespace '$nameSpace': ${e.message}")
            }
        }

    }
}
