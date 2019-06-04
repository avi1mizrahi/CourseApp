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
class ScopedKeyValueStore(private val parent: KeyValueStore,
                          private val prefix: List<String>) : KeyValueStore {
    override fun <V> getReference(key: List<String>,
                                  serializer: Serializer<V>) =
            parent.getReference(prefix + key, serializer)

    override fun equals(other: Any?): Boolean {
        if (other !is ScopedKeyValueStore) return false

        if (this.parent != other.parent) return false

        repeat (this.prefix.size) {
            if (this.prefix[it] != other.prefix[it]) return false
        }
        return true
    }

    // TODO if we use collections with scopedDB for some reason
    override fun hashCode(): Int {
        return super.hashCode()
    }

}

/****** EXTENSION METHODS ******/

/** specialization references for common types: */

fun KeyValueStore.getIntReference(key: List<String>): KeyValueStore.Object<Int> =
        getReference(key, IntSerializer())
fun KeyValueStore.getIntReference(key: String): KeyValueStore.Object<Int> = getIntReference(listOf(key))


fun KeyValueStore.getStringReference(key: List<String>): KeyValueStore.Object<String> =
        getReference(key, StringSerializer())
fun KeyValueStore.getStringReference(key: String): KeyValueStore.Object<String> = getStringReference(listOf(key))

// TODO tests for byteArray
fun KeyValueStore.getByteArrayReference(key: List<String>): KeyValueStore.Object<ByteArray> =
        getReference(key, ByteArraySerializer())
fun KeyValueStore.getByteArrayReference(key: String): KeyValueStore.Object<ByteArray> = getByteArrayReference(listOf(key))


fun KeyValueStore.scope(keys : List<String>) : ScopedKeyValueStore = ScopedKeyValueStore(this, keys)
fun KeyValueStore.scope(key : String) : ScopedKeyValueStore = this.scope(listOf(key))
