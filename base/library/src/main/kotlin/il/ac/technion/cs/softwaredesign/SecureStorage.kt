package il.ac.technion.cs.softwaredesign.storage


// TODO these should be provided by library.jar? Where is the new jar?

interface SecureStorageFactory {
    fun open(name: ByteArray): SecureStorage
}
interface SecureStorage {
    fun write(key: ByteArray, value: ByteArray)
    fun read(key: ByteArray): ByteArray?
}