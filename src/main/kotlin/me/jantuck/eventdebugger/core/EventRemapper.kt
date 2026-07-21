package me.jantuck.eventdebugger.core

import org.bukkit.event.Event
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.RegisteredListener
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger

internal class EventRemapper(
    private val logger: Logger,
    ownPluginName: String
) {
    private class MutableActivity(val eventName: String, val eventClass: String) {
        val inspectedCalls = AtomicLong()
        val changedValues = AtomicLong()

        @Volatile
        var lastPlugin: String? = null

        @Volatile
        var lastChangedMethod: String? = null

        @Volatile
        var lastSeen: Long = 0
    }

    private data class DetectedChange(val method: String, val before: Any?, val after: Any?)

    private val ownPluginName = ownPluginName.lowercase()
    private val subscriptions = EventSubscriptionRegistry(logger)
    private val snapshotter = ValueSnapshotter()
    private val executorField = runCatching {
        RegisteredListener::class.java.getDeclaredField("executor").apply { isAccessible = true }
    }.getOrNull()

    private val originalExecutors = IdentityHashMap<RegisteredListener, EventExecutor>()
    private val installedExecutors = IdentityHashMap<RegisteredListener, EventExecutor>()
    private val failedValueReads = ConcurrentHashMap.newKeySet<String>()
    private val pluginInspection = ConcurrentHashMap<String, Boolean>()
    private val inspectedCalls = AtomicLong()
    private val detectedChanges = AtomicLong()
    private val recordIds = AtomicLong()
    private val activityByClass = ConcurrentHashMap<String, MutableActivity>()

    private val historyLock = Any()
    private val history = ArrayDeque<ChangeRecord>()
    private val recordsById = HashMap<Long, ChangeRecord>()
    private val latestRecordByEvent = HashMap<String, ChangeRecord>()

    @Volatile
    private var settings = RemapperSettings(emptySet(), emptySet(), false, 200, false, 100)

    private var reportedMissingExecutorField = false

    fun configure(settings: RemapperSettings) {
        this.settings = settings
        pluginInspection.clear()
        trimHistory()
    }

    fun clearSubscriptions() {
        subscriptions.clear()
        failedValueReads.clear()
    }

    fun subscribe(eventClass: Class<out Event>, methods: List<String>): Int =
        subscriptions.subscribe(eventClass, methods)

    fun publishSubscriptions() = subscriptions.publish()

    fun remapSubscribedEvents(): Int {
        val field = executorField
        if (field == null) {
            if (!reportedMissingExecutorField) {
                logger.warning("RegisteredListener.executor is unavailable; this server version cannot be instrumented.")
                reportedMissingExecutorField = true
            }
            return 0
        }

        var remapped = 0
        val activeListeners = Collections.newSetFromMap(IdentityHashMap<RegisteredListener, Boolean>())
        subscriptions.subscriptions.asSequence()
            .map(EventSubscription::handlerList)
            .distinct()
            .forEach { handlerList ->
                handlerList.registeredListeners.forEach listenerLoop@{ registeredListener ->
                    activeListeners.add(registeredListener)
                    if (!shouldInspect(registeredListener.plugin.name)) return@listenerLoop

                    try {
                        val currentExecutor = field.get(registeredListener) as EventExecutor
                        if (installedExecutors[registeredListener] === currentExecutor) return@listenerLoop

                        originalExecutors[registeredListener] = currentExecutor
                        val listenerClass = registeredListener.listener.javaClass.name
                        val wrappedExecutor = EventExecutor { listener, event ->
                            inspectExecution(
                                execution = { currentExecutor.execute(listener, event) },
                                event = event,
                                registeredListener = registeredListener,
                                listenerClass = listenerClass
                            )
                        }
                        field.set(registeredListener, wrappedExecutor)
                        installedExecutors[registeredListener] = wrappedExecutor
                        remapped++

                        if (settings.logRemaps) {
                            logger.info(
                                "Watching ${registeredListener.plugin.name} listener $listenerClass " +
                                    "at ${registeredListener.priority} priority."
                            )
                        }
                    } catch (exception: Exception) {
                        logger.warning(
                            "Could not instrument a ${registeredListener.plugin.name} listener: ${exception.messageOf()}"
                        )
                    }
                }
            }

        originalExecutors.keys.removeIf { it !in activeListeners }
        installedExecutors.keys.removeIf { it !in activeListeners }
        return remapped
    }

    fun restoreOriginalExecutors() {
        val field = executorField ?: return
        originalExecutors.forEach { (listener, original) ->
            try {
                if (field.get(listener) === installedExecutors[listener]) field.set(listener, original)
            } catch (exception: Exception) {
                logger.warning("Could not restore a listener executor: ${exception.messageOf()}")
            }
        }
        originalExecutors.clear()
        installedExecutors.clear()
    }

    fun status(): DebuggerStatus = DebuggerStatus(
        eventTypes = subscriptions.eventTypeCount,
        watchedMethods = subscriptions.watchedMethodCount,
        wrappedListeners = installedExecutors.size,
        inspectedCalls = inspectedCalls.get(),
        detectedChanges = detectedChanges.get()
    )

    fun activity(): List<EventActivity> = activityByClass.values.map { activity ->
        EventActivity(
            eventName = activity.eventName,
            eventClass = activity.eventClass,
            inspectedCalls = activity.inspectedCalls.get(),
            changedValues = activity.changedValues.get(),
            lastPlugin = activity.lastPlugin,
            lastChangedMethod = activity.lastChangedMethod,
            lastSeen = activity.lastSeen
        )
    }.sortedByDescending(EventActivity::lastSeen)

    fun history(): List<ChangeRecord> = synchronized(historyLock) { history.toList().asReversed() }

    fun record(id: Long): ChangeRecord? = synchronized(historyLock) { recordsById[id] }

    fun latestRecord(eventClass: String): ChangeRecord? =
        synchronized(historyLock) { latestRecordByEvent[eventClass] }

    fun clearHistory() {
        synchronized(historyLock) {
            history.clear()
            recordsById.clear()
            latestRecordByEvent.clear()
        }
    }

    private fun shouldInspect(pluginName: String): Boolean =
        pluginInspection.computeIfAbsent(pluginName.lowercase()) { normalized ->
            normalized != ownPluginName &&
                normalized !in settings.excludedPlugins &&
                (settings.includedPlugins.isEmpty() || normalized in settings.includedPlugins)
        }

    private fun inspectExecution(
        execution: () -> Unit,
        event: Event,
        registeredListener: RegisteredListener,
        listenerClass: String
    ) {
        val readers = subscriptions.readersFor(event.javaClass)
        if (readers.isEmpty()) {
            execution()
            return
        }

        inspectedCalls.incrementAndGet()
        val activity = activityByClass.computeIfAbsent(event.javaClass.name) {
            MutableActivity(event.eventName, event.javaClass.name)
        }
        activity.inspectedCalls.incrementAndGet()
        activity.lastSeen = System.currentTimeMillis()
        val before = arrayOfNulls<Any?>(readers.size)
        readers.forEachIndexed { index, reader -> before[index] = readValue(event, reader) }

        try {
            execution()
        } finally {
            val changes = ArrayList<DetectedChange>(2)
            readers.forEachIndexed { index, reader ->
                val previous = before[index]
                if (previous !== ReadFailed) {
                    val current = readValue(event, reader)
                    if (current !== ReadFailed && !snapshotter.equivalent(previous, current)) {
                        changes += DetectedChange(reader.name, previous, current)
                    }
                }
            }
            if (changes.isNotEmpty()) recordChanges(event, registeredListener, listenerClass, activity, changes)
        }
    }

    private fun recordChanges(
        event: Event,
        listener: RegisteredListener,
        listenerClass: String,
        activity: MutableActivity,
        changes: List<DetectedChange>
    ) {
        detectedChanges.addAndGet(changes.size.toLong())
        activity.changedValues.addAndGet(changes.size.toLong())
        activity.lastPlugin = listener.plugin.name
        activity.lastChangedMethod = changes.last().method

        val formattedChanges = changes.map { change ->
            ValueChange(
                change.method,
                snapshotter.format(change.before, settings.maxValueLength),
                snapshotter.format(change.after, settings.maxValueLength)
            )
        }
        val asyncMarker = if (event.isAsynchronous) ", async" else ""
        logger.info(
            "${event.eventName} changed by ${listener.plugin.name} " +
                "(${listener.priority}$asyncMarker, $listenerClass)"
        )
        formattedChanges.forEach { change ->
            logger.info("  ${change.method}: ${change.before} -> ${change.after}")
        }

        if (settings.historyEnabled) {
            addHistory(
                ChangeRecord(
                    id = recordIds.incrementAndGet(),
                    timestamp = System.currentTimeMillis(),
                    eventName = event.eventName,
                    eventClass = event.javaClass.name,
                    pluginName = listener.plugin.name,
                    priority = listener.priority.name,
                    listenerClass = listenerClass,
                    asynchronous = event.isAsynchronous,
                    changes = formattedChanges
                )
            )
        }
    }

    private fun addHistory(record: ChangeRecord) {
        synchronized(historyLock) {
            history.addLast(record)
            recordsById[record.id] = record
            latestRecordByEvent[record.eventClass] = record
            trimHistoryLocked()
        }
    }

    private fun trimHistory() {
        synchronized(historyLock) { trimHistoryLocked() }
    }

    private fun trimHistoryLocked() {
        val limit = settings.historyLimit.coerceAtLeast(1)
        while (history.size > limit) {
            val removed = history.removeFirst()
            recordsById.remove(removed.id)
            if (latestRecordByEvent[removed.eventClass] === removed) {
                latestRecordByEvent.remove(removed.eventClass)
            }
        }
    }

    private fun readValue(event: Event, reader: EventMethodReader): Any? = try {
        snapshotter.snapshot(reader.read(event))
    } catch (exception: Throwable) {
        val key = "${event.javaClass.name}.${reader.name}"
        if (failedValueReads.add(key)) logger.warning("Could not read '$key': ${exception.messageOf()}")
        ReadFailed
    }

    private object ReadFailed
}
