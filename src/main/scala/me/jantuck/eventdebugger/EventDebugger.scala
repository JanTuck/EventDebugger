package me.jantuck.eventdebugger

import com.google.common.collect.ArrayListMultimap
import me.jantuck.eventdebugger.eventremapper.EventUtil
import org.bukkit.event.Event
import org.bukkit.plugin.java.JavaPlugin

import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsScala}


class EventDebugger extends JavaPlugin {
  override def onEnable() {
    saveDefaultConfig()
    val map = ArrayListMultimap.create[Class[_ <: Event], String]()
    getConfig.getKeys(false).forEach(key => {
      val section = getConfig.getConfigurationSection(key)
      val clazz = Class.forName(section.getString("class")).asInstanceOf[Class[_ <: Event]]
      map.putAll(clazz, section.getStringList("methods"))
    })
    getServer.getScheduler.runTask(this, _ => map.asMap.asScala.foreach(entry => EventUtil.remapAndListen(entry._1, entry._2.asScala.toList)))
  }

}
