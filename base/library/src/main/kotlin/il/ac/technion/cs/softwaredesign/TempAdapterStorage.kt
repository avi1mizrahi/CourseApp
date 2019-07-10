package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

/** This is an adapter interface for the previous storage,
 *   and adapter classes for each conversion
 */

interface SyncStorage {
    fun read(key: ByteArray): ByteArray?
    fun write(key: ByteArray, value: ByteArray)
}

class AsyncStorageAdapter @Inject constructor(secureStorageFactory: SecureStorageFactory) : SyncStorage {
    private val secureStorage = secureStorageFactory.open("main".toByteArray()).join()

    override fun read(key: ByteArray): ByteArray? =
            secureStorage.read(key).join()

    override fun write(key: ByteArray, value: ByteArray) =
            secureStorage.write(key, value).join()!!

}

