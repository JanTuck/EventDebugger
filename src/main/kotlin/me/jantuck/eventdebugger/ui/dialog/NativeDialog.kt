package me.jantuck.eventdebugger.ui.dialog

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.function.Consumer

object NativeDialog {
    data class Action(
        val label: String,
        val tooltip: String,
        val command: String,
        val color: Int = 0x67E8F9
    )

    data class Span(
        val text: String,
        val color: Int,
        val command: String? = null,
        val tooltip: String? = null
    )

    data class Line(val spans: List<Span>)

    sealed class Input {
        abstract val key: String
        abstract val label: String
        abstract val color: Int

        data class Text(
            override val key: String,
            override val label: String,
            val initial: String,
            val maxLength: Int,
            val width: Int = 440,
            override val color: Int = 0x67E8F9
        ) : Input()

        data class Toggle(
            override val key: String,
            override val label: String,
            override val color: Int,
            val initial: Boolean,
            val onTrue: String = "true",
            val onFalse: String = "false"
        ) : Input()

        data class Slider(
            override val key: String,
            override val label: String,
            override val color: Int,
            val min: Float,
            val max: Float,
            val initial: Float,
            val step: Float,
            val format: String = "options.generic_value"
        ) : Input()

        data class Choice(
            override val key: String,
            override val label: String,
            override val color: Int,
            val options: List<Option>,
            val width: Int = 440,
            val labelVisible: Boolean = true
        ) : Input()
    }

    data class Option(val id: String, val label: String, val selected: Boolean = false, val color: Int)

    data class Screen(
        val title: String,
        val icon: ItemStack,
        val content: List<Line>,
        val actions: List<Action>,
        val exitAction: Action? = null,
        val inputs: List<Input> = emptyList(),
        val templateActions: Boolean = false,
        val titleColor: Int = 0x67E8F9,
        val mutedColor: Int = 0x94A3B8,
        val contentWidth: Int = 280,
        val buttonWidth: Int = 200,
        val columns: Int = 2,
        val iconSize: Int = 26
    )

    private val runtime = runCatching { Runtime() }
    @Volatile private var lastFailure: Throwable? = runtime.exceptionOrNull()

    fun show(player: Player, screen: Screen): Boolean {
        val active = runtime.getOrNull() ?: return false
        return try {
            active.show(player, screen)
            lastFailure = null
            true
        } catch (throwable: Throwable) {
            lastFailure = throwable
            false
        }
    }

    fun failure(): String? = lastFailure?.let(::rootMessage)

    private class Runtime {
        private data class MethodKey(val type: Class<*>, val name: String, val arguments: List<Class<*>>)

        private val dialogClass = Class.forName("io.papermc.paper.dialog.Dialog")
        private val providerClass = Class.forName("io.papermc.paper.registry.data.dialog.DialogInstancesProvider")
        private val componentClass = Class.forName("net.kyori.adventure.text.Component")
        private val textColorClass = Class.forName("net.kyori.adventure.text.format.TextColor")
        private val clickEventClass = Class.forName("net.kyori.adventure.text.event.ClickEvent")
        private val hoverEventClass = Class.forName("net.kyori.adventure.text.event.HoverEvent")
        private val hoverEventSourceClass = Class.forName("net.kyori.adventure.text.event.HoverEventSource")
        private val textDecorationClass = Class.forName("net.kyori.adventure.text.format.TextDecoration")
        private val afterActionClass = Class.forName("io.papermc.paper.registry.data.dialog.DialogBase\$DialogAfterAction")
        private val provider: Any = providerClass.getMethod("instance").invoke(null)
        private val colorMethod = textColorClass.getMethod("color", Int::class.javaPrimitiveType)
        private val textMethod = componentClass.getMethod("text", String::class.java, textColorClass)
        private val emptyMethod = componentClass.getMethod("empty")
        private val newlineMethod = componentClass.getMethod("newline")
        private val appendMethod = componentClass.getMethod("append", componentClass)
        private val runCommandMethod = clickEventClass.getMethod("runCommand", String::class.java)
        private val clickEventMethod = componentClass.getMethod("clickEvent", clickEventClass)
        private val hoverEventMethod = componentClass.getMethod("hoverEvent", hoverEventSourceClass)
        private val showTextMethod = hoverEventClass.getMethod("showText", componentClass)
        private val decorateMethod = componentClass.getMethod("decorate", textDecorationClass)
        private val underlined = textDecorationClass.enumConstants.first { (it as Enum<*>).name == "UNDERLINED" }
        private val afterActionNone = afterActionClass.enumConstants.first { (it as Enum<*>).name == "NONE" }
        private val methods = HashMap<MethodKey, java.lang.reflect.Method>()

        fun show(player: Player, screen: Screen) {
            val dialog = dialogClass.getMethod("create", Consumer::class.java).invoke(null, Consumer<Any> { factory ->
                val registryBuilder = call(factory, "empty")
                val baseBuilder = call(provider, "dialogBaseBuilder", text(screen.title, screen.titleColor))
                call(baseBuilder, "canCloseWithEscape", true)
                call(baseBuilder, "pause", false)
                call(baseBuilder, "afterAction", afterActionNone)

                val description = call(provider, "plainMessageDialogBody", rich(screen), screen.contentWidth)
                val iconBuilder = call(provider, "itemDialogBodyBuilder", screen.icon)
                call(iconBuilder, "description", description)
                call(iconBuilder, "showDecorations", true)
                call(iconBuilder, "showTooltip", false)
                call(iconBuilder, "width", screen.iconSize)
                call(iconBuilder, "height", screen.iconSize)
                call(baseBuilder, "body", listOf(call(iconBuilder, "build")))

                if (screen.inputs.isNotEmpty()) call(baseBuilder, "inputs", screen.inputs.map(::buildInput))
                val base = call(baseBuilder, "build")
                val buttons = screen.actions.take(12).map { buildButton(it, screen) }
                val typeBuilder = call(provider, "multiAction", buttons)
                call(typeBuilder, "columns", screen.columns.coerceAtLeast(1))
                screen.exitAction?.let { call(typeBuilder, "exitAction", buildButton(it, screen)) }
                call(registryBuilder, "base", base)
                call(registryBuilder, "type", call(typeBuilder, "build"))
            })
            player.javaClass.methods.first { it.name == "showDialog" && it.parameterCount == 1 }.invoke(player, dialog)
        }

        private fun buildButton(action: Action, screen: Screen): Any {
            val builder = call(provider, "actionButtonBuilder", text(action.label, action.color))
            call(builder, "tooltip", text(action.tooltip, screen.mutedColor))
            call(builder, "width", screen.buttonWidth)
            val value = if (screen.templateActions && action.command.contains("\$(")) {
                call(provider, "commandTemplate", action.command)
            } else {
                call(provider, "staticAction", runCommandMethod.invoke(null, "/${action.command}"))
            }
            call(builder, "action", value)
            return call(builder, "build")
        }

        private fun buildInput(input: Input): Any = when (input) {
            is Input.Text -> {
                val builder = call(provider, "textBuilder", input.key, text(input.label, input.color))
                call(builder, "width", input.width)
                call(builder, "initial", input.initial)
                call(builder, "maxLength", input.maxLength)
                call(builder, "build")
            }
            is Input.Toggle -> {
                val builder = call(provider, "booleanBuilder", input.key, text(input.label, input.color))
                call(builder, "initial", input.initial)
                call(builder, "onTrue", input.onTrue)
                call(builder, "onFalse", input.onFalse)
                call(builder, "build")
            }
            is Input.Slider -> {
                val builder = call(provider, "numberRangeBuilder", input.key, text(input.label, input.color), input.min, input.max)
                call(builder, "initial", input.initial)
                call(builder, "step", input.step)
                call(builder, "labelFormat", input.format)
                call(builder, "width", 440)
                call(builder, "build")
            }
            is Input.Choice -> {
                val entries = input.options.map { option ->
                    call(provider, "singleOptionEntry", option.id, text(option.label, option.color), option.selected)
                }
                val builder = call(provider, "singleOptionBuilder", input.key, text(input.label, input.color), entries)
                call(builder, "width", input.width)
                call(builder, "labelVisible", input.labelVisible)
                call(builder, "build")
            }
        }

        private fun rich(screen: Screen): Any {
            var result = emptyMethod.invoke(null)
            screen.content.take(48).forEachIndexed { index, line ->
                if (index > 0) result = appendMethod.invoke(result, newlineMethod.invoke(null))
                line.spans.forEach { span ->
                    var part = text(span.text, span.color)
                    if (span.command != null) {
                        part = clickEventMethod.invoke(part, runCommandMethod.invoke(null, "/${span.command}"))
                        part = decorateMethod.invoke(part, underlined)
                        span.tooltip?.let {
                            part = hoverEventMethod.invoke(part, showTextMethod.invoke(null, text(it, screen.mutedColor)))
                        }
                    }
                    result = appendMethod.invoke(result, part)
                }
            }
            return result
        }

        private fun text(value: String, color: Int): Any =
            textMethod.invoke(null, value, colorMethod.invoke(null, color))

        private fun call(target: Any, name: String, vararg args: Any): Any {
            val key = MethodKey(target.javaClass, name, args.map(Any::javaClass))
            val method = methods.getOrPut(key) {
                target.javaClass.methods.first { candidate ->
                    candidate.name == name && candidate.parameterCount == args.size &&
                        candidate.parameterTypes.zip(args).all { (type, argument) ->
                            type.isAssignableFrom(argument.javaClass) ||
                                (type.isPrimitive && primitiveWrapper(type).isAssignableFrom(argument.javaClass))
                        }
                }
            }
            return method.invoke(target, *args)
        }

        private fun primitiveWrapper(type: Class<*>): Class<*> = when (type) {
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            else -> type
        }
    }

    private fun rootMessage(throwable: Throwable): String {
        var current = throwable
        while (current.cause != null) current = current.cause!!
        return current.message ?: current.javaClass.simpleName
    }
}
