package me.jantuck.kotlinplugin

import com.okkero.skedule.schedule
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class KotlinPlugin : JavaPlugin() {
    override fun onEnable() {
        this.schedule {
            repeating(20)
            do {
                Bukkit.getOnlinePlayers().forEach {
                    it.sendMessage("Dick")
                }
                yield()
            } while (true)
        }
    }
}