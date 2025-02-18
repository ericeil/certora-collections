package com.certora.collect

import com.certora.forkjoin.*

internal abstract class  TreapMerger<
    @Treapable K, TK: TreapKey<K>, A : Treap<K, TK, A>, B : Treap<K, TK, B>, R : Treap<K, TK, R>
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
    protected abstract fun shallowMergeMismatch(a: A?, b: B?): R?

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
        { 
            when {
                aCenter == null || bCenter == null -> shallowMergeMismatch(aCenter, bCenter)
                else -> shallowMerge(aCenter, bCenter)
            }
        }
    )

    abstract class MergeAll<
        @Treapable K, TK: TreapKey<K>, A : Treap<K, TK, A>, B : Treap<K, TK, B>, R : Treap<K, TK, R>
    > : TreapMerger<K, TK, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = mergeKeepers(a, b)
        context(ThresholdForker<Pair<A?, B?>>)
        override fun shallowMergeMismatch(a: A?, b: B?): R? = shallowMerge(a, b)
        override fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean =
            a.isApproximatelySmallerThanLog2(parallelThresholdLog2) && 
            b.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepAllMergeIntersection<
        @Treapable K, TK: TreapKey<K>, T : Treap<K, TK, T>
    > : TreapMerger<K, TK, T, T, T>() {
        context(ThresholdForker<Pair<T?, T?>>)
        override fun mergeMismatch(a: T?, b: T?): T? = a ?: b
        context(ThresholdForker<Pair<T?, T?>>)
        override fun shallowMergeMismatch(a: T?, b: T?): T? = a ?: b
        override fun parallelMergeTheshold(a: T?, b: T?, parallelThresholdLog2: Int): Boolean =
            a.isApproximatelySmallerThanLog2(parallelThresholdLog2) || 
            b.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepIntersection<
        @Treapable K, TK: TreapKey<K>, A : Treap<K, TK, A>, B : Treap<K, TK, B>, R : Treap<K, TK, R>
    > : TreapMerger<K, TK, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = null
        context(ThresholdForker<Pair<A?, B?>>)
        override fun shallowMergeMismatch(a: A?, b: B?): R? = null
        override fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean =
            a.isApproximatelySmallerThanLog2(parallelThresholdLog2) || 
            b.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepA<
        @Treapable K, TK: TreapKey<K>, A : Treap<K, TK, A>, B : Treap<K, TK, B>, R : Treap<K, TK, R>
    > : TreapMerger<K, TK, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = a?.let { mergeKeepers(it, b) }
        context(ThresholdForker<Pair<A?, B?>>)
        override fun shallowMergeMismatch(a: A?, b: B?): R? = a?.let { shallowMerge(it, b) }
        override fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean =
            a.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepAllMergeA<
        @Treapable K, TK: TreapKey<K>, A : Treap<K, TK, A>, B : Treap<K, TK, B>
    > : TreapMerger<K, TK, A, B, B>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): B? = a?.let { mergeKeepers(it, b) } ?: b
        context(ThresholdForker<Pair<A?, B?>>)
        override fun shallowMergeMismatch(a: A?, b: B?): B? = a?.let { shallowMerge(it, b) } ?: b
        override fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean =
            a.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }


    abstract class KeepB<
        @Treapable K, TK: TreapKey<K>, A : Treap<K, TK, A>, B : Treap<K, TK, B>, R : Treap<K, TK, R>
    > : TreapMerger<K, TK, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = b?.let { mergeKeepers(a, it) }
        context(ThresholdForker<Pair<A?, B?>>)
        override fun shallowMergeMismatch(a: A?, b: B?): R? = b?.let { shallowMerge(a, it) }
        override fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean =
            b.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }

    abstract class KeepAllMergeB<
        @Treapable K, TK: TreapKey<K>, A : Treap<K, TK, A>, B : Treap<K, TK, B>
    > : TreapMerger<K, TK, A, B, A>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): A? = b?.let { mergeKeepers(a, it) } ?: a
        context(ThresholdForker<Pair<A?, B?>>)
        override fun shallowMergeMismatch(a: A?, b: B?): A? = b?.let { shallowMerge(a, it) } ?: a
        override fun parallelMergeTheshold(a: A?, b: B?, parallelThresholdLog2: Int): Boolean =
            b.isApproximatelySmallerThanLog2(parallelThresholdLog2)
    }
}
