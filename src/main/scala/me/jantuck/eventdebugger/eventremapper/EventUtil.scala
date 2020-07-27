package me.jantuck.eventdebugger.eventremapper

import java.lang.invoke.MethodHandles

import com.esotericsoftware.reflectasm.MethodAccess
import com.google.common.collect.ArrayListMultimap
import org.bukkit.event.{Event, HandlerList, Listener}
import org.bukkit.plugin.{EventExecutor, RegisteredListener}

import scala.collection.mutable

object EventUtil {

  private val methodLookup = MethodHandles.lookup()
  private val executorFieldReflection = classOf[RegisteredListener].getDeclaredField("executor")
  executorFieldReflection.setAccessible(true)
  private val executorFieldGetter = methodLookup.unreflectGetter(executorFieldReflection)
  private val executorFieldSetter = methodLookup.unreflectSetter(executorFieldReflection)

  /*
    Caches
   */
  private val eventIndexAccess = mutable.Map.empty[Class[_ <: Event], mutable.Map[String, Int]]
  private val eventAccess = mutable.Map.empty[Class[_ <: Event], MethodAccess]
  private val eventToMethod = ArrayListMultimap.create[Class[_ <: Event], String]()

  def remapAndListen(clazz: Class[_ <: Event], subscribed: List[String]): Unit = {
    val handlerList = clazz.getDeclaredMethod("getHandlerList").invoke(null).asInstanceOf[HandlerList]
    val registeredListeners = handlerList.getRegisteredListeners

    registeredListeners.foreach(listener => {
      val oldExecutor = executorFieldGetter.invoke(listener).asInstanceOf[EventExecutor]
      val newExecutor = new EventExecutor {
        override def execute(listener1: Listener, event: Event): Unit = {
          _executeAndCheckChanges((oldExecutor, listener1, event), listener)
        }
      }
      executorFieldSetter.invoke(listener, newExecutor)

      /*
        Cache method access and stuff.
       */
      subscribeToMethods(clazz, subscribed)
    }
    )
  }

  private def subscribeToMethods(clazz: Class[_ <: Event], methods: List[String]): Unit ={
    val methodAccess = MethodAccess.get(clazz)
    eventAccess(clazz) = methodAccess // Cached
    methods.foreach(method => {
      val cachedIndex = eventIndexAccess.getOrElse(clazz, mutable.Map.empty)
      eventIndexAccess(clazz) = cachedIndex
      cachedIndex(method) = methodAccess.getIndex(method)
      eventToMethod.put(clazz, method)
    })
  }

  private def _executeAndCheckChanges(executionDetails: (EventExecutor, Listener, Event), registeredListener: RegisteredListener): Unit = {
    val event = executionDetails._3
    val before = getValuesSubscribedTo(event)
    executionDetails._1.execute(executionDetails._2, event)
    val after = getValuesSubscribedTo(event)
    notifyChanges(executionDetails._3 -> registeredListener, before, after)
  }

  private def getValuesSubscribedTo(event: Event): mutable.Map[(String, Int), AnyRef] = {
    val map = mutable.Map.empty[(String, Int), AnyRef]
    eventToMethod.get(event.getClass).forEach(method => {
      val methodAccess = eventAccess(event.getClass)
      val indices = eventIndexAccess(event.getClass)
      val index = indices(method)
      map(method -> index) = methodAccess.invoke(event, index)
    })
    map
  }

  private def notifyChanges(event: (Event, RegisteredListener), before: mutable.Map[(String, Int), AnyRef], after: mutable.Map[(String, Int), AnyRef]): Unit = {
    var first = true
    val logger = event._2.getPlugin.getLogger
    before.foreach(entry => {
      val newValue = after(entry._1)
      if (newValue != entry._2){
        if (first){
          logger.info("Event Debugger START")
          logger.info("Logger hijacked by Event Debugger // Ignore")
          logger.info(s"-> Change detected in event '${event._1.getEventName}'")
          logger.info(s"-> By plugin '${event._2.getPlugin.getName}'")
          first = false
        }
        logger.info(s"-> Detected change in '${entry._1._1}' from '${entry._2}' -> '$newValue'")
      }
    })
    if (!first){
      logger.info("Event Debugger END")
    }
  }
}
