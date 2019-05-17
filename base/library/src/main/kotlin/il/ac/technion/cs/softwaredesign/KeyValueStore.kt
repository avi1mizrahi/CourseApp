package il.ac.technion.cs.softwaredesign


/** Represents a storage-backed Key-Value store */
interface KeyValueStore {

    /** Database object accessor */
    interface Object<V> {
        fun write(value: V)
        fun read(): V?
        fun delete()
    }

    /**
     * Get object reference identified by [key], using the given [serializer].
     * It's the user responsibility to supply the same serializer when re-getting reference
     */
    fun <V> getReference(key: List<String>, serializer: Serializer<V>): Object<V>
}

/**
 * Wrap [parent] store calls with the given [prefix],
 * effectively creating a directory in the key-space
 * */
class ScopedKeyValueStore(private val parent: KeyValueStore, private val prefix: List<String>) : KeyValueStore {
    constructor(parent: ScopedKeyValueStore, prefix: List<String>) : this(parent.parent, parent.prefix + prefix)

    override fun <V> getReference(key: List<String>,
                                  serializer: Serializer<V>) =
            parent.getReference(prefix + key, serializer)
}

/****** EXTENSION METHODS ******/

/** specialization references for common types: */

fun KeyValueStore.getIntReference(key: List<String>): KeyValueStore.Object<Int> =
        getReference(key, IntSerializer())
fun KeyValueStore.getIntReference(key: String): KeyValueStore.Object<Int> = getIntReference(listOf(key))


fun KeyValueStore.getStringReference(key: List<String>): KeyValueStore.Object<String> =
        getReference(key, StringSerializer())
fun KeyValueStore.getStringReference(key: String): KeyValueStore.Object<String> = getStringReference(listOf(key))

