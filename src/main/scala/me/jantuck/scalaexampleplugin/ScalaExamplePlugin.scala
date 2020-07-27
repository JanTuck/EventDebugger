package me.jantuck.scalaexampleplugin

import org.bukkit.plugin.java.JavaPlugin

class ScalaExamplePlugin extends JavaPlugin {
  override def onEnable() {
    getLogger.info("<- Scala is fun.")
  }
}
