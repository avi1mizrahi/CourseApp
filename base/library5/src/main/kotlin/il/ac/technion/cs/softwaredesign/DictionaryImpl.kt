package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.nio.charset.Charset
import java.util.HashMap
import java.util.LinkedList

/**
 * Implements [Dictionary] using [SecureStorage].
 * Each Dictionary has a unique [dictionaryId] ([DictionaryFactory] take care of that)
 * and it use this id as prefix to the key, so no other dictionary can change the data
 * (unless you used [DictionaryFactory.restoreDictionary] to get the same dictionary
 * or use other DictionaryFactory with the same name. don't do the second).
 * This [dictionaryId] also use for dictionary restoring, get it from [getId] and use it
 * as argument for [DictionaryFactory.restoreDictionary].
 *
 * Dictionary has [counter] (Int) that initiated to 0 and modified only from [incCount].
 * if the dictionary is restored, the [counter] will restored (and this is the only time you read it from storage)
 * [counter] stored in storage every time [incCount] called.
 * If storage was cleared the counter will go back to 0, see [count] to see how it works.
 *
 * secureStorage Latency - only for [read] and [contains] - the length of the value in millis
 */
class DictionaryImpl(private val storage: SecureStorage, private val dictionaryId: ByteArray) : Dictionary {
    private var counter = storage.read(dictionaryId + 2.toByte()).join()?.toString(Charset.defaultCharset())?.toInt()?: 0

    override fun read(key: String): String? {
        val k = (dictionaryId + 0.toByte() + key.toByteArray()).toString(Charsets.UTF_8)
        var value = readFromCache(k)
        if (value == null) {
            value = storage.read(k.toByteArray()).join()?.toString(Charset.defaultCharset())
            updateCache(k, value)
        }


        return value
    }

    override fun write(key: String, value: String) {
        val k = (dictionaryId + 0.toByte() + key.toByteArray()).toString(Charsets.UTF_8)
        updateCache(k, value)

        storage.write(k.toByteArray(), value.toByteArray()).join()
    }

    private val CACHE_SIZE = 350
    private val cache = HashMap<String, String?>()
    private val cacheKeys = LinkedList<String>()


    @Synchronized
    private fun readFromCache(key: String) : String? {
        if (cache.contains(key)) {
            return cache[key]
        }
        return null
    }

    @Synchronized
    private fun updateCache(key: String, value: String?) {
        value ?: return

        if (cache.size >= CACHE_SIZE) {
            val keyToRemove = cacheKeys.poll()
            cache.keys.remove(keyToRemove)
        }

        if (cache.containsKey(key)) {
            cacheKeys.remove(key) // This is O(N)
        }
        cacheKeys.addLast(key)

        cache[key] = value
    }

    override fun contains(key: String): Boolean {
        read(key)?: return false
        return true
    }

    override fun isEmpty(): Boolean {
        return count() == 0
    }

    override fun nonEmpty(): Boolean {
        return !isEmpty()
    }

    override fun count(): Int {
        if (storage.read(dictionaryId + 1.toByte()).join() == null){
            // you here if storage was cleared and this instance is the same
            counter = 0
            storage.write(dictionaryId + 1.toByte(), "".toByteArray())
        }
        return counter
    }

    override fun incCount(by: Int) {
        counter = count() + by
        setCount(counter)
    }

    override fun getId(): String{
        return dictionaryId.toString(Charset.defaultCharset())
    }

    private fun setCount(value: Int){
        storage.write(dictionaryId + 2.toByte() , value.toString().toByteArray()).join()
    }
}
