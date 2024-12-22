package com.certora.collect

internal abstract class AbstractKeySet<@Treapable K, S : TreapSet<K>> : TreapSet<K> {
    abstract val map: AbstractTreapMap<K, *, *>
    abstract val keys: Lazy<S>

    @Suppress("Treapability")
    override fun hashCode() = keys.value.hashCode()
    override fun equals(other: Any?) = keys.value.equals(other)
    override fun toString() = keys.value.toString()

    override val size get() = map.size
    override fun isEmpty() = map.isEmpty()
    override fun clear() = treapSetOf<K>()

    override operator fun contains(element: K) = map.containsKey(element)
    override operator fun iterator() = map.entrySequence().map { it.key }.iterator()

    override fun add(element: K) = keys.value.add(element)
    override fun addAll(elements: Collection<K>) = keys.value.addAll(elements)
    override fun remove(element: K) = keys.value.remove(element)
    override fun removeAll(elements: Collection<K>) = keys.value.removeAll(elements)
    override fun removeAll(predicate: (K) -> Boolean) = keys.value.removeAll(predicate)
    override fun retainAll(elements: Collection<K>) = keys.value.retainAll(elements)

    override fun single() = map.single().key
    override fun singleOrNull() = map.singleOrNull()?.key
    override fun arbitraryOrNull() = map.arbitraryOrNull()?.key

    override fun containsAny(elements: Iterable<K>) = keys.value.containsAny(elements)
    override fun containsAny(predicate: (K) -> Boolean) = (this as Iterable<K>).any(predicate)
    override fun containsAll(elements: Collection<K>) = keys.value.containsAll(elements)
    override fun findEqual(element: K) = keys.value.findEqual(element)

    override fun forEachElement(action: (K) -> Unit) = map.forEachEntry { action(it.key) }

    override fun <R : Any> mapReduce(map: (K) -> R, reduce: (R, R) -> R) =
        this.map.mapReduce({ k, _ -> map(k) }, reduce)
    override fun <R : Any> parallelMapReduce(map: (K) -> R, reduce: (R, R) -> R, parallelThresholdLog2: Int) =
        this.map.parallelMapReduce({ k, _ -> map(k) }, reduce, parallelThresholdLog2)
}
