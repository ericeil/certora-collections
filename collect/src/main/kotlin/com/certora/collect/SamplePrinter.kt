package com.certora.collect

import com.thedeanda.lorem.LoremIpsum
import java.nio.file.*
import java.util.IdentityHashMap

private fun printMermaidGraph(sets: Map<String, Treap<*, *>>): String {
    var nextNodeNum = 0
    val nodeNames = IdentityHashMap<Any, String>()
    fun nodeName(obj: Any) = nodeNames.getOrPut(obj) { "n${nextNodeNum++}" }

    val output = StringBuilder()
    output.appendLine("graph TD")
    for ((setName, set) in sets) {
        output.appendLine("subgraph \"$setName\"")
        fun Treap<*, *>.nodeName(): String = when(this) {
            is SortedTreapSet<*> -> "${nodeName(this)}[\"$treapKey\"]"
            is SortedTreapMap<*, *> -> "${nodeName(this)}[\"$treapKey: $value\"]"
            else -> error("Can't handle $this")
        }
        fun Treap<*, *>.print() {
            left?.let {
                if (it !in nodeNames) {
                    it.print()
                }
                output.appendLine("${nodeName()} -->|L| ${it.nodeName()}")
            }
            right?.let {
                if (it !in nodeNames) {
                    it.print()
                }
                output.appendLine("${nodeName()} -->|R| ${it.nodeName()}")
            }
        }
        if (set !in nodeNames) {
            set.print()
        }
        output.appendLine("end")
    }
    return output.toString()
}

internal fun main(args: Array<String>) {
    val lorem = LoremIpsum.getInstance()

    val outputDir = Path.of(args[0])
    Files.createDirectories(outputDir)
    Files.newBufferedWriter(outputDir.resolve("sets.md")).use { file ->
        fun sample(name: String, sets: () -> Map<String, Any>) {
            val s = sets()
            file.write("# $name\n")
            s.forEach { (name, set) ->
                file.write("$name = $set\n\n")
            }
            file.write("```mermaid\n")
            @Suppress("UNCHECKED_CAST")
            file.write(printMermaidGraph(s as Map<String, Treap<*, *>>))
            file.write("```\n")
        }

        sample("Add to set (high)") {
            val a = (1..20).toTreapSet()
            mapOf("a" to a, "a+21" to (a + 21))
        }

        sample("Add to set (middle)") {
            val a = (1..40 step 2).toTreapSet()
            mapOf("a" to a, "a+20" to (a + 20))
        }

        sample("Remove from set (high)") {
            val a = (1..20).toTreapSet()
            mapOf("a" to a, "a-20" to (a - 20))
        }

        sample("Remove from set (middle)") {
            val a = (1..20).toTreapSet()
            mapOf("a" to a, "a-10" to (a - 10))
        }

        sample("Union disjoint sets") {
            val a = (1..20).toTreapSet()
            val b = (10..30).toTreapSet()
            mapOf("b" to b, "a" to a, "a+b" to a+b)
        }

        sample("Union disjoint (interleaved) sets") {
            val a = (1..40 step 2).toTreapSet()
            val b = (2..40 step 2).toTreapSet()
            mapOf("b" to b, "a" to a, "a+b" to a+b)
        }

        sample("Union non-disjoint sets") {
            val a = (1..20).toTreapSet()
            val b = (10..30).toTreapSet()
            mapOf("b" to b, "a" to a, "a+b" to a+b)
        }

        sample("Union non-disjoint (interleaved) sets") {
            val a = (1..20).toTreapSet()
            val b = (1..40 step 2).toTreapSet()
            mapOf("b" to b, "a" to a, "a+b" to a+b)
        }

        sample("Intersect non-disjoint sets") {
            val a = (1..20).toTreapSet()
            val b = (10..30).toTreapSet()
            mapOf("b" to b, "a" to a, "a intersect b" to (a intersect b))
        }

        sample("Subtract non-disjoint sets") {
            val a = (1..20).toTreapSet()
            val b = (10..30).toTreapSet()
            mapOf("b" to b, "a" to a, "a - b" to (a - b), "b - a" to (b - a))
        }

        sample("Subtract non-disjoint (interleaved) sets") {
            val a = (1..20).toTreapSet()
            val b = (1..40 step 2).toTreapSet()
            mapOf("b" to b, "a" to a, "a - b" to (a - b), "b - a" to (b - a))
        }

        sample("Set map value") {
            val a = (1..20).map { it to lorem.getWords(1) }.toTreapMap()
            mapOf("a" to a, "a.put(10, 'foo')" to a.put(10, "foo"))
        }

        sample("Merge maps") {
            val a = (1..20).map { it to lorem.getWords(1) }.toTreapMap()
            val b = (15..35).map { it to lorem.getWords(1) }.toTreapMap()
            val m = a.merge(b) { _, x, y ->
                when {
                    x == null -> y
                    y == null -> x
                    else -> "$x $y"
                }
            }
            mapOf("b" to b, "a" to a, "a.merge(b)" to m)
        }
    }
}
