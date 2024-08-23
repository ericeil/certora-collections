package utils

import java.io.*
import java.nio.file.*
import java.lang.reflect.*
import java.util.*

/**
    Given a Java-serializable object, produces a human-readable text file containing the details of the data that would
    be serialized from that object.  The resulting text file is useful for diagnosing serialization-related problems.
    The format is designed to be usefully diff-able, so this is also useful for simply comparing two object graphs. The
    resulting file is not meant to be consumed programatically; use normal serialization if you need that!

    To make this work, you will likely need to add the following to the java command line:
    ```
        --add-opens java.base/java.util=ALL-UNNAMED
    ```
 */
public fun debugJavaSerializationDump(obj: Serializable, path: Path, append: Boolean = false) {
    Files.newBufferedWriter(
        path,
        StandardOpenOption.CREATE,
        if (append) { StandardOpenOption.APPEND } else { StandardOpenOption.TRUNCATE_EXISTING}
    ).use { writer ->
        DebugObjectOutputStream(writer).use { oos ->
            oos.writeObject(obj)
        }
    }
}

private fun ByteArray.toHexString(): String =
    this.joinToString("") { String.format(Locale.ROOT, "%02X", it) }

/**
 * Converts a (signed) Byte [this] to a hex string of 2 chars, e.g. 15 becomes "0f", 16 becomes "10"
 */
private fun Byte.toHexString(): String {
    val translateThis = if (this < 0) {
        this.toInt().plus(256)
    } else {
        this.toInt()
    }
    return translateThis.toString(16).let { r ->
        check (r.length <= 2 && r.length > 0) { "Invalid byte $this -> $translateThis"}
        if (r.length == 1) { "0$r" } else { r }
    }
}

private class DebugObjectOutputStream(private val writer: Appendable) : ObjectOutputStream() {

    private var depth = 0
    var nextHandle = 0
    var handles = IdentityHashMap<Any, Int>()
    var currentObject: Any? = null

    private fun writeln(s: String) {
        repeat(depth) { writer.append("  ") }
        writer.append(s)
        writer.append('\n')
    }

    private fun nest(header: String, writer: () -> Unit) {
        writeln("$header {")
        depth++
        writer()
        depth--
        writeln("}")
    }

    private fun Class<*>.getDeclaredMethodOrNull(name: String, vararg args: Class<*>): Method? =
        try {
            getDeclaredMethod(name, *args)
        } catch (_: NoSuchMethodException) {
            null
        }

    private fun Class<*>.getInheritableMethodOrNull(name: String, vararg args: Class<*>): Method? =
        getDeclaredMethodOrNull(name, *args) ?: getSuperclass()?.getInheritableMethodOrNull(name, *args)

    protected final override fun writeObjectOverride(obj: Any?) {
        when {
            obj == null -> writeln("null")
            handles[obj] != null -> writeln("@${handles[obj]}")
            else -> writeNewObject(obj)
        }
    }

    private fun writeNewObject(obj: Any) {
        val handle = nextHandle++
        handles[obj] = handle

        when (obj) {
            is String -> writeln("String $obj @$handle")
            is Byte -> writeln("Byte $obj @$handle")
            is Short -> writeln("Short $obj @$handle")
            is Int -> writeln("Int $obj @$handle")
            is Long -> writeln("Long $obj @$handle")
            is Float -> writeln("Float $obj @$handle")
            is Double -> writeln("Double $obj @$handle")
            is Char -> writeln("Char $obj @$handle")
            is Boolean -> writeln("Boolean $obj @$handle")
            is Enum<*> -> writeln("Enum ${obj.javaClass.name}.${obj.name} @$handle")
            is Class<*> -> writeln("class ${obj.name} @$handle")
            is ByteArray -> writeln(obj.joinToString(",", "ByteArray [", "] @$handle") { it.toHexString() })
            is ShortArray -> writeln(obj.joinToString(",", "ShortArray [", "] @$handle"))
            is IntArray -> writeln(obj.joinToString(",", "IntArray [", "] @$handle"))
            is LongArray -> writeln(obj.joinToString(",", "LongArray [", "] @$handle"))
            is FloatArray -> writeln(obj.joinToString(",", "FloatArray [", "] @$handle"))
            is DoubleArray -> writeln(obj.joinToString(",", "DoubleArray [", "] @$handle"))
            is CharArray -> writeln(obj.joinToString(",", "CharArray [", "] @$handle"))
            is BooleanArray -> writeln(obj.joinToString(",", "BooleanArray [", "] @$handle"))
            is Array<*> -> nest("${obj.javaClass.name} @$handle %${obj.hashCode().toString(16)}") { obj.forEach { writeObject(it) } }
            else -> {
                obj.javaClass.getInheritableMethodOrNull("writeReplace")?.let { writeReplaceMethod ->
                    nest("writeReplace") { writeObject(writeReplaceMethod.invoke(obj)) }
                } ?: run {
                    writeOrdinaryObject(handle, obj, obj.javaClass)
                }
            }
        }
    }

    private fun writeOrdinaryObject(handle: Int, obj: Any, clazz: Class<*>) {
        nest("${clazz.name} @$handle %${obj.hashCode().toString(16)}") {
            clazz.getSuperclass()?.let {
                if (Serializable::class.java.isAssignableFrom(it)) {
                    writeOrdinaryObject(handle, obj, it)
                }
            }
            clazz.getDeclaredMethodOrNull("writeObject", ObjectOutputStream::class.java)?.let { writeObjectMethod ->
                writeObjectMethod.setAccessible(true)
                currentObject = obj
                nest("writeObject") { writeObjectMethod.invoke(obj, this@DebugObjectOutputStream) }
                currentObject = null
            } ?: run {
                defaultWriteFields(obj, clazz)
            }
        }
    }

    private fun defaultWriteFields(obj: Any, clazz: Class<*>) {
        for (fieldDesc in ObjectStreamClass.lookup(clazz)?.fields.orEmpty()) {
            runCatching {
                clazz.getDeclaredField(fieldDesc.name)
            }.onSuccess { field ->
                field.isAccessible = true
                nest(fieldDesc.name) { writeObject(field.get(obj)) }
            }.onFailure {
                writeln("(${fieldDesc.name}: $it)")
            }
        }
    }

    override fun defaultWriteObject() {
        nest("defaultWriteObject") {
            defaultWriteFields(currentObject!!, currentObject!!.javaClass)
        }
    }

    override fun putFields(): ObjectOutputStream.PutField = object : ObjectOutputStream.PutField() {
        override fun put(p0: String, p1: Boolean) { nest("$p0") { writeBoolean(p1) } }
        override fun put(p0: String, p1: Byte) { nest("$p0") { writeByte(p1.toInt()) } }
        override fun put(p0: String, p1: Char) { nest("$p0") { writeChar(p1.code) } }
        override fun put(p0: String, p1: Double) { nest("$p0") { writeDouble(p1) } }
        override fun put(p0: String, p1: Float) { nest("$p0") { writeFloat(p1) } }
        override fun put(p0: String, p1: Int) { nest("$p0") { writeInt(p1) } }
        override fun put(p0: String, p1: Long) { nest("$p0") { writeLong(p1) } }
        override fun put(p0: String, p1: Any) { nest("$p0") { writeObject(p1) } }
        override fun put(p0: String, p1: Short) { nest("$p0") { writeShort(p1.toInt()) } }
        @Deprecated("Deprecated in JDK") override fun write(out: ObjectOutput) { }
    }

    override fun writeFields() { }

    override fun write(`val`: Int) { writeln("write ${`val`.toString(16)}") }
    override fun write(buf: ByteArray) { writeln("write ${buf.joinToString(",", "ByteArray[", "]") { it.toHexString() }}") }
    override fun write(buf: ByteArray, off: Int, len: Int) { write(Arrays.copyOfRange(buf, off, off+len)) }

    override fun writeBoolean(`val`: Boolean) { writeln(">Boolean ${`val`}") }
    override fun writeByte(`val`: Int) { writeln(">Byte ${`val`}") }
    override fun writeShort(`val`: Int) { writeln(">Short ${`val`}") }
    override fun writeChar(`val`: Int) { writeln(">Char ${`val`}") }
    override fun writeInt(`val`: Int) { writeln(">Int ${`val`}") }
    override fun writeLong(`val`: Long) { writeln(">Long ${`val`}") }
    override fun writeFloat(`val`: Float) { writeln(">Float ${`val`}") }
    override fun writeDouble(`val`: Double) { writeln(">Double ${`val`}") }
    override fun writeBytes(str: String) { writeln(">Bytes ${str}") }
    override fun writeChars(str: String) { writeln(">Chars ${str}") }
    override fun writeUTF(str: String) { writeln(">UTF ${str}") }

    override fun close() {}
}
