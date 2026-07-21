package me.jantuck.eventdebugger.command

import org.bukkit.ChatColor
import org.bukkit.command.CommandSender

internal fun CommandSender.notice(message: String, color: ChatColor = ChatColor.AQUA) {
    sendMessage("$color[EventDebugger] ${ChatColor.RESET}$message")
}
