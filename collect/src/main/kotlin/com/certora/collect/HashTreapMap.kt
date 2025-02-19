package com.certora.collect

import com.certora.forkjoin.*
import kotlinx.collections.immutable.PersistentMap

/**
    A TreapMap for map keys that do not have a total ordering defined by implementing Comparable.  For those, we use the
    map keys' hash codes as Treap keys, and deal with collisions by chaining multiple map entries from a single Treap
    node.

    The HashTreapMap instance itself stores the first entry, and additional entries are chained via KeyValuePairList.
    This is just a simple linked list, so operations on it are either O(N) or O(N^2), but collisions are assumed to be
    rare enough that these lists will be very small - usually just one element.
 */
internal class HashTreapMap<@Treapable K, V>(
    override val key: K,
    override val value: V,
    override val next: KeyValuePairList.More<K, V>? = null,
    left: HashTreapMap<K, V>? = null,
    right: HashTreapMap<K, V>? = null
) : AbstractTreapMap<K, V, TreapKey.Hashed<K>, HashTreapMap<K, V>>(left, right), TreapKey.Hashed<K>, KeyValuePairList<K, V> {

    override fun hashCode(): Int {
        var h = 0
        forEachPair { (k, v) -> h += AbstractMapEntry.hashCode(k, v) }
        left?.let { h += it.hashCode() }
        right?.let { h += it.hashCode() }
        return h
    }

    override val treapPriority = super<TreapKey.Hashed>.treapPriority 

    override fun K.toTreapKey() = TreapKey.Hashed.fromKey(this)
    override fun new(key: K, value: V): HashTreapMap<K, V> = HashTreapMap(key, value)

    override fun put(key: K, value: V): TreapMap<K, V> = self.add(new(key, value))

    @Suppress("UNCHECKED_CAST")
    override fun Map<out K, V>.toTreapMapOrNull() =
        this as? HashTreapMap<K, V>
        ?: (this as? PersistentMap.Builder<K, V>)?.build() as? HashTreapMap<K, V>

    override fun singleOrNull() = MapEntry(key, value).takeIf { next == null && left == null && right == null }
    override fun arbitraryOrNull(): Map.Entry<K, V>? = MapEntry(key, value)

    private inline fun <U> KeyValuePairList<K, U>?.forEachPair(action: (KeyValuePairList<K, U>) -> Unit) {
        var current = this
        while (current != null) {
            action(current)
            current = current.next
        }
    }

    private fun KeyValuePairList<K, V>?.shallowContainsPair(key: K, value: V): Boolean {
        forEachPair {
            if (it.key == key && it.value == value) {
                return true
            }
        }
        return false
    }

    private fun KeyValuePairList<K, V>?.shallowContainsKey(key: K) : Boolean {
        forEachPair {
            if (it.key == key) {
                return true
            }
        }
        return false
    }

    protected override fun getTreapSequencesIfSameType(
        that: Map<out K, V>
    ): Pair<Sequence<HashTreapMap<K, V>>, Sequence<HashTreapMap<K, V>>>? {
        @Suppress("UNCHECKED_CAST")
        return (that as? HashTreapMap<K, V>)?.let {
            this.asTreapSequence() to it.asTreapSequence()
        }
    }

    override fun shallowZip(that: HashTreapMap<K, V>): Sequence<Map.Entry<K, Pair<V?, V?>>> = sequence {
        forEachPair {
            yield(MapEntry(it.key, it.value to that.shallowGetValueOrNull(it.key)))
        }
        that.forEachPair {
            if (!containsKey(it.key)) {
                yield(MapEntry(it.key, null to it.value))
            }
        }
    }

    override val self get() = this
    override val treapKey get() = key

    override fun copyWith(left: HashTreapMap<K, V>?, right: HashTreapMap<K, V>?): HashTreapMap<K, V> = HashTreapMap(key, value, next, left, right)

    override fun shallowEntrySequence(): Sequence<Map.Entry<K, V>> = sequence {
        forEachPair { (k, v) ->
            yield (MapEntry(k, v))
        }
    }

    override fun shallowContainsKey(key: K): Boolean = (this as KeyValuePairList<K, V>).shallowContainsKey(key)

    override fun shallowGetValueOrNull(key: K): V? {
        forEachPair {
            if (it.key == key) {
                return it.value
            }
        }
        return null
    }

    fun shallowGetValue(key: K): V {
        forEachPair {
            if (it.key == key) {
                return it.value
            }
        }
        error("Key $key not found")
    }

    override fun shallowAdd(that: HashTreapMap<K, V>): HashTreapMap<K, V> {
        check(that.next == null) { "Add with mulple map entries?" }
        return when {
            !shallowContainsKey(that.key) -> {
                HashTreapMap(this.key, this.value, KeyValuePairList.More(that.key, that.value, this.next), this.left, this.right)
            }
            this.shallowGetValue(that.key) == that.value -> {
                this
            }
            else -> {
                var newPairs: KeyValuePairList.More<K, V>? = null
                this.forEachPair {
                    if (it.key == that.key) {
                        newPairs = KeyValuePairList.More(it.key, that.value, newPairs)
                    } else {
                        newPairs = KeyValuePairList.More(it.key, it.value, newPairs)
                    }
                }
                val firstPair = newPairs!!
                HashTreapMap(firstPair.key, firstPair.value, firstPair.next, this.left, this.right)
            }
        }
    }

    override fun shallowRemoveEntry(key: K, value: V): HashTreapMap<K, V>? {
        return when {
            !this.shallowContainsPair(key, value) -> this
            else -> {
                var newPairs: KeyValuePairList.More<K, V>? = null
                this.forEachPair {
                    if (it.key != key || it.value != value) {
                        newPairs = KeyValuePairList.More(it.key, it.value, newPairs)
                    }
                }
                val firstPair = newPairs
                if (firstPair == null) {
                    null
                } else {
                    HashTreapMap(firstPair.key, firstPair.value, firstPair.next, this.left, this.right)
                }
            }
        }
    }

    override fun shallowUpdate(entryKey: K, transform: (V?) -> V?): HashTreapMap<K, V>? {
        return when (this.key) {
            entryKey -> {
                val newValue = transform(this.value)
                if(newValue == null) {
                    if(this.next == null) {
                        return null
                    } else {
                        HashTreapMap(this.next.key, this.next.value, this.next.next, this.left, this.right)
                    }
                } else if(newValue == value) {
                    this
                } else {
                    HashTreapMap(this.key, newValue, this.next, this.left, this.right)
                }
            }
            else -> {
                // look for entryKey in the buckets off of this one
                var newPairs: KeyValuePairList.More<K, V>? = null
                var found = false
                var it = this.next
                while(it != null) {
                    if(it.key == entryKey) {
                        val upd = transform(it.value)
                        found = true
                        if(upd != null && upd == it.value) {
                            return this
                        } else if(upd != null) {
                            newPairs = KeyValuePairList.More(it.key, upd, newPairs)
                        }
                    } else {
                        newPairs = KeyValuePairList.More(it.key, it.value, newPairs)
                    }
                    it = it.next
                }
                if(!found) {
                    val deNovoMerge = transform(null) ?: return this
                    newPairs = KeyValuePairList.More(entryKey, deNovoMerge, this.next)
                }
                HashTreapMap(this.key, this.value, newPairs, this.left, this.right)
            }
        }
    }

    override fun shallowRemove(element: K): HashTreapMap<K, V>? {
        if (!this.shallowContainsKey(element)) {
            return this
        } else {
            var newPairs: KeyValuePairList.More<K, V>? = null
            this.forEachPair {
                if (it.key != element) {
                    newPairs = KeyValuePairList.More(it.key, it.value, newPairs)
                }
            }
            val firstPair = newPairs
            return if (firstPair == null) {
                null
            } else {
                HashTreapMap(firstPair.key, firstPair.value, firstPair.next, this.left, this.right)
            }
        }
    }

    override val shallowSize: Int get() {
        var count = 0
        forEachPair {
            ++count
        }
        return count
    }

    override fun shallowEquals(that: HashTreapMap<K, V>): Boolean {
        forEachPair {
            if (!that.shallowContainsPair(it.key, it.value)) {
                return false
            }
        }
        return this.shallowSize == that.shallowSize
    }

    override fun shallowUpdateValues(transform: (K, V) -> V?): HashTreapMap<K, V>? {
        return when {
            next == null -> {
                val newValue = transform(key, value)
                when {
                    newValue == null -> null
                    newValue === value -> this
                    else -> HashTreapMap(key, newValue, null, left, right)
                }
            }
            else -> {
                var newPairs: KeyValuePairList.More<K, V>? = null
                this.forEachPair { (k, v) ->
                    val newValue = transform(k, v)
                    if (newValue != null) {
                        newPairs = KeyValuePairList.More(k, newValue, newPairs)
                    }
                }
                val firstPair = newPairs
                when {
                    firstPair == null -> null
                    else -> {
                        val newEntry = HashTreapMap(firstPair.key, firstPair.value, firstPair.next, this.left, this.right)
                        if (newEntry.shallowEquals(this)) { this } else { newEntry }
                    }
                }
            }
        }
    }

    override fun <R : Any> shallowMapReduce(map: (K, V) -> R, reduce: (R, R) -> R): R {
        var result: R? = null
        forEachPair {
            val mapped = map(it.key, it.value)
            result = result?.let { result -> reduce(result, mapped) } ?: mapped
        }
        return result!!
    }

    override fun forEachEntry(action: (Map.Entry<K, V>) -> Unit) {
        left?.forEachEntry(action)
        forEachPair { (k, v) -> action(MapEntry(k, v)) }
        right?.forEachEntry(action)
    }

    private fun treapSetFromKeys(): HashTreapSet<K> =
        HashTreapSet(treapKey, next?.toKeyList(), left?.treapSetFromKeys(), right?.treapSetFromKeys())

    inner class KeySet : AbstractKeySet<K, TreapKey.Hashed<K>, HashTreapSet<K>>() {
        override val map get() = this@HashTreapMap
        override val keys = lazy { treapSetFromKeys() }
        override fun hashCode() = super.hashCode() // avoids treapability warning
    }

    override val keys get() = KeySet()

    @Suppress("UNCHECKED_CAST")
    private fun <R> KeyValuePairList.More<K, R>.toNode(a: HashTreapMap<K, *>?, b: HashTreapMap<K, *>?): HashTreapMap<K, R> {
        val newNode = HashTreapMap(key, value, next, null, null)
        return when {
            a != null && newNode.shallowEquals(a as HashTreapMap<K, R>) -> a
            b != null && newNode.shallowEquals(b as HashTreapMap<K, R>) -> b
            else -> newNode
        }
    }

    private fun unionMerger(merger: (K, V, V) -> V) = 
        object : TreapMerger.KeepAllMergeIntersection<K, TreapKey.Hashed<K>, HashTreapMap<K, V>>() {
            override fun shallowMerge(a: HashTreapMap<K, V>?, b: HashTreapMap<K, V>?): HashTreapMap<K, V>? {
                a!!; b!!
                var pairs: KeyValuePairList.More<K, V>? = null
                a.forEachPair { (k, v) ->
                    if (b.shallowContainsKey(k)) {
                        merger(k, v, b.shallowGetValue(k)).let {
                            pairs = KeyValuePairList.More(k, it, pairs)
                        }
                    } else {
                        pairs = KeyValuePairList.More(k, v, pairs)
                    }
                }
                b.forEachPair { (k, v) ->
                    if (!a.shallowContainsKey(k)) {
                        pairs = KeyValuePairList.More(k, v, pairs)
                    }
                }
                return pairs!!.toNode(a, b)
            }
        }

    override fun union(m: Map<K, V>, merger: (K, V, V) -> V): TreapMap<K, V> = when (m) {
        is HashTreapMap<K, V> -> unionMerger(merger).merge(this, m).orEmpty()
        else -> fallbackUnion(m, merger)
    }

    override fun parallelUnion(m: Map<K, V>, parallelThresholdLog2: Int, merger: (K, V, V) -> V): TreapMap<K, V> = when (m) {
        is HashTreapMap<K, V> -> unionMerger(merger).parallelMerge(this, m, parallelThresholdLog2).orEmpty()
        else -> fallbackUnion(m, merger)
    }

    private fun <U, R> intersectMerger(merger: (K, V, U) -> R) =
        object : TreapMerger.KeepIntersection<K, TreapKey.Hashed<K>, HashTreapMap<K, V>, HashTreapMap<K, U>, HashTreapMap<K, R>>() {
            override fun shallowMerge(a: HashTreapMap<K, V>?, b: HashTreapMap<K, U>?): HashTreapMap<K, R>? {
                a!!; b!!
                var pairs: KeyValuePairList.More<K, R>? = null
                a.forEachPair { (k, v1) ->
                    if (b.shallowContainsKey(k)) {
                        val v2 = b.shallowGetValue(k)
                        merger(k, v1, v2).let {
                            pairs = KeyValuePairList.More(k, it, pairs)
                        }
                    }
                }
                return pairs?.toNode(a, b)
            }
        }

    override fun <U, R> intersect(m: Map<K, U>, merger: (K, V, U) -> R): TreapMap<K, R> = when (m) {
        is HashTreapMap<K, U> -> intersectMerger(merger).merge(this, m).orEmpty()
        else -> fallbackIntersect(m, merger)
    }

    override fun <U, R> parallelIntersect(m: Map<K, U>, parallelThresholdLog2: Int, merger: (K, V, U) -> R): TreapMap<K, R> = when (m) {
        is HashTreapMap<K, U> -> intersectMerger(merger).parallelMerge(this, m, parallelThresholdLog2).orEmpty()
        else -> fallbackIntersect(m, merger)
    }


    private fun <U> updateValuesMerger(transform: (K, V?, U) -> V?) =
        object : TreapMerger.KeepAllMergeB<K, TreapKey.Hashed<K>, HashTreapMap<K, V>, HashTreapMap<K, U>>() {
            override fun shallowMerge(a: HashTreapMap<K, V>?, b: HashTreapMap<K, U>?): HashTreapMap<K, V>? {
                b!!
                var pairs: KeyValuePairList.More<K, V>? = null
                a?.forEachPair { (k, v) ->
                    if (b.shallowContainsKey(k)) {
                        transform(k, v, b.shallowGetValue(k))?.let {
                            pairs = KeyValuePairList.More(k, it, pairs)
                        }
                    } else {
                        pairs = KeyValuePairList.More(k, v, pairs)
                    }
                }
                b.forEachPair { (k, v) ->
                    if (a?.shallowContainsKey(k) != true) {
                        transform(k, null, v)?.let {
                            pairs = KeyValuePairList.More(k, it, pairs)
                        }
                    }
                }
                return pairs?.toNode(a, b)
            }
        }

    private fun <U, R> mergeMerger(merger: (K, V?, U?) -> R?) =
        object : TreapMerger.MergeAll<K, TreapKey.Hashed<K>, HashTreapMap<K, V>, HashTreapMap<K, U>, HashTreapMap<K, R>>() {
            override fun shallowMerge(a: HashTreapMap<K, V>?, b: HashTreapMap<K, U>?): HashTreapMap<K, R>? {
                var pairs: KeyValuePairList.More<K, R>? = null
                a?.forEachPair { (k, v) ->
                    merger(k, v, b?.shallowGetValueOrNull(k))?.let {
                        pairs = KeyValuePairList.More(k, it, pairs)
                    }
                }
                b?.forEachPair { (k, v) ->
                    if (a?.shallowContainsKey(k) != true) {
                        merger(k, null, v)?.let {
                            pairs = KeyValuePairList.More(k, it, pairs)
                        }
                    }
                }
                return pairs?.toNode(a, b)
            }
        }

    override fun <U, R> merge(m: Map<K, U>, merger: (K, V?, U?) -> R?): TreapMap<K, R> = when (m) {
        is HashTreapMap<K, U> -> mergeMerger<U, R>(merger).merge(this, m).orEmpty()
        else -> fallbackMerge(m, merger)
    }
    override fun <U, R> parallelMerge(m: Map<K, U>, parallelThresholdLog2: Int, merger: (K, V?, U?) -> R?): TreapMap<K, R> = when (m) {
        is HashTreapMap<K, U> -> mergeMerger<U, R>(merger).parallelMerge(this, m, parallelThresholdLog2).orEmpty()
        else -> fallbackMerge(m, merger)
    }

    private fun <U, R> mergeIntersectionMerger(merger: (K, V, U) -> R?) =
        object : TreapMerger.KeepIntersection<K, TreapKey.Hashed<K>, HashTreapMap<K, V>, HashTreapMap<K, U>, HashTreapMap<K, R>>() {
            override fun shallowMerge(a: HashTreapMap<K, V>?, b: HashTreapMap<K, U>?): HashTreapMap<K, R>? {
                var pairs: KeyValuePairList.More<K, R>? = null
                a?.forEachPair { (k, v1) ->
                    if (b?.shallowContainsKey(k) == true) {
                        val v2 = b.shallowGetValue(k)
                        merger(k, v1, v2)?.let {
                            pairs = KeyValuePairList.More(k, it, pairs)
                        }
                    }
                }
                return pairs?.toNode(a, b)
            }
        }

    override fun <U, R> mergeIntersection(m: Map<K, U>, merger: (K, V, U) -> R?): TreapMap<K, R> = when (m) {
        is HashTreapMap<K, U> -> mergeIntersectionMerger(merger).merge(this, m).orEmpty()
        else -> fallbackMergeIntersection(m, merger)
    }
    override fun <U, R> parallelMergeIntersection(m: Map<K, U>, parallelThresholdLog2: Int, merger: (K, V, U) -> R?): TreapMap<K, R> = when (m) {
        is HashTreapMap<K, U> -> mergeIntersectionMerger(merger).parallelMerge(this, m, parallelThresholdLog2).orEmpty()
        else -> fallbackMergeIntersection(m, merger)
    }

    private fun <R> lookupMerger(transform: (K, V?) -> R) =
        object : TreapMerger.KeepB<K, TreapKey.Hashed<K>, HashTreapMap<K, V>, HashTreapSet<K>, HashTreapMap<K, R>>() {
            override fun shallowMerge(a: HashTreapMap<K, V>?, b: HashTreapSet<K>?): HashTreapMap<K, R>? {
                b!!
                var pairs: KeyValuePairList.More<K, R>? = null
                a?.forEachPair { (k, v) ->
                    if (b.shallowContains(k)) {
                        transform(k, v).let {
                            pairs = KeyValuePairList.More(k, it, pairs)
                        }
                    }
                }
                return pairs?.toNode(a, null)
            }
        }

    override fun <R> lookup(keys: Set<K>, transform: (K, V?) -> R): TreapMap<K, R> = when (keys) {
        is HashTreapSet<K> -> lookupMerger(transform).merge(this, keys).orEmpty()
        else -> fallbackLookup(keys, transform)
    }
    override fun <R> parallelLookup(keys: Set<K>, parallelThresholdLog2: Int, transform: (K, V?) -> R): TreapMap<K, R> = when (keys) {
        is HashTreapSet<K> -> lookupMerger(transform).parallelMerge(this, keys, parallelThresholdLog2).orEmpty()
        else -> fallbackLookup(keys, transform)
    }


    override fun <U> updateValues(
        m: Map<K, U>,
        transform: (K, V?, U) -> V?
    ): TreapMap<K, V> = when (m) {
        is HashTreapMap<K, U> -> updateValuesMerger(transform).merge(this, m).orEmpty()
        else -> fallbackUpdateValues(m, transform)
    }

    override fun <U> parallelUpdateValues(
        m: Map<K, U>,
        parallelThresholdLog2: Int,
        transform: (K, V?, U) -> V?
    ): TreapMap<K, V> = when (m) {
        is HashTreapMap<K, U> -> updateValuesMerger(transform).parallelMerge(this, m, parallelThresholdLog2).orEmpty()
        else -> fallbackUpdateValues(m, transform)
    }
}

internal interface KeyValuePairList<K, V> {
    abstract val key: K
    abstract val value: V
    abstract val next: More<K, V>?
    operator fun component1() = key
    operator fun component2() = value

    fun toKeyList(): ElementList.More<K> = ElementList.More(key, next?.toKeyList())

    class More<K, V>(
        override val key: K,
        override val value: V,
        override val next: More<K, V>?
    ) : KeyValuePairList<K, V>, java.io.Serializable
}


