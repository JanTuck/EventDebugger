package me.jantuck.eventdebugger

import com.esotericsoftware.reflectasm.MethodAccess
import com.okkero.skedule.schedule
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.plugin.java.JavaPlugin
import org.reflections.Reflections
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("UNCHECKED_CAST")
class EventDebugger : JavaPlugin() {
    companion object {
        val logger: Logger = LoggerFactory.getLogger("EventDebugger")
    }

    override fun onLoad() {
        logger.info(
            "If you see a warning after this message it is purely java being a bitch."
        )
        MethodAccess.get(this.javaClass) // Just to have that error message show before remapping and shit.
    }

    override fun onEnable() {
        saveDefaultConfig()
        this.schedule {
            waitFor(20)
            remapExactSubscriptions()
            tryRemapAllCancellable()
        }
    }

    /**
     * Tries to remap all the things listed in config
     */
    private fun remapExactSubscriptions() {
        val section = config.getConfigurationSection("exact") ?: return
        section.getKeys(false).forEach {
            val clazz = Class.forName(section.getString("$it.class"))
            val methods = section.getStringList("$it.methods")
            EventRemapper.remapAndSubscribe(clazz as Class<out Event>, methods)
        }
    }

    /**
     * Tries to remap all events implementing Cancellable
     */
    private fun tryRemapAllCancellable() {
        val section = config.getConfigurationSection("other") ?: return
        if (!section.getBoolean("listen-to-all-cancellable", false)) return
        Reflections.log = EventDebugger.logger // Prefer own logger, so it looks nicer.
        val nameSpaces = section.getStringList("cancellable-namespaces")
        val ignored = section.getStringList("ignore-cancellable")
        val isCancelledMethod = listOf("isCancelled")
        nameSpaces.forEach { namespace ->
            val reflections = Reflections(namespace)
            reflections
                .getSubTypesOf(Cancellable::class.java)
                .filter {
                    it.declaredFields.any { field -> field.type.name.endsWith("HandlerList") }
                            && !ignored.contains(it.name)
                }
                .forEach {
                    EventRemapper.remapAndSubscribe(it as Class<out Event>, isCancelledMethod)
                }
        }
    }
}