package com.certora.collect

import kotlinx.collections.immutable.*

internal class EmptyTreapMap<@Treapable K, V> private constructor() : TreapMap<K, V>, java.io.Serializable {
    override val size get() = 0
    override fun isEmpty() = true

    override fun hashCode() = 0
    override fun equals(other: Any?) = (other is Map<*, *>) && (other.isEmpty())
    override fun toString() = "{}"

    override fun clear(): TreapMap<K, V> = this

    override fun containsKey(key: K): Boolean = false
    override fun containsValue(value: V): Boolean = false
    override fun get(key: K): V? = null

    override fun remove(key: K): TreapMap<K, V> = this
    override fun remove(key: K, value: V): TreapMap<K, V> = this

    override fun single(): Map.Entry<K, V> = throw NoSuchElementException("Empty map.")
    override fun singleOrNull(): Map.Entry<K, V>? = null
    override fun arbitraryOrNull(): Map.Entry<K, V>? = null

    override fun forEachEntry(action: (Map.Entry<K, V>) -> Unit): Unit {}

    override fun <R : Any> updateValues(
        transform: (K, V) -> R?
    ): TreapMap<K, R> = treapMapOf()

    override fun <R : Any> parallelUpdateValues(
        parallelThresholdLog2: Int,
        transform: (K, V) -> R?
    ): TreapMap<K, R> = treapMapOf()

    override fun <U> updateValues(
        m: Map<K, U>,
        transform: (K, V?, U) -> V?
    ): TreapMap<K, V> = fallbackUpdateValues(m, transform)

    override fun <U> parallelUpdateValues(
        m: Map<K, U>,
        parallelThresholdLog2: Int,
        transform: (K, V?, U) -> V?
    ): TreapMap<K, V> = fallbackUpdateValues(m, transform)

    override fun <R : Any> mapReduce(map: (K, V) -> R, reduce: (R, R) -> R): R? = null
    override fun <R : Any> parallelMapReduce(map: (K, V) -> R, reduce: (R, R) -> R, parallelThresholdLog2: Int): R? = null

    override fun updateValue(key: K, transform: (V?) -> V?): TreapMap<K, V> =
        when (val v = transform(null)) {
            null -> this
            else -> put(key, v)
        }

    override fun union(m: Map<K, V>, merger: (K, V, V) -> V): TreapMap<K, V> = putAll(m)
    override fun parallelUnion(m: Map<K, V>, parallelThresholdLog2: Int, merger: (K, V, V) -> V): TreapMap<K, V> = putAll(m)

    override fun <U, R> intersect(m: Map<K, U>, merger: (K, V, U) -> R): TreapMap<K, R> = treapMapOf()
    override fun <U, R> parallelIntersect(m: Map<K, U>, parallelThresholdLog2: Int, merger: (K, V, U) -> R): TreapMap<K, R> = treapMapOf()

    override fun <U, R> merge(m: Map<K, U>, merger: (K, V?, U?) -> R?): TreapMap<K, R> = fallbackMerge(m, merger)
    override fun <U, R> parallelMerge(m: Map<K, U>, parallelThresholdLog2: Int, merger: (K, V?, U?) -> R?): TreapMap<K, R> = fallbackMerge(m, merger)
    override fun <U, R> mergeIntersection(m: Map<K, U>, merger: (K, V, U) -> R?): TreapMap<K, R> = fallbackMergeIntersection(m, merger)
    override fun <U, R> parallelMergeIntersection(m: Map<K, U>, parallelThresholdLog2: Int, merger: (K, V, U) -> R?): TreapMap<K, R> = fallbackMergeIntersection(m, merger)

    override fun zip(m: Map<out K, V>): Sequence<Map.Entry<K, Pair<V?, V?>>> =
        m.asSequence().map { MapEntry(it.key, null to it.value) }

    override val entries: ImmutableSet<Map.Entry<K, V>> get() = persistentSetOf<Map.Entry<K, V>>()
    override val keys: TreapSet<K> get() = treapSetOf<K>()
    override val values: ImmutableCollection<V> get() = persistentSetOf<V>()

    @Suppress("Treapability", "UNCHECKED_CAST")
    override fun put(key: K, value: V): TreapMap<K, V> = when (key) {
        !is Comparable<*>?, is PrefersHashTreap -> HashTreapMap(key, value)
        else -> SortedTreapMap(key, value)
    }

    @Suppress("UNCHECKED_CAST")
    override fun putAll(m: Map<out K, V>): TreapMap<K, V> = when {
        m.isEmpty() -> this
        m is TreapMap<*, *> -> m as TreapMap<K, V>
        m is PersistentMap.Builder<*, *> -> m.build() as TreapMap<K, V>
        else -> m.entries.fold(this as TreapMap<K, V>) { map, (key, value) -> map.put(key, value) }
    }

    companion object {
        private val instance = EmptyTreapMap<Nothing, Nothing>()
        @Suppress("UNCHECKED_CAST")
        operator fun <@Treapable K, V> invoke(): EmptyTreapMap<K, V> = instance as EmptyTreapMap<K, V>
    }
}
