package me.jantuck.eventdebugger.ui

import me.jantuck.eventdebugger.EventDebugger
import me.jantuck.eventdebugger.command.notice
import me.jantuck.eventdebugger.core.DebuggerController
import me.jantuck.eventdebugger.ui.chest.ChestDebugUi
import me.jantuck.eventdebugger.ui.dialog.ModernDialogUi
import org.bukkit.ChatColor
import org.bukkit.entity.Player

internal class DebuggerUiController(
    plugin: EventDebugger,
    private val debugger: DebuggerController
) {
    private val chestUi = ChestDebugUi(plugin, debugger)
    private val dialogUi = ModernDialogUi(plugin, debugger)

    val listener: ChestDebugUi
        get() = chestUi

    fun open(player: Player, forceChest: Boolean = false) {
        if (!debugger.uiEnabled) {
            player.notice("The UI is disabled in config.yml.", ChatColor.YELLOW)
            return
        }
        if (!forceChest && debugger.preferModernDialogs && dialogUi.open(player)) return
        chestUi.open(player)
    }

    fun openEvents(player: Player, page: Int = 0, refreshed: Boolean = false) {
        if (!dialogUi.openEvents(player, page, refreshed)) chestUi.openActivity(player, page)
    }

    fun openHistory(player: Player, page: Int = 0, notice: String? = null) {
        if (!dialogUi.openHistory(player, page, notice)) chestUi.openHistory(player, page)
    }

    fun openSubscriptions(player: Player, page: Int = 0, notice: String? = null) {
        if (!dialogUi.openSubscriptions(player, page, notice)) chestUi.openSubscriptions(player, page)
    }

    fun openFilters(player: Player, page: Int = 0) {
        if (!dialogUi.openFilters(player, page)) chestUi.openFilters(player, page)
    }

    fun handleDialogCommand(player: Player, arguments: List<String>) {
        when (arguments.firstOrNull()?.lowercase() ?: "dashboard") {
            "dashboard" -> openDashboard(player)
            "events" -> openEvents(
                player,
                arguments.getOrNull(1).toPage(),
                arguments.getOrNull(2).equals("refreshed", ignoreCase = true)
            )
            "history" -> openHistory(player, arguments.getOrNull(1).toPage())
            "detail" -> {
                val recordId = arguments.getOrNull(1)?.toLongOrNull()
                if (recordId == null || !dialogUi.openDetail(player, recordId)) chestUi.openHistory(player)
            }
            "subscriptions" -> openSubscriptions(player, arguments.getOrNull(1).toPage())
            "filters" -> openFilters(player, arguments.getOrNull(1).toPage())
            "settings" -> if (!dialogUi.openSettings(player)) chestUi.open(player)
            "toggle" -> toggleDebugger(player)
            "refresh" -> refreshListeners(player)
            "toggle-history" -> {
                debugger.toggleHistory()
                openDashboard(player)
            }
            "apply-settings" -> applySettings(player, arguments)
            "clear-history" -> {
                debugger.remapper.clearHistory()
                openHistory(player, notice = "History cleared")
            }
            "toggle-sub" -> toggleSubscription(player, arguments)
            "toggle-plugin" -> togglePlugin(player, arguments)
            "add-form" -> if (!dialogUi.openAddForm(player)) chestUi.openSubscriptions(player)
            "add" -> addSubscription(player, arguments)
            else -> openDashboard(player)
        }
    }

    private fun openDashboard(player: Player, notice: String? = null) {
        if (!dialogUi.open(player, notice)) chestUi.open(player)
    }

    private fun toggleDebugger(player: Player) {
        val starting = !debugger.enabled
        debugger.setEnabled(starting)
        openDashboard(player, if (starting) "Debugger started" else "Debugger paused; listeners restored")
    }

    private fun refreshListeners(player: Player) {
        if (!debugger.enabled) {
            openDashboard(player, "Debugger is paused; start it before refreshing")
            return
        }
        val added = debugger.refreshListeners()
        val message = if (added == 0) {
            "Listeners are already up to date"
        } else {
            "$added new ${if (added == 1) "listener" else "listeners"} discovered"
        }
        openDashboard(player, message)
    }

    private fun applySettings(player: Player, arguments: List<String>) {
        val debugging = arguments.getOrNull(1).equals("on", ignoreCase = true)
        val history = arguments.getOrNull(2).equals("on", ignoreCase = true)
        val historyLimit = arguments.getOrNull(3)?.toFloatOrNull()?.toInt() ?: debugger.historyLimit
        val refreshTicks = arguments.getOrNull(4)?.toLongOrNull() ?: debugger.listenerRefreshTicks
        debugger.applySettings(debugging, history, historyLimit, refreshTicks)
        openDashboard(player, "Capture settings applied")
    }

    private fun toggleSubscription(player: Player, arguments: List<String>) {
        val key = arguments.getOrNull(1) ?: return openSubscriptions(player)
        val page = arguments.getOrNull(2).toPage()
        debugger.toggleSubscription(key)
        openSubscriptions(player, page)
    }

    private fun togglePlugin(player: Player, arguments: List<String>) {
        val pluginName = arguments.getOrNull(1) ?: return openFilters(player)
        val page = arguments.getOrNull(2).toPage()
        debugger.togglePlugin(pluginName)
        openFilters(player, page)
    }

    private fun addSubscription(player: Player, arguments: List<String>) {
        val className = arguments.getOrNull(1).orEmpty()
        val methods = arguments.drop(2)
            .joinToString("")
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        val error = debugger.addSubscription(className, methods)
        if (error == null) {
            openSubscriptions(player, notice = "Subscription added")
        } else if (!dialogUi.openAddForm(player, error)) {
            chestUi.openSubscriptions(player)
        }
    }

    private fun String?.toPage(): Int = this?.toIntOrNull() ?: 0
}
