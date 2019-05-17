package il.ac.technion.cs.softwaredesign

interface DbMap<V> {
    fun write(key: String, value: V)
    fun read(key: String): V?
    fun delete(key: String)
}


fun <V> KeyValueStore.getMapReference(key: List<String>, serializer: Serializer<V>): DbMap<V> =
        DbMapImpl(ScopedKeyValueStore(this, key), serializer)

fun KeyValueStore.getIntMapReference(key: String): DbMap<Int> = getIntMapReference(listOf(key))
fun KeyValueStore.getIntMapReference(key: List<String>): DbMap<Int> = getMapReference(key, IntSerializer())

private class DbMapImpl<V>(val DB: ScopedKeyValueStore,
                           val serializer: Serializer<V>) : DbMap<V> {


    override fun write(key: String, value: V) =
            DB.getReference(listOf(key), serializer).write(value)

    override fun read(key: String): V? =
            DB.getReference(listOf(key), serializer).read()

    override fun delete(key: String) =
            DB.getReference(listOf(key), serializer).delete()
}

