package me.jantuck.eventdebugger.ui.dialog

import me.jantuck.eventdebugger.EventDebugger
import me.jantuck.eventdebugger.core.DebuggerController
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.text.SimpleDateFormat
import java.util.Date

private typealias Action = NativeDialog.Action
private typealias TextInput = NativeDialog.Input.Text
private typealias Span = NativeDialog.Span
private typealias Line = NativeDialog.Line

internal class ModernDialogUi(
    private val plugin: EventDebugger,
    private val debugger: DebuggerController
) {
    private val timeFormat = SimpleDateFormat("HH:mm:ss")
    @Volatile private var reportedFailure = false

    fun open(player: Player, notice: String? = null): Boolean {
        val status = debugger.remapper.status()
        val activities = debugger.remapper.activity().size
        val outcomes = debugger.remapper.history().size
        val running = debugger.enabled
        val body = mutableListOf(
            line(
                span(if (running) "● ONLINE" else "● PAUSED", if (running) GREEN else RED),
                span("  •  ", MUTED),
                span("${status.wrappedListeners} listeners", TEXT),
                span("  •  ", MUTED),
                span("${status.eventTypes} event types", TEXT)
            ),
            line(
                link("${status.inspectedCalls} calls", CYAN, "eventdebugger dialog events 0", "Open live events"),
                span(" inspected  •  ", MUTED),
                link("${status.detectedChanges} changes", AMBER, "eventdebugger dialog history 0", "Open change history")
            ),
            line(
                link("$activities active", CYAN, "eventdebugger dialog events 0", "Open live events"),
                span("  •  ", MUTED),
                link("$outcomes saved", TEXT, "eventdebugger dialog history 0", "Open change history")
            )
        )
        notice?.let { body.add(line(span("✓ $it", GREEN))) }
        val actions = listOf(
            Action("◈ Live events", "Current event activity and latest traces", "eventdebugger dialog events 0"),
            Action("⌛ Change history", "Recorded listener before and after values", "eventdebugger dialog history 0"),
            Action("⚙ Subscriptions", "Choose event classes and getter methods", "eventdebugger dialog subscriptions 0"),
            Action("⌁ Plugin filters", "Choose which plugins are inspected", "eventdebugger dialog filters 0"),
            Action(if (running) "Ⅱ Pause debugger" else "▶ Start debugger", "Restore or instrument listener executors", "eventdebugger dialog toggle", if (running) AMBER else GREEN),
            Action("↻ Refresh listeners", "Discover registrations immediately", "eventdebugger dialog refresh"),
            Action("⚙ Capture settings", "Debugger state, history retention, and refresh cadence", "eventdebugger dialog settings", TEXT)
        )
        return show(player, "EventDebugger  ·  Control Center", body, actions, visualIcon(Material.COMPASS, glow = running))
    }

    fun openEvents(player: Player, requestedPage: Int, refreshed: Boolean = false): Boolean {
        val events = debugger.remapper.activity()
        val page = validPage(requestedPage, events.size)
        val pageEvents = events.drop(page * PAGE_SIZE).take(PAGE_SIZE)
        val body = (if (pageEvents.isEmpty()) {
            listOf(
                breadcrumb("LIVE EVENTS"),
                line(span("Waiting for an event", TEXT)),
                line(span("Subscribed events appear here as soon as a listener runs.", MUTED))
            )
        } else {
            listOf(
                breadcrumb("LIVE EVENTS"),
                line(span("${events.size} active event ${plural(events.size, "type")}", TEXT)),
                line(span("Select one for its latest saved trace.", MUTED))
            )
        }).toMutableList()
        if (refreshed) body.add(line(span("✓ Snapshot refreshed  ·  ${timeFormat.format(Date())}", GREEN)))
        val entries = pageEvents.map { event ->
            val latest = debugger.remapper.latestRecord(event.eventClass)
            val method = event.lastChangedMethod ?: "no changed getter"
            Action(
                "${event.eventName}  ·  ${event.inspectedCalls} calls  ·  ${event.changedValues} Δ",
                "${event.lastPlugin ?: "No plugin change"}  •  $method${if (latest == null) "  •  no saved trace" else ""}",
                latest?.let { "eventdebugger dialog detail ${it.id}" } ?: "eventdebugger dialog events $page",
                if (event.changedValues > 0) AMBER else CYAN
            )
        }
        val actions = entries + navigation(page, events.size, "events") +
            Action("↻ Refresh", "Rebuild with current counters", "eventdebugger dialog events $page refreshed", GREEN)
        return show(
            player,
            "Live Events  ·  ${page + 1}/${pageCount(events.size)}",
            body,
            actions,
            visualIcon(Material.CLOCK, events.size, events.isNotEmpty()),
            exitAction = dashboardAction()
        )
    }

    fun openHistory(player: Player, requestedPage: Int, notice: String? = null): Boolean {
        val records = debugger.remapper.history()
        val page = validPage(requestedPage, records.size)
        val pageRecords = records.drop(page * PAGE_SIZE).take(PAGE_SIZE)
        val body = (if (pageRecords.isEmpty()) {
            listOf(
                breadcrumb("CHANGE HISTORY"),
                line(span(if (debugger.historyEnabled) "History is ready" else "History is disabled", if (debugger.historyEnabled) GREEN else RED)),
                line(span(if (debugger.historyEnabled) "Changed listener outcomes will appear here." else "Enable it from the dashboard to retain changed outcomes.", MUTED))
            )
        } else {
            listOf(
                breadcrumb("CHANGE HISTORY"),
                line(span("${records.size} saved ${plural(records.size, "outcome")}", TEXT), span("  •  newest first", MUTED))
            )
        }).toMutableList()
        notice?.let { body.add(line(span("✓ $it", GREEN))) }
        val entries = pageRecords.map { record ->
            val first = record.changes.firstOrNull()
            Action(
                "#${record.id}  ${record.priority}  ·  ${first?.method ?: record.eventName}  ·  ${timeFormat.format(Date(record.timestamp))}",
                "${record.eventName}  •  ${record.pluginName}  •  ${record.changes.size} ${plural(record.changes.size, "change")}",
                "eventdebugger dialog detail ${record.id}",
                AMBER
            )
        }
        val actions = entries + navigation(page, records.size, "history") +
            Action("Clear history", "Remove all saved outcomes", "eventdebugger dialog clear-history", RED)
        return show(
            player,
            "Change History  ·  ${page + 1}/${pageCount(records.size)}",
            body,
            actions,
            visualIcon(Material.BOOK, records.size, debugger.historyEnabled),
            exitAction = dashboardAction()
        )
    }

    fun openDetail(player: Player, recordId: Long): Boolean {
        val record = debugger.remapper.record(recordId) ?: return openHistory(player, 0)
        val body = mutableListOf(
            line(
                link("HISTORY", MUTED, "eventdebugger dialog history 0", "Back to change history"),
                span("  ›  ${record.eventName.uppercase()}", CYAN)
            ),
            line(
                span(record.eventName, CYAN),
                span("  •  ${record.pluginName}  •  ", MUTED),
                span(record.priority, AMBER),
                if (record.asynchronous) span("  ASYNC", RED) else span("", MUTED)
            ),
            line(span(shortClass(record.listenerClass), TEXT), span("  •  ${timeFormat.format(Date(record.timestamp))}", MUTED))
        )
        record.changes.forEachIndexed { index, change ->
            if (index == 0) body.add(line(span("", MUTED)))
            body.add(line(span(change.method, AMBER)))
            body.add(
                line(
                    span(shortValue(change.before), RED),
                    span("  →  ", MUTED),
                    span(shortValue(change.after), GREEN)
                )
            )
        }
        return show(
            player,
            "Listener Outcome",
            body,
            listOf(
                Action("← Change history", "Return to saved outcomes", "eventdebugger dialog history 0", AMBER)
            ),
            visualIcon(Material.PAPER, record.changes.size, glow = true),
            exitAction = dashboardAction()
        )
    }

    fun openSubscriptions(player: Player, requestedPage: Int, notice: String? = null): Boolean {
        val subscriptions = debugger.configuredSubscriptions()
        val page = validPage(requestedPage, subscriptions.size)
        val pageSubscriptions = subscriptions.drop(page * PAGE_SIZE).take(PAGE_SIZE)
        val enabledCount = subscriptions.count { !it.disabled }
        val body = mutableListOf(
            breadcrumb("SUBSCRIPTIONS"),
            line(
                span("$enabledCount watched", GREEN),
                span("  •  ${subscriptions.size - enabledCount} paused  •  click to toggle", MUTED)
            )
        )
        notice?.let { body.add(line(span("✓ $it", GREEN))) }
        val entries = pageSubscriptions.map { subscription ->
            val enabled = !subscription.disabled
            Action(
                "${if (enabled) "●" else "○"} ${subscription.key}",
                "${subscription.className}  •  ${subscription.methods.joinToString()}",
                "eventdebugger dialog toggle-sub ${subscription.key} $page",
                if (enabled) GREEN else MUTED
            )
        }
        val actions = entries + navigation(page, subscriptions.size, "subscriptions") +
            Action("＋ Add event", "Subscribe a runtime event class and its getters", "eventdebugger dialog add-form")
        return show(
            player,
            "Subscriptions  ·  ${page + 1}/${pageCount(subscriptions.size)}",
            body,
            actions,
            visualIcon(Material.COMPARATOR, enabledCount, enabledCount > 0),
            exitAction = dashboardAction()
        )
    }

    fun openFilters(player: Player, requestedPage: Int): Boolean {
        val plugins = plugin.server.pluginManager.plugins.filter { it !== plugin }.sortedBy { it.name.lowercase() }
        val page = validPage(requestedPage, plugins.size)
        val watchedCount = plugins.count { debugger.isPluginWatched(it.name) }
        val body = listOf(
            breadcrumb("PLUGIN FILTERS"),
            line(
                span("$watchedCount inspected", GREEN),
                span("  •  ${plugins.size - watchedCount} ignored  •  click to toggle", MUTED)
            )
        )
        val entries = plugins.drop(page * PAGE_SIZE).take(PAGE_SIZE).map { target ->
            val watched = debugger.isPluginWatched(target.name)
            Action(
                "${if (watched) "●" else "○"} ${target.name}",
                "Version ${target.description.version}  •  click to ${if (watched) "ignore" else "inspect"}",
                "eventdebugger dialog toggle-plugin ${target.name} $page",
                if (watched) GREEN else MUTED
            )
        }
        return show(
            player,
            "Plugin Filters  ·  ${page + 1}/${pageCount(plugins.size)}",
            body,
            entries + navigation(page, plugins.size, "filters"),
            visualIcon(Material.HOPPER, watchedCount, watchedCount > 0),
            exitAction = dashboardAction()
        )
    }

    fun openSettings(player: Player): Boolean {
        val refresh = debugger.listenerRefreshTicks.toString()
        return show(
            player,
            "Capture Settings",
            listOf(
                breadcrumb("CAPTURE SETTINGS"),
                line(span("Tune the recorder without touching config.yml.", TEXT))
            ),
            listOf(
                Action(
                    "Apply settings",
                    "Save these controls and rebuild instrumentation",
                    "eventdebugger dialog apply-settings \$(debugger) \$(history) \$(history_limit) \$(refresh)",
                    GREEN
                )
            ),
            visualIcon(Material.REDSTONE_TORCH, glow = debugger.enabled),
            listOf(
                NativeDialog.Input.Toggle("debugger", "Debugger active", CYAN, debugger.enabled, "on", "off"),
                NativeDialog.Input.Toggle("history", "Retain changed outcomes", AMBER, debugger.historyEnabled, "on", "off"),
                NativeDialog.Input.Slider(
                    "history_limit",
                    "Saved outcomes",
                    AMBER,
                    10f,
                    500f,
                    debugger.historyLimit.toFloat(),
                    10f
                ),
                NativeDialog.Input.Choice(
                    "refresh",
                    "Listener discovery",
                    CYAN,
                    listOf(
                        NativeDialog.Option("0", "Manual only", refresh == "0", MUTED),
                        NativeDialog.Option("20", "Every second", refresh == "20", TEXT),
                        NativeDialog.Option("100", "Every 5 seconds", refresh == "100", TEXT),
                        NativeDialog.Option("200", "Every 10 seconds", refresh == "200", TEXT),
                        NativeDialog.Option("600", "Every 30 seconds", refresh == "600", TEXT),
                        NativeDialog.Option("1200", "Every minute", refresh == "1200", TEXT)
                    )
                )
            ),
            true,
            dashboardAction()
        )
    }

    fun openAddForm(player: Player, error: String? = null): Boolean = show(
        player,
        "Add Event Subscription",
        listOfNotNull(
            line(
                link("SUBSCRIPTIONS", MUTED, "eventdebugger dialog subscriptions 0", "Back to subscriptions"),
                span("  ›  ADD EVENT", CYAN)
            ),
            error?.let { line(span("⚠ $it", RED)) },
            line(span("Runtime event class", CYAN), span("  •  fully-qualified name", MUTED)),
            line(span("Getter methods", CYAN), span("  •  comma-separated, zero arguments", MUTED)),
            line(span("Example  ", MUTED), span("PlayerInteractEvent  /  isCancelled,getAction", TEXT))
        ),
        listOf(
            Action("＋ Add subscription", "Validate, save, and begin watching this event", "eventdebugger dialog add \$(event_class) \$(methods)", GREEN)
        ),
        visualIcon(Material.OAK_SIGN),
        listOf(
            TextInput("event_class", "Event class", "org.bukkit.event.", 200),
            TextInput("methods", "Getter methods", "isCancelled", 200)
        ),
        true,
        Action("Cancel", "Return without changing configuration", "eventdebugger dialog subscriptions 0", RED)
    )

    private fun navigation(page: Int, total: Int, screen: String): List<Action> = buildList {
        if (page > 0) add(Action("← Previous", "Open page $page", "eventdebugger dialog $screen ${page - 1}", TEXT))
        if ((page + 1) * PAGE_SIZE < total) add(Action("Next →", "Open page ${page + 2}", "eventdebugger dialog $screen ${page + 1}", TEXT))
    }

    private fun show(
        player: Player,
        title: String,
        body: List<Line>,
        actions: List<Action>,
        icon: ItemStack,
        inputs: List<NativeDialog.Input> = emptyList(),
        templateActions: Boolean = false,
        exitAction: Action? = null
    ): Boolean {
        val shown = NativeDialog.show(
            player,
            NativeDialog.Screen(
                title = title,
                icon = icon,
                content = body,
                actions = actions,
                exitAction = exitAction,
                inputs = inputs,
                templateActions = templateActions
            )
        )
        if (!shown && !reportedFailure) {
            reportedFailure = true
            plugin.logger.warning("Modern dialog UI is unavailable: ${NativeDialog.failure() ?: "unsupported server"}")
        }
        return shown
    }

    private fun line(vararg spans: Span): Line = Line(spans.toList())

    private fun span(text: String, color: Int): Span = Span(text, color)

    private fun link(text: String, color: Int, command: String, tooltip: String): Span = Span(text, color, command, tooltip)

    private fun breadcrumb(section: String): Line = line(
        link("DASHBOARD", MUTED, "eventdebugger dialog dashboard", "Go back"),
        span("  ›  $section", CYAN)
    )

    private fun dashboardAction(): Action = Action("⌂ Dashboard", "Return to the control center", "eventdebugger dialog dashboard", TEXT)

    private fun visualIcon(material: Material, amount: Int = 1, glow: Boolean = false): ItemStack =
        ItemStack(material, amount.coerceIn(1, material.maxStackSize.coerceAtLeast(1))).also { item ->
            if (glow) Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"))
                ?.let { item.addUnsafeEnchantment(it, 1) }
        }

    private fun plural(amount: Int, word: String): String = if (amount == 1) word else "${word}s"

    private fun shortClass(value: String): String = value.substringAfterLast('.')

    private fun shortValue(value: String): String = if (value.length <= 96) value else value.take(93) + "..."

    private fun validPage(page: Int, total: Int): Int = page.coerceIn(0, (total - 1).coerceAtLeast(0) / PAGE_SIZE)

    private fun pageCount(total: Int): Int = ((total - 1).coerceAtLeast(0) / PAGE_SIZE) + 1

    private companion object {
        const val CYAN = 0x67E8F9
        const val TEXT = 0xE5E7EB
        const val MUTED = 0x94A3B8
        const val GREEN = 0x86EFAC
        const val RED = 0xFCA5A5
        const val AMBER = 0xFCD34D
        const val PAGE_SIZE = 7
    }

}
