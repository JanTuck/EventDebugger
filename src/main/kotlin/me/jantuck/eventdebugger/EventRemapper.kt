package me.jantuck.eventdebugger

import com.esotericsoftware.reflectasm.MethodAccess
import com.google.common.collect.ArrayListMultimap
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.RegisteredListener

object EventRemapper {
    /*
    Executor field from RegisteredListener class, uses this to apply our executor on "top" of the normal one.
     */
    private val executorField =
        RegisteredListener::class.java.getDeclaredField("executor").apply { this.isAccessible = true }

    // Our cache to hold our methodaccess, index of method and method name.
    private val subscribedMethodForEvent =
        ArrayListMultimap.create<Class<out Event>, Triple<String, Int, MethodAccess>>()

    fun remapAndSubscribe(clazz: Class<out Event>, subscribed: List<String>) {
        val handlerList = clazz.getDeclaredMethod("getHandlerList").invoke(null) as HandlerList
        val listeners = handlerList.registeredListeners
        listeners.forEach {
            val oldExecutor = executorField.get(it) as EventExecutor
            val newExecutor = EventExecutor { listener1, event1 ->
                executeAndCheckChanges({ event -> oldExecutor.execute(listener1, event) }, event1, it)
            }
            executorField.set(it, newExecutor)
            EventDebugger.logger.info("-> Listener for '${clazz.simpleName}' from '${it.plugin.name}' remapped.")
            EventDebugger.logger.info("--> Subscribed to (${subscribed.joinToString(", ")})")
        }
        // Cache values
        val methodAccess = MethodAccess.get(clazz)
        subscribed.forEach {
            subscribedMethodForEvent.put(clazz, Triple(it, methodAccess.getIndex(it), methodAccess))
        }
    }

    private inline fun executeAndCheckChanges(
        executionMethod: (Event) -> Unit,
        event: Event,
        registeredListener: RegisteredListener
    ) {
        val oldValues = returnCurrentValues(event)
        executionMethod.invoke(event)
        val newValues = returnCurrentValues(event)
        val differences = returnDifferences(oldValues, newValues)
        val logger = EventDebugger.logger
        if (differences.isNotEmpty()) {
            logger.info("EventDebugger START")
            logger.info("-> Change in event '${event.eventName}'")
            logger.info("-> Caused by '${registeredListener.plugin.name}'")
            differences.forEach { (t, u) -> logger.info("-> '$t' changed from '${u.first}' to '${u.second}'") }
            logger.info("EventDebugger END")
        }
    }

    private fun returnCurrentValues(event: Event): Map<String, Any> =
        subscribedMethodForEvent.get(event.javaClass).map {
            it.first to it.third.invoke(event, it.second).let { any ->
                (any as? ItemStack)?.clone() ?: any
            }
        }.toMap()

    private fun returnDifferences(before: Map<String, Any>, after: Map<String, Any>): Map<String, Pair<Any, Any?>> =
        before
            .filter { after[it.key] != it.value }
            .map { it.key to (it.value to after[it.key]) }
            .toMap()
}