package me.jantuck.eventdebugger.core

import com.esotericsoftware.reflectasm.MethodAccess
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap

internal class ValueSnapshotter {
    private val cloneAccess = ConcurrentHashMap<Class<*>, Pair<Int, MethodAccess>>()
    private val unclonableClasses = ConcurrentHashMap.newKeySet<Class<*>>()

    fun snapshot(value: Any?): Any? = when (value) {
        null -> null
        is ItemStack -> value.clone()
        is Map<*, *> -> LinkedHashMap(value)
        is Collection<*> -> ArrayList(value)
        is Array<*> -> value.clone()
        is BooleanArray -> value.clone()
        is ByteArray -> value.clone()
        is CharArray -> value.clone()
        is DoubleArray -> value.clone()
        is FloatArray -> value.clone()
        is IntArray -> value.clone()
        is LongArray -> value.clone()
        is ShortArray -> value.clone()
        !is Cloneable -> value
        else -> clone(value)
    }

    fun equivalent(first: Any?, second: Any?): Boolean = when {
        first is Array<*> && second is Array<*> -> first.contentDeepEquals(second)
        first is BooleanArray && second is BooleanArray -> first.contentEquals(second)
        first is ByteArray && second is ByteArray -> first.contentEquals(second)
        first is CharArray && second is CharArray -> first.contentEquals(second)
        first is DoubleArray && second is DoubleArray -> first.contentEquals(second)
        first is FloatArray && second is FloatArray -> first.contentEquals(second)
        first is IntArray && second is IntArray -> first.contentEquals(second)
        first is LongArray && second is LongArray -> first.contentEquals(second)
        first is ShortArray && second is ShortArray -> first.contentEquals(second)
        else -> first == second
    }

    fun format(value: Any?, maxLength: Int): String {
        val text = try {
            when (value) {
                null -> "null"
                is Array<*> -> value.contentDeepToString()
                is BooleanArray -> value.contentToString()
                is ByteArray -> value.contentToString()
                is CharArray -> value.contentToString()
                is DoubleArray -> value.contentToString()
                is FloatArray -> value.contentToString()
                is IntArray -> value.contentToString()
                is LongArray -> value.contentToString()
                is ShortArray -> value.contentToString()
                else -> value.toString()
            }
        } catch (_: Throwable) {
            "<unprintable ${value?.javaClass?.simpleName ?: "value"}>"
        }.replace("\r", "\\r").replace("\n", "\\n")

        val limit = maxLength.coerceAtLeast(20)
        return if (text.length <= limit) text else text.take(limit - 1) + "\u2026"
    }

    private fun clone(value: Any): Any {
        val type = value.javaClass
        if (type in unclonableClasses) return value
        return try {
            val (index, access) = cloneAccess.computeIfAbsent(type) {
                MethodAccess.get(it).let { methodAccess -> methodAccess.getIndex("clone") to methodAccess }
            }
            access.invoke(value, index) ?: value
        } catch (_: Exception) {
            unclonableClasses.add(type)
            value
        }
    }
}
