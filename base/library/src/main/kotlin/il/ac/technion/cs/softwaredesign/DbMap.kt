package il.ac.technion.cs.softwaredesign

interface DbMap<V> {
    fun write(key: String, value: V)
    fun read(key: String): V?
    fun delete(key: String)
}


fun <V> KeyValueStore.getMapReference(key: List<String>, serializer: Serializer<V>): DbMap<V> =
        DbMapImpl(key, this, serializer)

fun KeyValueStore.getIntMapReference(key: List<String>): DbMap<Int> =
        getMapReference(key, IntSerializer())

private class DbMapImpl<V>(key: List<String>,
                           parent: KeyValueStore,
                           val serializer: Serializer<V>) : DbMap<V> {
    private val parent: KeyValueStore

    init {
        this.parent = ScopedKeyValueStore(key, parent)
    }

    override fun write(key: String, value: V) =
            parent.getReference(listOf(key), serializer).write(value)

    override fun read(key: String): V? =
            parent.getReference(listOf(key), serializer).read()

    override fun delete(key: String) =
            parent.getReference(listOf(key), serializer).delete()
}

