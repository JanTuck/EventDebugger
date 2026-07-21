package me.jantuck.eventdebugger.core

internal data class RemapperSettings(
    val includedPlugins: Set<String>,
    val excludedPlugins: Set<String>,
    val logRemaps: Boolean,
    val maxValueLength: Int,
    val historyEnabled: Boolean,
    val historyLimit: Int
)

internal data class DebuggerStatus(
    val eventTypes: Int,
    val watchedMethods: Int,
    val wrappedListeners: Int,
    val inspectedCalls: Long,
    val detectedChanges: Long
)

internal data class ChangeRecord(
    val id: Long,
    val timestamp: Long,
    val eventName: String,
    val eventClass: String,
    val pluginName: String,
    val priority: String,
    val listenerClass: String,
    val asynchronous: Boolean,
    val changes: List<ValueChange>
)

internal data class ValueChange(val method: String, val before: String, val after: String)

internal data class EventActivity(
    val eventName: String,
    val eventClass: String,
    val inspectedCalls: Long,
    val changedValues: Long,
    val lastPlugin: String?,
    val lastChangedMethod: String?,
    val lastSeen: Long
)

internal data class ConfiguredSubscription(
    val key: String,
    val className: String,
    val methods: List<String>,
    val disabled: Boolean
)
