package me.jantuck.eventdebugger

import me.jantuck.eventdebugger.command.EventDebuggerCommand
import me.jantuck.eventdebugger.core.DebuggerController
import me.jantuck.eventdebugger.ui.DebuggerUiController
import org.bukkit.plugin.java.JavaPlugin

class EventDebugger : JavaPlugin() {
    private lateinit var debugger: DebuggerController

    override fun onEnable() {
        saveDefaultConfig()
        debugger = DebuggerController(this)
        val ui = DebuggerUiController(this, debugger)
        val commandHandler = EventDebuggerCommand(debugger, ui)

        getCommand("eventdebugger")?.apply {
            setExecutor(commandHandler)
            tabCompleter = commandHandler
        } ?: logger.severe("The eventdebugger command is missing from plugin.yml.")

        server.pluginManager.registerEvents(debugger, this)
        server.pluginManager.registerEvents(ui.listener, this)
        debugger.start()
        logger.info("Ready. Use /eventdebugger status to see what is being watched.")
    }

    override fun onDisable() {
        if (::debugger.isInitialized) debugger.stop()
    }
}
