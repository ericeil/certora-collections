package com.certora.collect

import com.certora.forkjoin.*
import kotlinx.collections.immutable.PersistentMap

/**
    A TreapMap specific to Comparable keys.  Iterates in the order defined by the objects.  We store one element per
    Treap node, with the map key itself as the Treap key, and an additional `value` field
 */
internal class SortedTreapMap<@Treapable K, V>(
    val key: K,
    val value: V,
    left: SortedTreapMap<K, V>? = null,
    right: SortedTreapMap<K, V>? = null
) : AbstractTreapMap<K, V, TreapKey.Sorted<K>, SortedTreapMap<K, V>>(left, right), TreapKey.Sorted<K> {

    init { check(key is Comparable<*>?) { "SortedTreapMap keys must be Comparable" } }

    override fun hashCode(): Int {
        var h = AbstractMapEntry.hashCode(key, value)
        left?.let { h += it.hashCode() }
        right?.let { h += it.hashCode() }
        return h
    }

    override val treapPriority = super<TreapKey.Sorted>.treapPriority

    override fun K.toTreapKey() = TreapKey.Sorted.fromKey(this)

    override fun new(key: K, value: V): SortedTreapMap<K, V> = SortedTreapMap(key, value)

    override fun put(key: K, value: V): TreapMap<K, V> = when (key) {
        !is Comparable<*>?, is PrefersHashTreap -> HashTreapMap(key, value) + this
        else -> self.add(new(key, value))
    }

    @Suppress("UNCHECKED_CAST")
    override fun Map<out K, V>.toTreapMapOrNull() =
        this as? SortedTreapMap<K, V>
        ?: (this as? PersistentMap.Builder<K, V>)?.build() as? SortedTreapMap<K, V>

    override fun singleOrNull(): Map.Entry<K, V>? = MapEntry(key, value).takeIf { left == null && right == null }
    override fun arbitraryOrNull(): Map.Entry<K, V>? = MapEntry(key, value)

    protected override fun getTreapSequencesIfSameType(
        that: Map<out K, V>
    ): Pair<Sequence<SortedTreapMap<K, V>>, Sequence<SortedTreapMap<K, V>>>? {
        @Suppress("UNCHECKED_CAST")
        return (that as? SortedTreapMap<K, V>)?.let {
            this.asTreapSequence() to it.asTreapSequence()
        }
    }

    override fun shallowZip(that: SortedTreapMap<K, V>): Sequence<Map.Entry<K, Pair<V, V>>> =
        sequenceOf(MapEntry(this.key, this.value to that.value))

    override val self get() = this
    override val treapKey get() = key

    fun asEntry(): Map.Entry<K, V> = MapEntry(key, value)

    override fun shallowEntrySequence(): Sequence<Map.Entry<K, V>> = sequenceOf(MapEntry(key, value))

    override fun shallowContainsKey(key: K) = true
    override val shallowSize get() = 1
    override fun shallowRemove(element: K): SortedTreapMap<K, V>? = null
    override fun shallowRemoveEntry(key: K, value: V): SortedTreapMap<K, V>? = this.takeIf { this.value != value }
    override fun shallowGetValueOrNull(key: K): V = value
    override fun shallowEquals(that: SortedTreapMap<K, V>): Boolean = this.value == that.value

    override fun copyWith(left: SortedTreapMap<K, V>?, right: SortedTreapMap<K, V>?) = SortedTreapMap(key, value, left, right)

    override fun shallowAdd(that: SortedTreapMap<K, V>): SortedTreapMap<K, V> {
        return if (this.value == that.value) {
            this
        } else {
            SortedTreapMap(treapKey, that.value, left, right)
        }
    }

    override fun shallowUpdateValues(transform: (K, V) -> V?): SortedTreapMap<K, V>? {
        val newValue = transform(key, value)
        return when {
            newValue == null -> null
            newValue === value -> this
            else -> SortedTreapMap(key, newValue, left, right)
        }
    }

    override fun shallowUpdate(entryKey: K, transform: (V?) -> V?): SortedTreapMap<K, V>? {
        val newValue = transform(value)
        return when {
            newValue == null -> null
            newValue === value -> this
            else -> SortedTreapMap(key, newValue, left, right)
        }
    }

    fun floorEntry(key: K): Map.Entry<K, V>? {
        val cmp = TreapKey.Sorted.fromKey(key)?.compareKeyTo(this)
        return when {
            cmp == null -> null
            cmp < 0 -> left?.floorEntry(key)
            cmp > 0 -> right?.floorEntry(key) ?: this.asEntry()
            else -> this.asEntry()
        }
    }

    fun ceilingEntry(key: K): Map.Entry<K, V>? {
        val cmp = TreapKey.Sorted.fromKey(key)?.compareKeyTo(this)
        return when {
            cmp == null -> null
            cmp < 0 -> left?.ceilingEntry(key) ?: this.asEntry()
            cmp > 0 -> right?.ceilingEntry(key)
            else -> this.asEntry()
        }
    }

    fun lowerEntry(key: K): Map.Entry<K, V>? {
        val cmp = TreapKey.Sorted.fromKey(key)?.compareKeyTo(this)
        return when {
            cmp == null -> null
            cmp > 0 -> right?.lowerEntry(key) ?: this.asEntry()
            else -> left?.lowerEntry(key)
        }
    }

    fun higherEntry(key: K): Map.Entry<K, V>? {
        val cmp = TreapKey.Sorted.fromKey(key)?.compareKeyTo(this)
        return when {
            cmp == null -> null
            cmp < 0 -> left?.higherEntry(key) ?: this.asEntry()
            else -> right?.higherEntry(key)
        }
    }

    fun firstEntry(): Map.Entry<K, V>? = left?.firstEntry() ?: this.asEntry()
    fun lastEntry(): Map.Entry<K, V>? = right?.lastEntry() ?: this.asEntry()

    override fun <R : Any> shallowMapReduce(map: (K, V) -> R, reduce: (R, R) -> R): R = map(key, value)

    override fun forEachEntry(action: (Map.Entry<K, V>) -> Unit) {
        left?.forEachEntry(action)
        action(this.asEntry())
        right?.forEachEntry(action)
    }

    private fun treapSetFromKeys(): SortedTreapSet<K> =
        SortedTreapSet(treapKey, left?.treapSetFromKeys(), right?.treapSetFromKeys())

    inner class KeySet : AbstractKeySet<K, TreapKey.Sorted<K>, SortedTreapSet<K>>() {
        override val map get() = this@SortedTreapMap
        override val keys = lazy { treapSetFromKeys() }
        override fun hashCode() = super.hashCode() // avoids treapability warning
    }

    override val keys get() = KeySet()

    @Suppress("UNCHECKED_CAST")
    private fun <R> R.toNode(a: SortedTreapMap<K, *>?, b: SortedTreapMap<K, *>?): SortedTreapMap<K, R> = when {
        a != null && this === a.value -> a as SortedTreapMap<K, R>
        b != null && this === b.value -> b as SortedTreapMap<K, R>
        else -> SortedTreapMap(a?.key ?: b!!.key, this, null, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> R.toNode(a: SortedTreapMap<K, *>?, b: SortedTreapSet<K>?): SortedTreapMap<K, R> = when {
        a != null && this === a.value -> a as SortedTreapMap<K, R>
        else -> SortedTreapMap(a?.key ?: b!!.treapKey, this, null, null)
    }

    private fun unionMerger(merger: (K, V, V) -> V) = 
        object : TreapMerger.KeepAllMergeIntersection<K, TreapKey.Sorted<K>, SortedTreapMap<K, V>>() {
            override fun shallowMerge(a: SortedTreapMap<K, V>?, b: SortedTreapMap<K, V>?): SortedTreapMap<K, V>? {
                a!!; b!!
                val k = a.key
                return merger(k, a.value, b.value).toNode(a, b)
            }
        }

    override fun union(m: Map<K, V>, merger: (K, V, V) -> V): TreapMap<K, V> = when (m) {
        is SortedTreapMap<K, V> -> unionMerger(merger).merge(this, m).orEmpty()
        else -> fallbackUnion(m, merger)
    }
    override fun parallelUnion(m: Map<K, V>, parallelThresholdLog2: Int, merger: (K, V, V) -> V): TreapMap<K, V> = when (m) {
        is SortedTreapMap<K, V> -> unionMerger(merger).parallelMerge(this, m, parallelThresholdLog2).orEmpty()
        else -> fallbackUnion(m, merger)
    }

    private fun <U, R> intersectMerger(merger: (K, V, U) -> R) =
        object : TreapMerger.KeepIntersection<K, TreapKey.Sorted<K>, SortedTreapMap<K, V>, SortedTreapMap<K, U>, SortedTreapMap<K, R>>() {
            override fun shallowMerge(a: SortedTreapMap<K, V>?, b: SortedTreapMap<K, U>?): SortedTreapMap<K, R>? {
                a!!; b!!
                val k = a.key
                return merger(k, a.value, b.value).toNode(a, b)
            }
        }

    override fun <U, R> intersect(m: Map<K, U>, merger: (K, V, U) -> R): TreapMap<K, R> = when (m) {
        is SortedTreapMap<K, U> -> intersectMerger(merger).merge(this, m).orEmpty()
        else -> fallbackIntersect(m, merger)
    }
    override fun <U, R> parallelIntersect(m: Map<K, U>, parallelThresholdLog2: Int, merger: (K, V, U) -> R): TreapMap<K, R> = when (m) {
        is SortedTreapMap<K, U> -> intersectMerger(merger).parallelMerge(this, m, parallelThresholdLog2).orEmpty()
        else -> fallbackIntersect(m, merger)
    }


    private fun <U> updateValuesMerger(transform: (K, V?, U) -> V?) =
        object : TreapMerger.KeepAllMergeB<K, TreapKey.Sorted<K>, SortedTreapMap<K, V>, SortedTreapMap<K, U>>() {
            override fun shallowMerge(a: SortedTreapMap<K, V>?, b: SortedTreapMap<K, U>?): SortedTreapMap<K, V>? {
                val k = b!!.key
                return transform(k, a?.value, b.value)?.toNode(a, b)
            }
        }

    override fun <U> updateValues(
        m: Map<K, U>,
        transform: (K, V?, U) -> V?
    ): TreapMap<K, V> = when (m) {
        is SortedTreapMap<K, U> -> updateValuesMerger(transform).merge(this, m).orEmpty()
        else -> fallbackUpdateValues(m, transform)
    }
    override fun <U> parallelUpdateValues(
        m: Map<K, U>,
        parallelThresholdLog2: Int,
        transform: (K, V?, U) -> V?
    ): TreapMap<K, V> = when (m) {
        is SortedTreapMap<K, U> -> updateValuesMerger(transform).parallelMerge(this, m, parallelThresholdLog2).orEmpty()
        else -> fallbackUpdateValues(m, transform)
    }

    private fun <U, R> mergeMerger(merger: (K, V?, U?) -> R?) =
        object : TreapMerger.MergeAll<K, TreapKey.Sorted<K>, SortedTreapMap<K, V>, SortedTreapMap<K, U>, SortedTreapMap<K, R>>() {
            override fun shallowMerge(a: SortedTreapMap<K, V>?, b: SortedTreapMap<K, U>?): SortedTreapMap<K, R>? {                
                val k = a?.key ?: b!!.key
                return merger(k, a?.value, b?.value)?.toNode(a, b)
            }
        }

    override fun <U, R> merge(m: Map<K, U>, merger: (K, V?, U?) -> R?): TreapMap<K, R> = when (m) {
        is SortedTreapMap<K, U> -> mergeMerger<U, R>(merger).merge(this, m).orEmpty()
        else -> fallbackMerge(m, merger)
    }
    override fun <U, R> parallelMerge(m: Map<K, U>, parallelThresholdLog2: Int, merger: (K, V?, U?) -> R?): TreapMap<K, R> = when (m) {
        is SortedTreapMap<K, U> -> mergeMerger<U, R>(merger).parallelMerge(this, m, parallelThresholdLog2).orEmpty()
        else -> fallbackMerge(m, merger)
    }

    private fun <U, R> mergeIntersectionMerger(merger: (K, V, U) -> R?) =
        object : TreapMerger.KeepIntersection<K, TreapKey.Sorted<K>, SortedTreapMap<K, V>, SortedTreapMap<K, U>, SortedTreapMap<K, R>>() {
            override fun shallowMerge(a: SortedTreapMap<K, V>?, b: SortedTreapMap<K, U>?): SortedTreapMap<K, R>? {
                a!!; b!!
                val k = a.key 
                return merger(k, a.value, b.value)?.toNode(a, b)
            }
        }

    override fun <U, R> mergeIntersection(m: Map<K, U>, merger: (K, V, U) -> R?): TreapMap<K, R> = when (m) {
        is SortedTreapMap<K, U> -> mergeIntersectionMerger(merger).merge(this, m).orEmpty()
        else -> fallbackMergeIntersection(m, merger)
    }
    override fun <U, R> parallelMergeIntersection(m: Map<K, U>, parallelThresholdLog2: Int, merger: (K, V, U) -> R?): TreapMap<K, R> = when (m) {
        is SortedTreapMap<K, U> -> mergeIntersectionMerger(merger).parallelMerge(this, m, parallelThresholdLog2).orEmpty()
        else -> fallbackMergeIntersection(m, merger)
    }

    private fun <R> lookupMerger(transform: (K, V?) -> R) =
        object : TreapMerger.KeepB<K, TreapKey.Sorted<K>, SortedTreapMap<K, V>, SortedTreapSet<K>, SortedTreapMap<K, R>>() {
            override fun shallowMerge(a: SortedTreapMap<K, V>?, b: SortedTreapSet<K>?): SortedTreapMap<K, R>? {
                val k = b!!.treapKey
                return transform(k, a?.value).toNode(a, b)
            }
        }

    override fun <R> lookup(keys: Set<K>, transform: (K, V?) -> R): TreapMap<K, R> = when (keys) {
        is SortedTreapSet<K> -> lookupMerger(transform).merge(this, keys).orEmpty()
        else -> fallbackLookup(keys, transform)
    }
    override fun <R> parallelLookup(keys: Set<K>, parallelThresholdLog2: Int, transform: (K, V?) -> R): TreapMap<K, R> = when (keys) {
        is SortedTreapSet<K> -> lookupMerger(transform).parallelMerge(this, keys, parallelThresholdLog2).orEmpty()
        else -> fallbackLookup(keys, transform)
    }
}
