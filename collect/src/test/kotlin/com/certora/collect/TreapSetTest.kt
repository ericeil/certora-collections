package com.certora.collect

import java.util.Random
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.DeserializationStrategy

/** Tests for [TreapSet]. */
abstract class TreapSetTest {

    abstract fun makeKey(value: Int, code: Int = value.hashCode()): TestKey
    open val nullKeysAllowed: Boolean get() = true

    fun makeSet(): TreapSet.Builder<TestKey?> = treapSetOf<TestKey?>().builder()
    abstract fun makeBaseline(): MutableSet<TestKey?>

    fun makeSet(other: Collection<TestKey?>): TreapSet.Builder<TestKey?> = makeSet().also { it += other }
    fun makeBaseline(other: Collection<TestKey?>): MutableSet<TestKey?> = makeSet().also { it += other }

    open fun assertOrderedIteration(expected: Iterator<*>, actual: Iterator<*>) {}

    fun assertVeryEqual(expected: Set<*>, actual: Set<*>) {
        assertEquals(expected, actual)
        assertTrue(actual.equals(expected))
        assertEquals(expected.hashCode(), actual.hashCode())
        assertEquals(expected.size, actual.size)
        assertEquals(expected.isEmpty(), actual.isEmpty())
        assertOrderedIteration(expected.iterator(), actual.iterator())
    }

    fun <TResult> assertEqualMutation(
            baseline: MutableSet<TestKey?>,
            set: MutableSet<TestKey?>,
            action: MutableSet<TestKey?>.() -> TResult
    ) {
        assertEqualResult(baseline, set, action)
        assertVeryEqual(baseline, set)
    }

    fun <TResult> assertEqualResult(
            baseline: MutableSet<TestKey?>,
            set: MutableSet<TestKey?>,
            action: MutableSet<TestKey?>.() -> TResult
    ) {
        assertEquals(baseline.action(), set.action())
    }

    @Test
    fun construct() {
        val b = makeBaseline()
        val s = makeSet()
        assertVeryEqual(b, s)
    }

    @Test
    fun addAndContains() {
        val b = makeBaseline()
        val s = makeSet()

        val o1 = makeKey(5)
        assertEqualResult(b, s) { contains(o1) }
        assertEqualMutation(b, s) { add(o1) }
        assertEqualResult(b, s) { contains(o1) }

        assertEqualMutation(b, s) { add(o1) }
        assertEqualResult(b, s) { contains(o1) }

        val o2 = makeKey(3)
        assertEqualResult(b, s) { contains(o2) }
        assertEqualMutation(b, s) { add(o2) }
        assertEqualResult(b, s) { contains(o2) }

        assertEqualMutation(b, s) { add(o2) }
        assertEqualResult(b, s) { contains(o2) }

        assertEqualResult(b, s) { contains(o1) }
        assertEqualMutation(b, s) { add(o1) }
        assertEqualResult(b, s) { contains(o1) }

        val o3 = makeKey(4)
        assertEqualResult(b, s) { contains(o3) }
        assertEqualMutation(b, s) { add(o3) }
        assertEqualResult(b, s) { contains(o3) }

        val o4 = makeKey(-908857765)
        assertEqualResult(b, s) { contains(o4) }
        assertEqualMutation(b, s) { add(o4) }
        assertEqualResult(b, s) { contains(o4) }
    }

    @Test
    fun remove() {
        val b = makeBaseline()
        val s = makeSet()

        val objs = Array<TestKey>(5) { makeKey(it, code = 0) }
        for (o in objs) {
            assertEqualMutation(b, s) { add(o) }
            assertEqualResult(b, s) { contains(o) }
        }

        assertEqualMutation(b, s) { remove(objs[0]) }
        assertEqualResult(b, s) { contains(objs[0]) }

        assertEqualMutation(b, s) { remove(objs[1]) }
        assertEqualResult(b, s) { contains(objs[1]) }

        assertEqualMutation(b, s) { remove(objs[4]) }
        assertEqualResult(b, s) { contains(objs[4]) }

        assertEqualMutation(b, s) { remove(objs[2]) }
        assertEqualResult(b, s) { contains(objs[2]) }

        assertEqualMutation(b, s) { remove(objs[3]) }
        assertEqualResult(b, s) { contains(objs[3]) }

        for (o in objs) {
            assertEqualMutation(b, s) { add(o) }
            assertEqualResult(b, s) { contains(o) }
        }
    }

    @Test
    fun clear() {
        val s = makeSet()
        val k = makeKey(1)
        s.add(k)
        assertEquals(1, s.size)
        assertTrue(k in s)
        s.clear()
        assertEquals(0, s.size)
        assertFalse(k in s)
    }

    fun randomHashObjectMaybeNull(rand: Random): TestKey? {
        val r = rand.nextInt()
        if (nullKeysAllowed) {
            if (r % 20 == 0) {
                return null
            }
        }
        return makeKey(r % 2000)
    }

    @Test
    fun addMany() {
        val b = makeBaseline()
        val s = makeSet()

        val rand = Random(1234)

        for (i in 1..65550) {
            val elem = randomHashObjectMaybeNull(rand)
            when (i) {
                1, 255, 256, 257, 65535, 65536, 65537 -> {
                    assertEqualMutation(b, s) { add(elem) }
                }
                else -> {
                    assertEqualResult(b, s) { add(elem) }
                }
            }
            assertEqualResult(b, s) { contains(elem) }
        }
    }

    @Test
    fun nullKey() {
        if (nullKeysAllowed) {
            val b = makeBaseline()
            val s = makeSet()

            assertEqualMutation(b, s) { add(makeKey(0)) }
            assertEqualResult(b, s) { contains(null) }
            assertEqualMutation(b, s) { add(null) }
            assertEqualMutation(b, s) { add(null) }
            assertEqualResult(b, s) { contains(null) }

            assertEqualMutation(b, s) { remove(null) }
            assertEqualResult(b, s) { contains(null) }
            assertEqualMutation(b, s) { remove(null) }
            assertEqualResult(b, s) { contains(null) }
        }
    }

    @Test
    fun addNullAndHashElementsByAddAndAddAll() {
        val plain = makeKey(0)
        val hash = HashTestKey(0)
        addSequence("addNullSetAtEnd", plain, null, useAddAll = true)
        addSequence("addNullSetAtStart", null, plain, useAddAll = true)
        addSequence("addNullElementAtEnd", plain, null, useAddAll = false)
        addSequence("addNullElementAtStart", null, plain, useAddAll = false)
        addSequence("addHashElementAtEnd", plain, hash, useAddAll = false)
        addSequence("addHashElementAtStart", hash, plain, useAddAll = false)
        addSequence("addHashSetAtEnd", plain, hash, useAddAll = true)
        addSequence("addHashSetAtStart", hash, plain, useAddAll = true)
    }

    private fun addSequence(name: String, first: TestKey?, second: TestKey?, useAddAll: Boolean) {
        val set = makeSet()
        for (element in listOf(first, second)) {
            if (useAddAll) {
                set.addAll(treapSetOf(element))
            } else {
                set.add(element)
            }
        }
        assertEquals(set, setOf<TestKey?>(first, second), name)
    }

    @Test
    fun nullQueries() {
        val withNull = makeSet()
        withNull.add(makeKey(0))
        withNull.add(null)

        val withoutNull = makeSet()
        withNull.add(makeKey(0))

        assertTrue(null in withNull)
        assertFalse(null in withoutNull)

        assertTrue(withNull.containsAll(treapSetOf(null)))
        assertFalse(withoutNull.containsAll(treapSetOf(null)))

        assertTrue(withNull.remove(null))
        assertFalse(withoutNull.remove(null))
    }

    @Test
    fun hashQueries() {
        val withHash = makeSet()
        withHash.add(makeKey(0))
        withHash.add(HashTestKey(0))

        val withoutHash = makeSet()
        withHash.add(makeKey(0))

        assertTrue(HashTestKey(0) in withHash)
        assertFalse(HashTestKey(0) in withoutHash)

        assertTrue(withHash.containsAll(treapSetOf(HashTestKey(0))))
        assertFalse(withoutHash.containsAll(treapSetOf(HashTestKey(0))))

        assertTrue(withHash.remove(HashTestKey(0)))
        assertFalse(withoutHash.remove(HashTestKey(0)))
    }

    @Test
    fun retainAll() {
        val b = makeBaseline()
        val s = makeSet()

        assertEqualMutation(b, s) { addAll((1..4).map { makeKey(it) }) }
        assertEqualMutation(b, s) { retainAll((1..4).map { makeKey(it) }) }

        assertEqualMutation(b, s) { retainAll((3..6).map { makeKey(it) }) }

        assertEqualMutation(b, s) { retainAll((5..100).map { makeKey(it) }) }

        assertTrue(s.isEmpty())
    }

    @Test
    fun copyFromSetAndList() {
        assertCopiedFrom(setOf<TestKey?>())
        assertCopiedFrom(listOf<TestKey?>())

        val rand = Random(1234)
        val generated = Array<TestKey>(10000) { makeKey(rand.nextInt()) }.toList()
        assertCopiedFrom(makeBaseline(generated))
        assertCopiedFrom(generated)
    }

    private fun assertCopiedFrom(source: Collection<TestKey?>) {
        val baseline = makeBaseline(source)
        val set = makeSet(source)
        assertVeryEqual(baseline, set)
        if (source is Set<*>) {
            assertVeryEqual(source, set)
        }
    }

    open fun assertIdenticalJson(expected: String, actual: String) {}
    abstract fun getBaseDeserializer(): DeserializationStrategy<*>?
    abstract fun getDeserializer(): DeserializationStrategy<*>?

    @Test
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun kotlinJsonSerialize() {
        if (getDeserializer() == null) {
            return
        }
        val b = makeBaseline()
        b.add(makeKey(1))
        b.add(makeKey(9897))
        b.add(makeKey(3))
        b.add(null)
        b.add(makeKey(5))

        val s = makeSet(b)

        val bs = Json.encodeToString(b)
        val ss = Json.encodeToString(s)

        assertIdenticalJson(bs, ss)

        @Suppress("UNCHECKED_CAST")
        val db = Json.decodeFromString(getBaseDeserializer()!!, bs) as Set<TestKey?>
        @Suppress("UNCHECKED_CAST")
        val ds = Json.decodeFromString(getDeserializer()!!, ss) as Set<TestKey?>

        assertVeryEqual(db, ds)
    }

    fun roundTripSerialize(set: MutableSet<TestKey?>): MutableSet<TestKey?> {
        val bos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(bos)
        oos.writeObject(set)
        oos.flush()

        val bytes = bos.toByteArray()
        val bis = ByteArrayInputStream(bytes)
        val ois = ObjectInputStream(bis)

        @Suppress("UNCHECKED_CAST")
        return ois.readObject() as MutableSet<TestKey?>
    }

    @Test
    fun javaSerialize() {
        val b = makeBaseline()
        b.add(makeKey(1))
        b.add(makeKey(9897))
        b.add(makeKey(3))
        if (nullKeysAllowed) {
            b.add(null)
        }
        b.add(makeKey(5))

        val s = makeSet(b)

        val rb = roundTripSerialize(b)
        val rs = roundTripSerialize(s)

        assertVeryEqual(b, rb)
        assertVeryEqual(s, rs)
        assertVeryEqual(b, rs)
    }

    @Test
    fun arbitraryOrNull() {
        val s = makeSet()
        assertNull(s.build().arbitraryOrNull())

        s += makeKey(1, 1)
        assertEquals(makeKey(1, 1), s.build().arbitraryOrNull())

        s += makeKey(2, 1)
        assertTrue(s.build().arbitraryOrNull() in (1..2).map { makeKey(it) })

        for (it in 3..100) {
            s += makeKey(it)
        }
        assertTrue(s.build().arbitraryOrNull() in (1..100).map { makeKey(it) })
    }
}

