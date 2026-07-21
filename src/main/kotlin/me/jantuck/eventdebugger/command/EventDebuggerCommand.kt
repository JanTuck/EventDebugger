package me.jantuck.eventdebugger.command

import me.jantuck.eventdebugger.core.DebuggerController
import me.jantuck.eventdebugger.ui.DebuggerUiController

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

internal class EventDebuggerCommand(
    private val debugger: DebuggerController,
    private val ui: DebuggerUiController
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        when (args.firstOrNull()?.lowercase() ?: "status") {
            "status" -> showStatus(sender)
            "gui" -> withPlayer(sender) { ui.open(it, args.getOrNull(1).equals("chest", ignoreCase = true)) }
            "dialog" -> withPlayer(sender) { ui.handleDialogCommand(it, args.drop(1)) }
            "history" -> withPlayer(sender, ui::openHistory)
            "events", "activity" -> withPlayer(sender, ui::openEvents)
            "filters" -> withPlayer(sender, ui::openFilters)
            "subscriptions", "list" -> {
                if (args.getOrNull(1).equals("gui", ignoreCase = true)) {
                    withPlayer(sender, ui::openSubscriptions)
                } else {
                    showSubscriptions(sender)
                }
            }
            "refresh" -> refresh(sender, label)
            "reload" -> {
                debugger.reload()
                sender.notice(
                    "Configuration reloaded; ${debugger.remapper.status().eventTypes} event types are watched."
                )
            }
            "on", "enable" -> setEnabled(sender, true)
            "off", "disable" -> setEnabled(sender, false)
            "help" -> showHelp(sender, label)
            else -> sender.notice("Unknown subcommand '${args[0]}'. Use /$label help.", ChatColor.RED)
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size != 1) return emptyList()
        val prefix = args[0].lowercase()
        return SUBCOMMANDS.filter { it.startsWith(prefix) }
    }

    private fun showStatus(sender: CommandSender) {
        val status = debugger.remapper.status()
        sender.notice(
            "EventDebugger is ${if (debugger.enabled) "enabled" else "disabled"}.",
            if (debugger.enabled) ChatColor.GREEN else ChatColor.YELLOW
        )
        sender.notice("Watching ${status.watchedMethods} methods on ${status.eventTypes} event types.")
        sender.notice(
            "Instrumented listeners: ${status.wrappedListeners}; inspected calls: ${status.inspectedCalls}; " +
                "changes: ${status.detectedChanges}."
        )
        val refresh = debugger.listenerRefreshTicks
            .takeUnless { it == 0L }
            ?.let { "every $it ticks" }
            ?: "manual only"
        sender.notice("Listener refresh: $refresh.")
    }

    private fun showSubscriptions(sender: CommandSender) {
        val subscriptions = debugger.configuredSubscriptions()
        if (subscriptions.isEmpty()) {
            sender.notice("No exact event subscriptions are configured.", ChatColor.YELLOW)
            return
        }
        sender.notice("Configured exact subscriptions:")
        subscriptions.forEach { subscription ->
            val methods = subscription.methods.joinToString(", ").ifEmpty { "<no methods>" }
            val state = if (subscription.disabled) " ${ChatColor.RED}(disabled)" else ""
            sender.sendMessage(
                "${ChatColor.GRAY}- ${subscription.className.substringAfterLast('.')}: $methods$state"
            )
        }
        if (debugger.cancellableNamespaces.isNotEmpty()) {
            sender.sendMessage(
                "${ChatColor.GRAY}- All cancellable events in: ${debugger.cancellableNamespaces.joinToString()}"
            )
        }
    }

    private fun refresh(sender: CommandSender, label: String) {
        if (!debugger.enabled) {
            sender.notice("Debugging is disabled. Use /$label on first.", ChatColor.YELLOW)
            return
        }
        val added = debugger.refreshListeners()
        sender.notice(
            if (added == 0) "Listeners are up to date."
            else "Now watching $added additional listener${if (added == 1) "" else "s"}."
        )
    }

    private fun setEnabled(sender: CommandSender, enabled: Boolean) {
        if (!debugger.setEnabled(enabled)) {
            sender.notice("Debugging is already ${if (enabled) "enabled" else "disabled"}.", ChatColor.YELLOW)
            return
        }
        sender.notice(
            if (enabled) "Debugging enabled."
            else "Debugging disabled; all listener executors were restored."
        )
    }

    private fun showHelp(sender: CommandSender, label: String) {
        sender.notice("Commands:")
        sender.sendMessage("${ChatColor.GRAY}/$label gui ${ChatColor.DARK_GRAY}- open the visual debugger")
        sender.sendMessage("${ChatColor.GRAY}/$label events|history|filters ${ChatColor.DARK_GRAY}- open a UI page")
        sender.sendMessage("${ChatColor.GRAY}/$label status ${ChatColor.DARK_GRAY}- current counters and state")
        sender.sendMessage("${ChatColor.GRAY}/$label subscriptions ${ChatColor.DARK_GRAY}- configured watches")
        sender.sendMessage("${ChatColor.GRAY}/$label refresh ${ChatColor.DARK_GRAY}- discover newly registered listeners")
        sender.sendMessage("${ChatColor.GRAY}/$label reload ${ChatColor.DARK_GRAY}- safely reload config.yml")
        sender.sendMessage("${ChatColor.GRAY}/$label on|off ${ChatColor.DARK_GRAY}- persistently toggle debugging")
    }

    private fun withPlayer(sender: CommandSender, action: (Player) -> Unit) {
        val player = sender as? Player
        if (player == null) sender.notice("This command requires a player.", ChatColor.RED) else action(player)
    }

    private companion object {
        val SUBCOMMANDS = listOf(
            "gui",
            "status",
            "events",
            "history",
            "subscriptions",
            "filters",
            "refresh",
            "reload",
            "on",
            "off",
            "help"
        )
    }
}
