package com.certora.collect

import com.certora.forkjoin.*

internal abstract class TreapMerger<
    @Treapable K, AV, BV, RV, A : Treap<K, AV, A>, B : Treap<K, BV, B>, R : Treap<K, RV, R>
> {
    protected abstract fun shallowMerge(a: A?, b: B?): R?

    fun merge(a: A?, b: B?): R? = notForking(a to b) { mergeTreaps(a, b) }    

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

    abstract class Union<@Treapable K, V, T : Treap<K, V, T>> : TreapMerger<K, V, V, V, T, T, T>() {
        context(ThresholdForker<Pair<T?, T?>>)
        override fun mergeMismatch(a: T?, b: T?): T? = a ?: b
    }

    abstract class Intersection<
        @Treapable K, AV, BV, RV, A : Treap<K, AV, A>, B : Treap<K, BV, B>, R : Treap<K, RV, R>
    > : TreapMerger<K, AV, BV, RV, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = null
    }

    abstract class All<
        @Treapable K, AV, BV, RV, A : Treap<K, AV, A>, B : Treap<K, BV, B>, R : Treap<K, RV, R>
    > : TreapMerger<K, AV, BV, RV, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = mergeKeepers(a, b)
    }

    abstract class AllA<
        @Treapable K, AV, BV, RV, A : Treap<K, AV, A>, B : Treap<K, BV, B>, R : Treap<K, RV, R>
    > : TreapMerger<K, AV, BV, RV, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = a?.let { mergeKeepers(it, b) }
    }

    abstract class AllB<
        @Treapable K, AV, BV, RV, A : Treap<K, AV, A>, B : Treap<K, BV, B>, R : Treap<K, RV, R>
    > : TreapMerger<K, AV, BV, RV, A, B, R>() {
        context(ThresholdForker<Pair<A?, B?>>)
        override fun mergeMismatch(a: A?, b: B?): R? = b?.let { mergeKeepers(a, it) }
    }
}
