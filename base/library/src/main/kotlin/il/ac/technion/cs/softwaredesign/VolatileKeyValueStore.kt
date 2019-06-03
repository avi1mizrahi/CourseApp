package il.ac.technion.cs.softwaredesign

class VolatileKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<List<String>, Any>()

    override fun <V> getReference(key: List<String>,
                                  serializer: Serializer<V>): KeyValueStore.Object<V> =
            object : KeyValueStore.Object<V> {

                override fun write(value: V) = map.set(key, value as Any)
                @Suppress("UNCHECKED_CAST")
                override fun read(): V? = map[key] as V?

                override fun delete() {
                    map.remove(key)
                }
            }
}