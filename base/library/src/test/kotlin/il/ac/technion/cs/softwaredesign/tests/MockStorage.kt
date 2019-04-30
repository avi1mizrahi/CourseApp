package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.Storage

class MockStorage : Storage {
    private val encoding = Charsets.UTF_8

    private val keyvalDB = HashMap<String, ByteArray>()

    override fun read(key: ByteArray): ByteArray? {
        return keyvalDB[key.toString(encoding)]
    }

    override fun write(key: ByteArray, value: ByteArray) {
        keyvalDB[key.toString(encoding)] = value
    }
}