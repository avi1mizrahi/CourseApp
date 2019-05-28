# CourseApp: Assignment 2

## Authors
* Alon Tavor, 307915561
* Avi Mizrahi, 200668945

### Previous assignment
200668945-307915561

## Notes

### Implementation Summary
TODO: Short summary of your implementation, including data structures used, design choices made, and
a short tour of the class hierarchy you created.

Our software stack built with 3 layers:
1. SecureStorage - a persistent storage with a raw interface (as you supplied)
2. Library - Persistent Data Structures
3. CourseAppImpl - the business logic. Uses DataTypeProxies - represents entities backed by the storage.

Data structures in the library:
Heap with random access by node id, and the 10th* queries handled relatively efficiently with an O(k^2) algorithm.
Set (linked list)
KeyValueStore/DbMap - two kind of methods to access objects in the database: 
    get a reference of specific type, or get a typed map
ScopedKeyValueStore - a KeyValueStore which is a directory withing another store (a namespace)

Combining these we can use O(logn) time for most of the actions.
By using the namespaces the use of several SecureStorage instances is redundant (saves a few milis in startup)

### Testing Summary
TODO: Short summary describing the ways you chose to test your code.

We tested each of the layer and stubbed it's underlying layer, each class on its own.

### Difficulties
TODO: Please list any technological difficulties you had while working on this assignment, especially
with the tools used: Kotlin, JUnit, MockK, Gradle, and Guice.

### Feedback
TODO: Put any feedback you may have for this assignment here. This **will** be read by the course staff,
and may influence future assignments!