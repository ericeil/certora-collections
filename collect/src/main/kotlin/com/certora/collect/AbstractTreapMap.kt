package com.certora.collect

import com.certora.forkjoin.*
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableSet

/**
    Base class for TreapMap implementations.  Provides the Map operations; derived classes deal with type-specific
    behavior such as hash collisions.  See [Treap] for an overview of all of this.
 */
internal sealed class AbstractTreapMap<@Treapable K, V, TK: TreapKey<K>, @Treapable S : AbstractTreapMap<K, V, TK, S>>(
    left: S?,
    right: S?
) : TreapMap<K, V>, Treap<K, V, TK, S>(left, right) {

    /**
        Derived classes override to create an apropriate node containing the given entry.
     */
    abstract fun new(key: K, value: V): S

    /**
        Converts the given Map to a AbstractTreapMap, if the Collection is already a AbstractTreapMap of the same type
        as 'this' AbstractTreapMap.  For example, if this is a HashTreapMap, and so is the supplied collection.
        Otherwise returns null.
     */
    abstract fun Map<out K, V>.toTreapMapOrNull(): S?

    /**
        Given a map, calls the supplied `action` if the collection is a Treap of the same type as this Treap, otherwise
        calls `fallback.`  Used to implement optimized operations over two compatible Treaps, with a fallback when
        needed.
     */
    private inline fun <R> Map<out K, V>.useAsTreap(action: (S) -> R, fallback: () -> R): R {
        val treapMap = this.toTreapMapOrNull()
        return if (treapMap != null) {
            action(treapMap.self)
        } else {
            fallback()
        }
    }

    /**
        Gets a sequence of map entries just in this Treap node.
     */
    abstract fun shallowEntrySequence(): Sequence<Map.Entry<K, V>>

    /**
        Converts the supplied map key to a TreapKey appropriate to this type of AbstractTreapMap (sorted vs. hashed)
     */
    abstract fun K.toTreapKey(): TreapKey<K>?

    /**
        Does this node contain an entry with the given map key?
     */
    abstract fun shallowContainsKey(key: K): Boolean

    /**
        Gets the value of the entry with the given key, *in this node only*.
     */
    abstract fun shallowGetValueOrNull(key: K): V?

    abstract fun shallowRemoveEntry(key: K, value: V): S?
    abstract fun shallowUpdate(entryKey: K, transform: (V?) -> V?): S?
    abstract fun <R : Any> shallowMapReduce(map: (K, V) -> R, reduce: (R, R) -> R): R

    private fun containsEntry(entry: Map.Entry<K, V>): Boolean {
        val key = entry.key
        val value = entry.value
        return when {
            this.get(key) != value -> false
            value != null -> true // get(key) returned a non-null value, and it matched
            else -> this.containsKey(key) // get(key) returned null; is the key in the map or not?
        }
    }

    /**
        Gets a sequence of all map entries in this Map.
     */
    fun entrySequence() = asTreapSequence().flatMap { it.shallowEntrySequence() }


    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // Map opreations start here

    override fun toString(): String = entries.joinToString(", ", "{", "}") { toString(it) }

    private fun toString(entry: Map.Entry<K, V>): String = toString(entry.key) + "=" + toString(entry.value)

    private fun toString(o: Any?): String = if (o === this) { "(this Map)" } else { o.toString() }

    @Suppress("UNCHECKED_CAST")
    override fun equals(other: Any?) : Boolean {
        val otherMap = other as? Map<K, V>
        return when {
            otherMap == null -> false
            otherMap === this -> true
            otherMap.isEmpty() -> false // NB AbstractTreapMap always contains at least one entry            
            else -> otherMap.useAsTreap(
                { otherTreap -> this.self.deepEquals(otherTreap) },
                { other.size == this.size && other.entries.all { this.containsEntry(it) }}
            )
        }
    }

    override val size: Int get() = computeSize()
    override fun isEmpty(): Boolean = false

    // NB AbstractTreapMap always contains at least one entry
    override fun single() = singleOrNull() ?: throw IllegalArgumentException("Map contains more than one entry")

    override fun containsKey(key: K) =
        key.toTreapKey()?.let { self.find(it) }?.shallowContainsKey(key) ?: false

    override fun containsValue(value: V) = values.contains(value)

    override fun get(key: K): V? =
        key.toTreapKey()?.let { self.find(it) }?.shallowGetValueOrNull(key)

    override fun putAll(m: Map<out K, V>): TreapMap<K, V> =
        m.entries.fold(this as TreapMap<K, V>) { t, e -> t.put(e.key, e.value) }

    override fun remove(key: K): TreapMap<K, V> =
        key.toTreapKey()?.let { self.remove(it, key) ?: clear() } ?: this

    override fun remove(key: K, value: V): TreapMap<K, V> =
        key.toTreapKey()?.let { self.removeEntry(it, key, value) ?: clear() } ?: this

    override fun clear(): TreapMap<K, V> = treapMapOf<K, V>()

    override fun builder(): TreapMapBuilder<K, V> = TreapMapBuilder(self)

    override val entries: ImmutableSet<Map.Entry<K, V>>
        get() = object : AbstractSet<Map.Entry<K, V>>(), ImmutableSet<Map.Entry<K, V>> {
            override val size get() = this@AbstractTreapMap.size
            override fun isEmpty() = this@AbstractTreapMap.isEmpty()
            override fun iterator() = entrySequence().iterator()
        }

    override val values: ImmutableCollection<V>
        get() = object: AbstractCollection<V>(), ImmutableCollection<V> {
            override val size get() = this@AbstractTreapMap.size
            override fun isEmpty() = this@AbstractTreapMap.isEmpty()
            override operator fun iterator() = entrySequence().map { it.value }.iterator()
        }

    /**
        Applies a transform to each entry, producing new values.
     */
    @Suppress("UNCHECKED_CAST", "Treapability")
    override fun <R : Any> updateValues(transform: (K, V) -> R?): TreapMap<K, R> =
        (this as AbstractTreapMap<Any?, Any?, *, *>).updateValuesErasedTypes(
            transform as (Any?, Any?) -> Any?
        ) as TreapMap<K, R>

    private fun updateValuesErasedTypes(transform: (K, V) -> V?): TreapMap<K, V> = when {
        isEmpty() -> self
        else -> notForking(this) {
            updateValuesImpl(transform) ?: clear()
        }
    }

    /**
        Applies a transform to each entry, producing new values, processing multiple entries in parallel.

        @param[parallelThresholdLog2] The minimum number of entries to process in parallel, expressed as a power of 2.
        If a subtree is estimated to have fewer entries than this, it will be processed sequentially.

        @param[transform] The transform to apply to each entry.  Must be pure and thread-safe.
     */
    @Suppress("UNCHECKED_CAST", "Treapability")
    override fun <R : Any> parallelUpdateValues(parallelThresholdLog2: Int, transform: (K, V) -> R?): TreapMap<K, R> =
        (this as AbstractTreapMap<Any?, Any?, *, *>).parallelUpdateValuesErasedTypes(
            parallelThresholdLog2,
            transform as (Any?, Any?) -> Any?
        ) as TreapMap<K, R>

    private fun parallelUpdateValuesErasedTypes(parallelThresholdLog2: Int, transform: (K, V) -> V?): TreapMap<K, V> = when {
        isEmpty() -> self
        else -> maybeForking(self, threshold = { it.isApproximatelySmallerThanLog2(parallelThresholdLog2) }) {
            updateValuesImpl(transform) ?: clear()
        }
    }

    context(ThresholdForker<S>)
    private fun updateValuesImpl(transform: (K, V) -> V?): S? {
        val (newLeft, newRight, newThis) = fork(
            self,
            { left?.updateValuesImpl(transform) },
            { right?.updateValuesImpl(transform) },
            { shallowUpdateValues(transform) }
        )
        return newThis?.with(newLeft, newRight) ?: (newLeft join newRight)
    }

    abstract fun shallowUpdateValues(transform: (K, V) -> V?): S?


    /**
        Insert, update, or remove an entry at key [key] based on the auxiliary data [value] passed in and the supplied
        [merger] function. The [merger] function takes the current value associated with [key] (if it exists) and the
        value being updated and produces the new value for [key], or null if the binding should be removed.

        More specifically, if [key] exists in the map, then the current value associate with [key] is passed into merger
        as the first argument, along with [value] as the second argument. If the merger function then returns null, the
        key is removed from the mapping, otherwise the value associated with [key] is updated to the value returned by
        merger.

        If [key] does not exist in the map, then [merger] is called with null as the first argument and [value] as the
        second argument. If [merger] then returns null, the map is unchanged, otherwise, [key] is inserted into the map.

        In pseudo-code, this function is equivalent to the following:
        ```
        if(key in this) {
          val merged = merger(this.get(key)!!, value)
          if(merged == null) {
             this.removeKey(key)
          } else {
             this.put(key, merged)
          }
       } else {
          val gen = merger(null, value)
          if(gen == null) {
             this
          } else {
             this.put(key, gen)
          }
       }
       ```
     */
    override fun updateValue(key: K, transform: (V?) -> V?): TreapMap<K, V> {
        val treapKey = key.toTreapKey()?.precompute()
        return if (treapKey == null) {
            // The key is not compatible with this map type, so it's definitely not in the map.
            transform(null)?.let { put(key, it) } ?: this
        } else {
            self.updateValue(treapKey, key, transform, ::new) ?: clear()
        }
    }

    /**
        Produces a sequence from the entries of this map and another map.  For each key, the result is an entry mapping
        the key to a pair of (possibly null) values.
     */
    override fun zip(m: Map<out K, V>) = sequence<Map.Entry<K, Pair<V?, V?>>> {
        fun <T> Iterator<T>.nextOrNull() = if (hasNext()) { next() } else { null }

        val sequences = getTreapSequencesIfSameType(m)
        if (sequences != null) {
            // Fast case for when the maps are the same type
            val thisIt = sequences.first.iterator()
            val thatIt = sequences.second.iterator()

            var thisCurrent = thisIt.nextOrNull()
            var thatCurrent = thatIt.nextOrNull()

            while (thisCurrent != null && thatCurrent != null) {
                val c = thisCurrent.compareKeyTo(thatCurrent)
                when {
                    c < 0 -> {
                        yieldAll(thisCurrent.shallowZipThisOnly())
                        thisCurrent = thisIt.nextOrNull()
                    }
                    c > 0 -> {
                        yieldAll(thatCurrent.shallowZipThatOnly())
                        thatCurrent = thatIt.nextOrNull()
                    }
                    else -> {
                        yieldAll(thisCurrent.shallowZip(thatCurrent))
                        thisCurrent = thisIt.nextOrNull()
                        thatCurrent = thatIt.nextOrNull()
                    }
                }
            }
            while (thisCurrent != null) {
                yieldAll(thisCurrent.shallowZipThisOnly())
                thisCurrent = thisIt.nextOrNull()
            }
            while (thatCurrent != null) {
                yieldAll(thatCurrent.shallowZipThatOnly())
                thatCurrent = thatIt.nextOrNull()
            }
        } else {
            // Slower fallback for maps of different types
            for ((k, v) in entries) {
                yield(MapEntry(k, v to m[k]))
            }
            for ((k, v) in m.entries) {
                if (k !in this@AbstractTreapMap) {
                    yield(MapEntry(k, null to v))
                }
            }
        }
    }

    private fun shallowZipThisOnly() = shallowEntrySequence().map { MapEntry(it.key, it.value to null) }
    private fun shallowZipThatOnly() = shallowEntrySequence().map { MapEntry(it.key, null to it.value) }
    protected abstract fun shallowZip(that: S): Sequence<Map.Entry<K, Pair<V?, V?>>>
    protected abstract fun getTreapSequencesIfSameType(that: Map<out K, V>): Pair<Sequence<S>, Sequence<S>>?


    override fun <R : Any> mapReduce(map: (K, V) -> R, reduce: (R, R) -> R): R =
        notForking(self) { mapReduceImpl(map, reduce) }

    override fun <R : Any> parallelMapReduce(map: (K, V) -> R, reduce: (R, R) -> R, parallelThresholdLog2: Int): R =
        maybeForking(self, threshold = { it.isApproximatelySmallerThanLog2(parallelThresholdLog2) }) {
            mapReduceImpl(map, reduce)
        }

    context(ThresholdForker<S>)
    private fun <R : Any> mapReduceImpl(map: (K, V) -> R, reduce: (R, R) -> R): R {
        val (left, middle, right) = fork(
            self,
            { left?.mapReduceImpl(map, reduce) },
            { shallowMapReduce(map, reduce) },
            { right?.mapReduceImpl(map, reduce) }
        )
        val leftAndMiddle = left?.let { reduce(it, middle) } ?: middle
        return right?.let { reduce(leftAndMiddle, it) } ?: leftAndMiddle
    }
}

/**
    Removes a map entry (`entryKey`, `entryValue`) with key `key`.
 */
internal fun <@Treapable K, V, TK: TreapKey<K>, @Treapable S : AbstractTreapMap<K, V, TK, S>> S?.removeEntry(
    key: TreapKey<K>,
    entryKey: K,
    entryValue: V
): S? = when {
    this == null -> null
    key.comparePriorityTo(this) > 0 -> this
    else -> {
        val c = key.compareKeyTo(this)
        when {
            c < 0 -> this.with(left = left.removeEntry(key, entryKey, entryValue))
            c > 0 -> this.with(right = right.removeEntry(key, entryKey, entryValue))
            else -> this.shallowRemoveEntry(entryKey, entryValue) ?: (this.left join this.right)
        }
    }
}

internal fun <@Treapable K, V, TK: TreapKey<K>, @Treapable S : AbstractTreapMap<K, V, TK, S>> S?.updateValue(
    thatKey: TreapKey<K>,
    entryKey: K,
    transform: (V?) -> V?,
    new: (K, V) -> S
): S? = when {
    this == null -> {
        val generated = transform(null)
        if(generated == null) {
            null
        } else {
            new(entryKey, generated)
        }
    }
    else -> {
        val c = thatKey.comparePriorityTo(this)
        when {
            c == 0 -> this.shallowUpdate(entryKey, transform) ?: (left join right)
            c > 0 -> {
                val merged = transform(null)
                if(merged == null) {
                    self
                } else {
                    val newRoot = new(entryKey, merged)
                    this.split(thatKey).let { split ->
                        newRoot.with(left = split.left, right = split.right)
                    }
                }
            }
            thatKey.compareKeyTo(this) < 0 -> this.with(left = this.left.updateValue(thatKey, entryKey, transform, new))
            else -> this.with(right = this.right.updateValue(thatKey, entryKey, transform, new))
        }
    }
}

context(ThresholdForker<T>)
internal fun <@Treapable K, V, U, TK: TreapKey<K>, @Treapable S : AbstractTreapMap<K, V, TK, S>, @Treapable T : AbstractTreapMap<K, U, TK, T>>
S?.updateValuesImpl(
    m: T?,
    updater: (S?, T) -> S?
): S? {
    val (newLeft, newRight, newThis) = when {
        m == null -> return this
        this == null -> {
            fork(
                m,
                { null.updateValuesImpl(m.left, updater) },
                { null.updateValuesImpl(m.right, updater) },
                { updater(null, m) }
            )
        }
        this.comparePriorityTo(m) >= 0 -> {
            val mSplit = m.split(this)
            fork(
                m,
                { this.left.updateValuesImpl(mSplit.left, updater) },
                { this.right.updateValuesImpl(mSplit.right, updater) },
                { mSplit.duplicate.let { if (it != null) { updater(this, it) } else { this }}}
            )
        }
        else -> {
            val thisSplit = this.split(m)
            fork(
                m,
                { m.left.let { if (it == null) { thisSplit.left } else { thisSplit.left.updateValuesImpl(it, updater) }}},
                { m.right.let { if (it == null) { thisSplit.right } else { thisSplit.right.updateValuesImpl(it, updater) }}},
                { updater(thisSplit.duplicate, m) }
            )
        }
    }
    return newThis?.with(newLeft, newRight) ?: (newLeft join newRight)
}

internal fun <@Treapable K, V, U> TreapMap<K, V>.fallbackUpdateValues(
    m: Map<K, U>,
    transform: (K, V?, U) -> V?
): TreapMap<K, V> {
    var newThis = this
    for ((k, u) in m.entries) {
        newThis = newThis.updateValue(k) { v ->
            transform(k, v, u)
        }
    }
    return newThis
}

internal fun <@Treapable K, V> TreapMap<K, V>.fallbackUnion(m: Map<K, V>, merger: (K, V, V) -> V): TreapMap<K, V> {
    var r = this
    for ((k, v) in m.entries) {
        if (k in this) {
            r += k to merger(k, this[k]!!, v)
        } else {
            r += k to v
        }
    }
    return r
}

internal fun <@Treapable K, V, U, R> TreapMap<K, V>.fallbackIntersect(m: Map<K, U>, merger: (K, V, U) -> R): TreapMap<K, R> {
    var r = treapMapOf<K, R>()
    for ((k, v) in m.entries) {
        if (k in this) {
            r += k to merger(k, this[k]!!, v)
        }
    }
    return r
}

internal fun <@Treapable K, V, U, R> TreapMap<K, V>.fallbackMerge(m: Map<K, U>, merger: (K, V?, U?) -> R?): TreapMap<K, R> {
    var r = treapMapOf<K, R>()
    for (k in this.keys union m.keys) {
        merger(k, this[k], m[k])?.let { r += k to it }
    }
    return r
}

internal fun <@Treapable K, V, U, R> TreapMap<K, V>.fallbackMergeIntersection(m: Map<K, U>, merger: (K, V, U) -> R?): TreapMap<K, R> {
    var r = treapMapOf<K, R>()
    for (k in this.keys intersect m.keys) {
        merger(k, this[k]!!, m[k]!!)?.let { r += k to it }
    }
    return r
}


