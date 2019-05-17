package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.KeyValueStore
import il.ac.technion.cs.softwaredesign.Serializer

class MockKeyValueStore : KeyValueStore {
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