package me.jantuck.eventdebugger.core

import com.esotericsoftware.reflectasm.MethodAccess
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

internal data class EventMethodReader(
    val name: String,
    private val index: Int,
    private val access: MethodAccess
) {
    fun read(event: Event): Any? = access.invoke(event, index)
}

internal data class EventSubscription(
    val eventClass: Class<out Event>,
    val readers: List<EventMethodReader>,
    val handlerList: HandlerList
)

internal class EventSubscriptionRegistry(private val logger: Logger) {
    private val pendingReaders = LinkedHashMap<Class<out Event>, LinkedHashMap<String, EventMethodReader>>()
    private val handlerLists = LinkedHashMap<Class<out Event>, HandlerList>()
    private val resolvedReaders = ConcurrentHashMap<Class<out Event>, List<EventMethodReader>>()

    @Volatile
    var subscriptions: List<EventSubscription> = emptyList()
        private set

    val eventTypeCount: Int
        get() = subscriptions.size

    var watchedMethodCount: Int = 0
        private set

    fun clear() {
        subscriptions = emptyList()
        watchedMethodCount = 0
        pendingReaders.clear()
        handlerLists.clear()
        resolvedReaders.clear()
    }

    fun subscribe(eventClass: Class<out Event>, requestedMethods: List<String>): Int {
        val methodNames = requestedMethods.map(String::trim).filter(String::isNotEmpty)
        if (methodNames.any { it.equals("callEvent", ignoreCase = true) }) {
            logger.warning("Skipping '${eventClass.name}.callEvent': invoking callEvent while inspecting an event would recurse.")
        }

        val handlerList = findHandlerList(eventClass)
        if (handlerList == null) {
            logger.warning("Skipping '${eventClass.name}': no static getHandlerList() method was found.")
            return 0
        }

        val access = try {
            MethodAccess.get(eventClass)
        } catch (exception: Exception) {
            logger.warning("Skipping '${eventClass.name}': methods could not be inspected (${exception.messageOf()}).")
            return 0
        }

        val readers = pendingReaders.getOrPut(eventClass, ::LinkedHashMap)
        handlerLists[eventClass] = handlerList
        var added = 0
        methodNames.filterNot { it.equals("callEvent", ignoreCase = true) }.forEach { methodName ->
            if (methodName in readers) return@forEach
            try {
                readers[methodName] = EventMethodReader(methodName, access.getIndex(methodName, 0), access)
                added++
            } catch (_: Exception) {
                logger.warning("Skipping '${eventClass.name}.$methodName': a zero-argument method was not found.")
            }
        }
        return added
    }

    fun publish() {
        subscriptions = pendingReaders.mapNotNull { (eventClass, methods) ->
            val handlerList = handlerLists[eventClass] ?: return@mapNotNull null
            methods.values.takeIf { it.isNotEmpty() }
                ?.let { EventSubscription(eventClass, it.toList(), handlerList) }
        }
        watchedMethodCount = subscriptions.sumOf { it.readers.size }
        resolvedReaders.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun readersFor(eventClass: Class<out Event>): List<EventMethodReader> =
        resolvedReaders.computeIfAbsent(eventClass) { actualClass ->
            val readers = LinkedHashMap<String, EventMethodReader>()
            subscriptions.forEach { subscription ->
                if (subscription.eventClass.isAssignableFrom(actualClass)) {
                    subscription.readers.forEach { readers.putIfAbsent(it.name, it) }
                }
            }
            readers.values.toList()
        }

    private fun findHandlerList(eventClass: Class<out Event>): HandlerList? {
        var current: Class<*>? = eventClass
        while (current != null && Event::class.java.isAssignableFrom(current)) {
            findHandlerListMethod(current)?.let { method ->
                return runCatching { method.invoke(null) as HandlerList }.getOrNull()
            }
            current = current.superclass
        }
        return findHandlerListOnInterfaces(eventClass.interfaces.toList())
    }

    private fun findHandlerListOnInterfaces(interfaces: List<Class<*>>): HandlerList? {
        interfaces.forEach { interfaceClass ->
            findHandlerListMethod(interfaceClass)?.let { method ->
                return runCatching { method.invoke(null) as HandlerList }.getOrNull()
            }
            findHandlerListOnInterfaces(interfaceClass.interfaces.toList())?.let { return it }
        }
        return null
    }

    private fun findHandlerListMethod(type: Class<*>): Method? = type.declaredMethods.firstOrNull {
        it.name == "getHandlerList" &&
            it.parameterCount == 0 &&
            it.returnType == HandlerList::class.java &&
            Modifier.isStatic(it.modifiers)
    }?.apply { isAccessible = true }
}

internal fun Throwable.messageOf(): String = message ?: javaClass.simpleName
