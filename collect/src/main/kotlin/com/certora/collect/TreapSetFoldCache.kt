package com.certora.collect

import java.util.IdentityHashMap

/**
    A cache for intermediate results when folding over [TreapSet]s.

    This can significantly speed up [TreapSet.treapFold] when applied to multiple [TreapSet] instances which share nodes
    internally.
*/
public class TreapSetFoldCache {
    private val cache = IdentityHashMap<Any, Any>()

    @Suppress("UNCHECKED_CAST")
    internal inline operator fun <R : Any> invoke(treap: TreapSet<*>, action: () -> R) =
        cache.getOrPut(treap) { action() } as R
}
