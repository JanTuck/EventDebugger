package me.jantuck.eventdebugger

import com.esotericsoftware.reflectasm.MethodAccess
import com.google.common.collect.ArrayListMultimap
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.RegisteredListener
import java.lang.reflect.Method

object EventRemapper {
    /*
    Executor field from RegisteredListener class, uses this to apply our executor on "top" of the normal one.
     */
    private val executorField =
        RegisteredListener::class.java.getDeclaredField("executor").apply { this.isAccessible = true }

    // Our cache to hold our methodaccess, index of method and method name.
    private val subscribedMethodForEvent =
        ArrayListMultimap.create<Class<out Event>, Triple<String, Int, MethodAccess>>()


    private fun getHandlerListStaticMethod(clazz: Class<*>): Method? =
        clazz.declaredMethods.find { it.name == "getHandlerList" && it.returnType == HandlerList::class.java /* Unnecessary check?*/ }

    /**
     * Traverses to try and find the HandlerList of an event.
     */
    private fun tryGetHandlerList(clazz: Class<out Event>): HandlerList? {
        val handlerListMethod = getHandlerListStaticMethod(clazz)
        if (handlerListMethod != null) return handlerListMethod.invoke(null) as HandlerList

        // run super class first to ignore interfaces if it is present in super class.
        val superClass = clazz.superclass
        val superClassMethod = getHandlerListStaticMethod(superClass)
        if (superClassMethod != null)
            return superClassMethod.invoke(null) as HandlerList

        val interfaces = clazz.interfaces
        interfaces.forEach {
            val method = getHandlerListStaticMethod(it)
            if (method != null)
                return method.invoke(null) as HandlerList
        }
        return null
    }

    /**
     * Remaps shit
     */
    fun remapAndSubscribe(clazz: Class<out Event>, subscribed: List<String>) {
        if (subscribed.any { it.equals("callevent", true) }) // Meh
            throw java.lang.RuntimeException("You are not allowed to subscribe to method callEvent")
        val handlerList =
            tryGetHandlerList(clazz) ?: throw RuntimeException("Could not find HandlerList of ${clazz.simpleName}")
        val listeners = handlerList.registeredListeners
        listeners.forEach {
            val oldExecutor = executorField.get(it) as EventExecutor
            val classPathForListener = it.listener.javaClass.name
            val newExecutor = EventExecutor { listener1, event1 ->
                executeAndCheckChanges(
                    { event -> oldExecutor.execute(listener1, event) },
                    event1,
                    it,
                    classPathForListener
                )
            }
            executorField.set(it, newExecutor)
            EventDebugger.logger.info("> Listener for '${clazz.simpleName}' from '${it.plugin.name}' remapped.")
            EventDebugger.logger.info("-> Subscribed to (${subscribed.joinToString(", ")})")
            EventDebugger.logger.info("--> Event priority '${it.priority}'")
        }
        // Cache MethodAccess and index for subscribed methods, of course also cache the method name.
        val methodAccess = MethodAccess.get(clazz)
        subscribed.forEach {
            subscribedMethodForEvent.put(clazz, Triple(it, methodAccess.getIndex(it), methodAccess))
        }
    }

    /**
     * Executes the event and checks for changes, this is inlined cause of the "lambda"
     */
    private inline fun executeAndCheckChanges(
        executionMethod: (Event) -> Unit,
        event: Event,
        registeredListener: RegisteredListener,
        classPath: String
    ) {
        val oldValues = returnCurrentValues(event)
        executionMethod.invoke(event)
        val newValues = returnCurrentValues(event)
        val differences = returnDifferences(oldValues, newValues)
        val logger = EventDebugger.logger
        if (differences.isNotEmpty()) {
            logger.info("EventDebugger START")
            logger.info("-> Change in event '${event.eventName}'")
            logger.info("-> Classpath for listener '$classPath'")
            logger.info("-> Caused by '${registeredListener.plugin.name}'")
            differences.forEach { (t, u) -> logger.info("-> '$t' changed from '${u.first}' to '${u.second}'") }
            logger.info("EventDebugger END")
        }
    }


    private val cachedMethodAccess =
        mutableMapOf<Class<*>, Pair<Int, MethodAccess>>() // Caches index, and methodaccess for further use.

    /**
     * Tries and cloning an object via the .clone method on the object (any)
     */
    private fun tryCloneAny(any: Any?): Any? {
        if (any !is Cloneable) return any // Assuming it can't be cloned if they don't implement this interface.
        if (any is ItemStack) return any.clone() // Quick lookup, instead of using MethodAccess this should be fine.
        val anyClass = any::class.java
        val handle = cachedMethodAccess[anyClass]
        if (handle != null) return handle.second.invoke(any, handle.first)
        return try {
            val lookup = MethodAccess.get(anyClass)
            val index = lookup.getIndex("clone")
            cachedMethodAccess[anyClass] = index to lookup
            lookup.invoke(any, index)
        } catch (e: Exception) {
            any // Unable to invoke or find the method, return any. (Note, that other issues might occur and catching exception seems quite... meh)
        }
    }

    private fun returnCurrentValues(event: Event): Map<String, Any?> =
        subscribedMethodForEvent.get(event.javaClass).map {
            it.first to it.third.invoke(event, it.second)?.let { any -> tryCloneAny(any) }
        }.toMap()

    private fun returnDifferences(before: Map<String, Any?>, after: Map<String, Any?>): Map<String, Pair<Any?, Any?>> =
        before
            .map { it.key to (it.value to after[it.key]) } // Make the map template we want
            .filter { it.second.first != it.second.second } // Check for changes.
            .toMap()
}