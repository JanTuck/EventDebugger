package me.jantuck.eventdebugger.ui.chest

import me.jantuck.eventdebugger.EventDebugger
import me.jantuck.eventdebugger.core.ChangeRecord
import me.jantuck.eventdebugger.core.DebuggerController
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.conversations.ConversationContext
import org.bukkit.conversations.ConversationFactory
import org.bukkit.conversations.Prompt
import org.bukkit.conversations.StringPrompt
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.text.SimpleDateFormat
import java.util.Date

internal class ChestDebugUi(
    private val plugin: EventDebugger,
    private val debugger: DebuggerController
) : Listener {
    private enum class Screen { DASHBOARD, HISTORY, DETAIL, ACTIVITY, SUBSCRIPTIONS, FILTERS }

    private class Holder(val screen: Screen, val page: Int = 0, val payload: Any? = null) : InventoryHolder {
        lateinit var backing: Inventory
        override fun getInventory(): Inventory = backing
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss")

    fun open(player: Player) {
        if (!debugger.uiEnabled) {
            player.notice("The UI is disabled in config.yml.", ChatColor.YELLOW)
            return
        }
        openDashboard(player)
    }

    fun openHistory(player: Player, page: Int = 0) {
        val records = debugger.remapper.history()
        val currentPage = validPage(page, records.size)
        val inventory = inventory(Screen.HISTORY, currentPage, 54, "EventDebugger - History")
        records.drop(currentPage * 45).take(45).forEachIndexed { slot, record ->
            inventory.setItem(slot, recordItem(record))
        }
        navigation(inventory, currentPage, records.size, "history")
        inventory.setItem(49, item(Material.BARRIER, "${ChatColor.RED}Clear history", listOf("${ChatColor.GRAY}${records.size} saved changes")))
        player.openInventory(inventory)
    }

    fun openActivity(player: Player, page: Int = 0) {
        val events = debugger.remapper.activity()
        val currentPage = validPage(page, events.size)
        val inventory = inventory(Screen.ACTIVITY, currentPage, 54, "EventDebugger - Live events")
        events.drop(currentPage * 45).take(45).forEachIndexed { slot, activity ->
            val lore = listOf(
                "${ChatColor.GRAY}${activity.eventClass}",
                "${ChatColor.WHITE}Listener calls: ${ChatColor.AQUA}${activity.inspectedCalls}",
                "${ChatColor.WHITE}Changed values: ${ChatColor.GOLD}${activity.changedValues}",
                "${ChatColor.WHITE}Last change: ${ChatColor.YELLOW}${activity.lastPlugin ?: "none"}",
                "${ChatColor.DARK_GRAY}${activity.lastChangedMethod ?: "No changed outcome yet"}"
            )
            inventory.setItem(slot, item(if (activity.changedValues > 0) material("WRITABLE_BOOK", "BOOK_AND_QUILL") else Material.PAPER, "${ChatColor.AQUA}${activity.eventName}", lore))
        }
        navigation(inventory, currentPage, events.size, "activity")
        player.openInventory(inventory)
    }

    fun openSubscriptions(player: Player, page: Int = 0) {
        val subscriptions = debugger.configuredSubscriptions()
        val currentPage = validPage(page, subscriptions.size)
        val inventory = inventory(Screen.SUBSCRIPTIONS, currentPage, 54, "EventDebugger - Subscriptions")
        subscriptions.drop(currentPage * 45).take(45).forEachIndexed { slot, subscription ->
            val enabled = !subscription.disabled
            val lore = listOf(
                "${ChatColor.GRAY}${subscription.className}",
                "${ChatColor.WHITE}${subscription.methods.joinToString(", ")}",
                "",
                "${if (enabled) ChatColor.GREEN else ChatColor.RED}${if (enabled) "Enabled" else "Disabled"}",
                "${ChatColor.YELLOW}Click to ${if (enabled) "disable" else "enable"}"
            )
            inventory.setItem(slot, item(material(if (enabled) "LIME_WOOL" else "RED_WOOL", "WOOL"), "${ChatColor.AQUA}${subscription.key}", lore))
        }
        navigation(inventory, currentPage, subscriptions.size, "subscriptions")
        inventory.setItem(49, item(material("OAK_SIGN", "SIGN"), "${ChatColor.GREEN}Add subscription", listOf("${ChatColor.GRAY}Enter an event and getters in chat")))
        player.openInventory(inventory)
    }

    fun openFilters(player: Player, page: Int = 0) {
        val plugins = Bukkit.getPluginManager().plugins.filter { it !== plugin }.sortedBy { it.name.lowercase() }
        val currentPage = validPage(page, plugins.size)
        val inventory = inventory(Screen.FILTERS, currentPage, 54, "EventDebugger - Plugin filters")
        plugins.drop(currentPage * 45).take(45).forEachIndexed { slot, target ->
            val watched = debugger.isPluginWatched(target.name)
            val lore = listOf(
                "${ChatColor.GRAY}Version ${target.description.version}",
                "${if (watched) ChatColor.GREEN else ChatColor.RED}${if (watched) "Watched" else "Ignored"}",
                "${ChatColor.YELLOW}Click to ${if (watched) "ignore" else "watch"}"
            )
            inventory.setItem(slot, item(material(if (watched) "LIME_WOOL" else "RED_WOOL", "WOOL"), "${ChatColor.AQUA}${target.name}", lore))
        }
        navigation(inventory, currentPage, plugins.size, "filters")
        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? Holder ?: return
        event.isCancelled = true
        if (event.rawSlot !in 0 until event.view.topInventory.size) return
        val player = event.whoClicked as? Player ?: return
        when (holder.screen) {
            Screen.DASHBOARD -> dashboardClick(player, event.rawSlot, event.isRightClick)
            Screen.HISTORY -> historyClick(player, holder.page, event.rawSlot)
            Screen.DETAIL -> if (event.rawSlot == 49) openHistory(player)
            Screen.ACTIVITY -> pageClick(player, holder.page, event.rawSlot, debugger.remapper.activity().size, ::openActivity)
            Screen.SUBSCRIPTIONS -> subscriptionClick(player, holder.page, event.rawSlot)
            Screen.FILTERS -> filterClick(player, holder.page, event.rawSlot)
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is Holder && event.rawSlots.any { it < event.view.topInventory.size }) {
            event.isCancelled = true
        }
    }

    private fun openDashboard(player: Player) {
        val status = debugger.remapper.status()
        val inventory = inventory(Screen.DASHBOARD, 0, 27, "EventDebugger")
        inventory.setItem(10, item(
            material(if (debugger.enabled) "LIME_WOOL" else "RED_WOOL", "WOOL"),
            "${if (debugger.enabled) ChatColor.GREEN else ChatColor.RED}Debugger ${if (debugger.enabled) "enabled" else "disabled"}",
            listOf("${ChatColor.GRAY}Click to toggle", "${ChatColor.WHITE}${status.wrappedListeners} instrumented listeners")
        ))
        inventory.setItem(11, item(material("CLOCK", "WATCH"), "${ChatColor.AQUA}Live event activity", listOf("${ChatColor.GRAY}${debugger.remapper.activity().size} event types have run")))
        inventory.setItem(12, item(material("WRITABLE_BOOK", "BOOK_AND_QUILL"), "${ChatColor.AQUA}Change history", listOf(
            "${ChatColor.GRAY}${debugger.remapper.history().size} saved outcomes",
            "${if (debugger.historyEnabled) ChatColor.GREEN else ChatColor.RED}History ${if (debugger.historyEnabled) "enabled" else "disabled"}",
            "${ChatColor.YELLOW}Left-click to view, right-click to toggle"
        )))
        inventory.setItem(13, item(material("COMPARATOR", "REDSTONE_COMPARATOR"), "${ChatColor.AQUA}Subscriptions", listOf("${ChatColor.GRAY}${status.eventTypes} event types, ${status.watchedMethods} getters")))
        inventory.setItem(14, item(Material.HOPPER, "${ChatColor.AQUA}Plugin filters", listOf("${ChatColor.GRAY}Choose which plugins are inspected")))
        inventory.setItem(15, item(material("SUNFLOWER", "DOUBLE_PLANT"), "${ChatColor.AQUA}Refresh listeners", listOf("${ChatColor.GRAY}Discover new registrations now")))
        inventory.setItem(16, item(Material.REDSTONE, "${ChatColor.RED}Reload configuration", listOf("${ChatColor.GRAY}Safely restore and re-instrument")))
        player.openInventory(inventory)
    }

    private fun dashboardClick(player: Player, slot: Int, rightClick: Boolean) {
        when (slot) {
            10 -> {
                debugger.setEnabled(!debugger.enabled)
                openDashboard(player)
            }
            11 -> openActivity(player)
            12 -> if (rightClick) {
                debugger.toggleHistory()
                openDashboard(player)
            } else openHistory(player)
            13 -> openSubscriptions(player)
            14 -> openFilters(player)
            15 -> {
                val added = debugger.refreshListeners()
                player.notice(if (added == 0) "Listeners are up to date." else "Now watching $added additional listeners.")
                openDashboard(player)
            }
            16 -> {
                debugger.reload()
                player.notice("Configuration reloaded.")
                openDashboard(player)
            }
        }
    }

    private fun historyClick(player: Player, page: Int, slot: Int) {
        val records = debugger.remapper.history()
        when {
            slot < 45 -> records.getOrNull(page * 45 + slot)?.let { openDetail(player, it) }
            slot == 49 -> {
                debugger.remapper.clearHistory()
                openHistory(player)
            }
            else -> pageClick(player, page, slot, records.size, ::openHistory)
        }
    }

    private fun subscriptionClick(player: Player, page: Int, slot: Int) {
        val subscriptions = debugger.configuredSubscriptions()
        when {
            slot < 45 -> subscriptions.getOrNull(page * 45 + slot)?.let {
                debugger.toggleSubscription(it.key)
                openSubscriptions(player, page)
            }
            slot == 49 -> startAddConversation(player)
            else -> pageClick(player, page, slot, subscriptions.size, ::openSubscriptions)
        }
    }

    private fun filterClick(player: Player, page: Int, slot: Int) {
        val plugins = Bukkit.getPluginManager().plugins.filter { it !== plugin }.sortedBy { it.name.lowercase() }
        when {
            slot < 45 -> plugins.getOrNull(page * 45 + slot)?.let {
                debugger.togglePlugin(it.name)
                openFilters(player, page)
            }
            else -> pageClick(player, page, slot, plugins.size, ::openFilters)
        }
    }

    private fun pageClick(player: Player, page: Int, slot: Int, total: Int, opener: (Player, Int) -> Unit) {
        when (slot) {
            45 -> openDashboard(player)
            48 -> if (page > 0) opener(player, page - 1)
            50 -> if ((page + 1) * 45 < total) opener(player, page + 1)
        }
    }

    private fun openDetail(player: Player, record: ChangeRecord) {
        val inventory = inventory(Screen.DETAIL, 0, 54, "EventDebugger - Outcome", record)
        inventory.setItem(4, item(material("WRITABLE_BOOK", "BOOK_AND_QUILL"), "${ChatColor.GOLD}${record.eventName}", listOf(
            "${ChatColor.GRAY}${record.eventClass}",
            "${ChatColor.WHITE}Plugin: ${ChatColor.AQUA}${record.pluginName}",
            "${ChatColor.WHITE}Priority: ${ChatColor.AQUA}${record.priority}${if (record.asynchronous) " (async)" else ""}",
            "${ChatColor.WHITE}Listener: ${ChatColor.GRAY}${record.listenerClass}",
            "${ChatColor.DARK_GRAY}${timeFormat.format(Date(record.timestamp))}"
        )))
        record.changes.take(36).forEachIndexed { index, change ->
            inventory.setItem(9 + index, item(Material.PAPER, "${ChatColor.YELLOW}${change.method}", valueLore(change.before, change.after)))
        }
        inventory.setItem(49, item(Material.ARROW, "${ChatColor.YELLOW}Back to history"))
        player.openInventory(inventory)
    }

    private fun startAddConversation(player: Player) {
        player.closeInventory()
        ConversationFactory(plugin)
            .withModality(true)
            .withLocalEcho(false)
            .withTimeout(90)
            .withEscapeSequence("cancel")
            .withFirstPrompt(EventClassPrompt(debugger))
            .addConversationAbandonedListener {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline) openSubscriptions(player)
                })
            }
            .buildConversation(player)
            .begin()
    }

    private fun navigation(inventory: Inventory, page: Int, total: Int, label: String) {
        inventory.setItem(45, item(Material.ARROW, "${ChatColor.YELLOW}Dashboard"))
        if (page > 0) inventory.setItem(48, item(Material.ARROW, "${ChatColor.YELLOW}Previous page"))
        inventory.setItem(49.takeUnless { label == "history" } ?: 47, item(Material.MAP, "${ChatColor.AQUA}Page ${page + 1}", listOf("${ChatColor.GRAY}$total entries")))
        if ((page + 1) * 45 < total) inventory.setItem(50, item(Material.ARROW, "${ChatColor.YELLOW}Next page"))
    }

    private fun recordItem(record: ChangeRecord): ItemStack {
        val lore = mutableListOf(
            "${ChatColor.WHITE}Plugin: ${ChatColor.AQUA}${record.pluginName}",
            "${ChatColor.WHITE}Priority: ${ChatColor.GRAY}${record.priority}",
            "${ChatColor.WHITE}Changes: ${ChatColor.GOLD}${record.changes.size}"
        )
        record.changes.take(3).forEach { lore.add("${ChatColor.YELLOW}${it.method}: ${ChatColor.GRAY}${it.before} -> ${it.after}") }
        lore.add("${ChatColor.DARK_GRAY}${timeFormat.format(Date(record.timestamp))} - click for details")
        return item(material("WRITABLE_BOOK", "BOOK_AND_QUILL"), "${ChatColor.AQUA}${record.eventName}", lore)
    }

    private fun valueLore(before: String, after: String): List<String> =
        wrap("Before: $before", ChatColor.RED) + listOf("") + wrap("After: $after", ChatColor.GREEN)

    private fun wrap(text: String, color: ChatColor): List<String> =
        text.chunked(45).map { "$color$it" }.take(12)

    private fun inventory(screen: Screen, page: Int, size: Int, title: String, payload: Any? = null): Inventory {
        val holder = Holder(screen, page, payload)
        return Bukkit.createInventory(holder, size, title).also { holder.backing = it }
    }

    private fun item(material: Material, name: String, lore: List<String> = emptyList()): ItemStack =
        ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(name)
                if (lore.isNotEmpty()) setLore(lore)
            }
        }

    private fun material(vararg names: String): Material =
        names.asSequence().mapNotNull(Material::matchMaterial).firstOrNull() ?: Material.STONE

    private fun validPage(page: Int, total: Int): Int = page.coerceIn(0, ((total - 1).coerceAtLeast(0) / 45))

    private fun Player.notice(message: String, color: ChatColor = ChatColor.AQUA) {
        sendMessage("$color[EventDebugger] ${ChatColor.RESET}$message")
    }

    private class EventClassPrompt(private val debugger: DebuggerController) : StringPrompt() {
        override fun getPromptText(context: ConversationContext): String =
            "${ChatColor.AQUA}[EventDebugger] ${ChatColor.WHITE}Enter the fully-qualified event class, or type cancel:"

        override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
            val eventClass = debugger.resolveEventClass(input?.trim().orEmpty())
            if (eventClass == null) {
                context.forWhom.sendRawMessage("${ChatColor.RED}That class was not found or is not a Bukkit event. Try again.")
                return this
            }
            context.setSessionData("eventClass", eventClass.name)
            return MethodPrompt(debugger)
        }
    }

    private class MethodPrompt(private val debugger: DebuggerController) : StringPrompt() {
        override fun getPromptText(context: ConversationContext): String =
            "${ChatColor.AQUA}[EventDebugger] ${ChatColor.WHITE}Enter zero-argument method names separated by commas (for example isCancelled,getResult):"

        override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
            val methods = input.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
            if (methods.isEmpty()) {
                context.forWhom.sendRawMessage("${ChatColor.RED}Enter at least one method.")
                return this
            }
            val className = context.getSessionData("eventClass") as String
            val error = debugger.addSubscription(className, methods)
            if (error != null) {
                context.forWhom.sendRawMessage("${ChatColor.RED}$error")
                return this
            }
            context.forWhom.sendRawMessage("${ChatColor.GREEN}Subscription added.")
            return Prompt.END_OF_CONVERSATION
        }
    }
}
