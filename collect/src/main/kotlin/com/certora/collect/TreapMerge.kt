package com.certora.collect

import com.certora.forkjoin.*

internal enum class TreapMergeMode {
    A,
    B,
    UNION,
    INTERSECTION
}

context(ThresholdForker<Pair<A?, B?>>)
internal fun <@Treapable K, A, B, R> treapMerge(
    a: A?,
    b: B?,
    mode: TreapMergeMode,
    merge: (A?, B?) -> R?
): R? 
where 
    A : Treap<K, A>,
    B : Treap<K, B>,
    R : Treap<K, R>
{
    if (a == null && b == null) {
        return null
    }
    if (a == null) {
        when (mode) {
            TreapMergeMode.A, TreapMergeMode.INTERSECTION -> return null
            TreapMergeMode.B, TreapMergeMode.UNION -> {}
        }
    }
    if (b == null) {
        when (mode) {
            TreapMergeMode.B, TreapMergeMode.INTERSECTION -> return null
            TreapMergeMode.A, TreapMergeMode.UNION -> {}
        }
    }

    fun merge(
        aLeft: A?, bLeft: B?,
        aRight: A?, bRight: B?,
        aCenter: A?, bCenter: B?
    ) = fork(
        a to b,
        { treapMerge(aLeft, bLeft, mode, merge) },
        { treapMerge(aRight, bRight, mode, merge) },
        { merge(aCenter, bCenter) }
    )

    val (left, right, center) = when {
        a == null || b == null || a.comparePriorityTo(b) >= 0 -> {
            val (bLeft, bRight, bDup) = b.split(a)
            merge(a?.left, bLeft, a?.right, bRight, a, bDup)
        }
        else -> {
            val (aLeft, aRight, aDup) = a.split(b)
            merge(aLeft, b.left, aRight, b.right, aDup, b)
        }
    }

    return center?.with(left, right) ?: (left join right)
}