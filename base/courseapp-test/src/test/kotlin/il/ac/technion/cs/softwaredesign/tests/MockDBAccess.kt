package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.DBAccess

class MockDBAccess() : DBAccess() {

    // TODO don't know if we can do map with ByteArray as key, it hashes/compares the pointer and not the contents.
    private val keyvalDB = HashMap<String, ByteArray>()
    protected override fun read(key: ByteArray): ByteArray? {
        return keyvalDB[key.toString(encoding)]
    }

    protected override fun write(key: ByteArray, value: ByteArray) {
        keyvalDB[key.toString(encoding)] = value
    }
}