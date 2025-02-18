package com.certora.collect

import com.certora.forkjoin.*

internal abstract class TreapMerger<
    @Treapable K, AV, BV, RV, TK: TreapKey<K>, A : Treap<K, AV, TK, A>, B : Treap<K, BV, TK, B>, R : Treap<K, RV, TK, R>
> {
    protected abstract fun shallowMerge(a: A?, b: B?): R?

    fun merge(a: A?, b: B?): R? = notForking(a to b) { mergeTreaps(a, b) }
    fun parallelMerge(a: A?, b: B?, parallelThresholdLog2: Int): R? = maybeForking(
        a to b,
        { (a, b) -> parallelMergeTheshold(a, b, parallelThresholdLog2) },
        { mergeTreaps(a, b) }
    )

    protected abstract fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean

    context(ThresholdForker<Pair<A?, B?>>)
    private fun mergeTreaps(a: A?, b: B?): R? = when {
        a == null && b == null -> null
        a == null || b == null -> mergeMismatch(a, b)
        else -> mergeKeepers(a, b)
    }

    context(ThresholdForker<Pair<A?, B?>>)
    protected abstract fun mergeMismatch(a: A?, b: B?): R?


    context(ThresholdForker<Pair<A?, B?>>)
    protected fun mergeKeepers(a: A?, b: B?): R? {
        val (left, right, center) = when {
            a == null || b == null || a.comparePriorityTo(b) >= 0 -> {
                val (bLeft, bRight, bDup) = b.split(a)
                mergeSplits(a?.left, bLeft, a?.right, bRight, a, bDup)
            }
            else -> {
                val (aLeft, aRight, aDup) = a.split(b)
                mergeSplits(aLeft, b.left, aRight, b.right, aDup, b)
            }
        }
        return center?.with(left, right) ?: (left join right)
    }

    context(ThresholdForker<Pair<A?, B?>>)
    private fun mergeSplits(
        aLeft: A?, bLeft: B?,
        aRight: A?, bRight: B?,
        aCenter: A?, bCenter: B?
    ) = fork(
        aLeft to bLeft,
        { mergeTreaps(aLeft, bLeft) },
        { mergeTreaps(aRight, bRight) },
        { shallowMerge(aCenter, bCenter) }
    )

    abstract class MergeAll<
        @Treapable K, AV, BV, RV, TK: TreapKey<K>, A : Treap<K, AV, TK, A>, B : Treap<K, BV, TK, B>, R : Treap<K, RV, TK, R>
    > : TreapMerger<K, AV, BV, RV, TK, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = mergeKeepers(a, b)
        override fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean =
            a.isApproximatelySmallerThanLog2(parallelThresholdLog2) && 
            b.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepAllMergeIntersection<
        @Treapable K, V, TK: TreapKey<K>, T : Treap<K, V, TK, T>
    > : TreapMerger<K, V, V, V, TK, T, T, T>() {
        context(ThresholdForker<Pair<T?, T?>>)
        override fun mergeMismatch(a: T?, b: T?): T? = a ?: b
        override fun parallelMergeTheshold(a: T?, b: T?, parallelThresholdLog2: Int): Boolean =
            a.isApproximatelySmallerThanLog2(parallelThresholdLog2) || 
            b.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepIntersection<
        @Treapable K, AV, BV, RV, TK: TreapKey<K>, A : Treap<K, AV, TK, A>, B : Treap<K, BV, TK, B>, R : Treap<K, RV, TK, R>
    > : TreapMerger<K, AV, BV, RV, TK, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = null
        override fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean =
            a.isApproximatelySmallerThanLog2(parallelThresholdLog2) || 
            b.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepA<
        @Treapable K, AV, BV, RV, TK: TreapKey<K>, A : Treap<K, AV, TK, A>, B : Treap<K, BV, TK, B>, R : Treap<K, RV, TK, R>
    > : TreapMerger<K, AV, BV, RV, TK, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = a?.let { mergeKeepers(it, b) }
        override fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean =
            a.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepAllMergeA<
        @Treapable K, V, TK: TreapKey<K>, T : Treap<K, V, TK, T>
    > : TreapMerger<K, V, V, V, TK, T, T, T>() {
        context(ThresholdForker<Pair<T?, T?>>)
        override fun mergeMismatch(a: T?, b: T?): T? = a?.let { mergeKeepers(it, b) } ?: b
        override fun parallelMergeTheshold(a: T?, b: T?, parallelThresholdLog2: Int): Boolean =
            a.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepB<
        @Treapable K, AV, BV, RV, TK: TreapKey<K>, A : Treap<K, AV, TK, A>, B : Treap<K, BV, TK, B>, R : Treap<K, RV, TK, R>
    > : TreapMerger<K, AV, BV, RV, TK, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = b?.let { mergeKeepers(a, it) }
        override fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean =
            b.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepAllMergeB<
        @Treapable K, V, TK: TreapKey<K>, T : Treap<K, V, TK, T>
    > : TreapMerger<K, V, V, V, TK, T, T, T>() {
        context(ThresholdForker<Pair<T?, T?>>)
        override fun mergeMismatch(a: T?, b: T?): T? = b?.let { mergeKeepers(a, it) } ?: a
        override fun parallelMergeTheshold(a: T?, b: T?, parallelThresholdLog2: Int): Boolean =
            b.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }
}
