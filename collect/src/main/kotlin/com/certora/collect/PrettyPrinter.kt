package com.certora.collect

import java.nio.file.*
import java.util.IdentityHashMap

/**
    Provides methods for pretty-printing the collections in this package.
 */
public object PrettyPrinter {
    public fun <T> printMermaidGraph(sets: Map<String, TreapSet<T>>): String {
        var nextNodeNum = 0
        val nodeNames = IdentityHashMap<Any, String>()
        fun nodeName(obj: Any) = nodeNames.getOrPut(obj) { "n${nextNodeNum++}" }

        val output = StringBuilder()
        output.appendLine("graph TD")
        for ((setName, set) in sets) {
            output.appendLine("subgraph $setName")
            if (set is AbstractTreapSet<*, *>) {
                fun AbstractTreapSet<*,*>.nodeName(): String = "${nodeName(this)}[${treapKey.toString()}]"
                fun AbstractTreapSet<*,*>.print() {
                    listOfNotNull(left, right).forEach {
                        if (it !in nodeNames) {
                            it.print()
                        }
                        output.appendLine("${nodeName()} --> ${it.nodeName()}")
                    }
                }
                if (set !in nodeNames) {
                    set.print()
                }
            }
            output.appendLine("end")
        }
        return output.toString()
    }
}

public fun main(args: Array<String>) {
    val outputDir = Path.of(args[0])
    Files.createDirectories(outputDir)
    Files.newBufferedWriter(outputDir.resolve("sets.md")).use { file ->
        fun sample(name: String, sets: () -> Map<String, TreapSet<Int>>) {
        val setsText = """
# $name
```mermaid
${PrettyPrinter.printMermaidGraph(sets())}
```
"""
        file.write(setsText)
        }

        sample("Union disjoint sets") {
            val a = (1..20).toTreapSet()
            val b = (10..30).toTreapSet()
            mapOf("a" to a, "b" to b, "a+b" to a+b)
        }

        sample("Union overlapping sets") {
            val a = (1..20).toTreapSet()
            val b = (10..30).toTreapSet()
            mapOf("a" to a, "b" to b, "a+b" to a+b)
        }

        sample("Intersect overlapping sets") {
            val a = (1..20).toTreapSet()
            val b = (10..30).toTreapSet()
            mapOf("a" to a, "b" to b, "a intersect b" to (a intersect b))
        }

        sample("Subtract overlapping sets") {
            val a = (1..20).toTreapSet()
            val b = (10..30).toTreapSet()
            mapOf("a" to a, "b" to b, "a - b" to (a - b), "b - a" to (b - a))
        }
    }
}
