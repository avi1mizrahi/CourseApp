# CourseApp: Assignment 2

## Authors
* Alon Tavor, 307915561
* Avi Mizrahi, 200668945

### Previous assignment
200668945-307915561

## Notes

### Implementation Summary
In this assignment we tackle the Futures problem as follows:
1. We've created an adapter storage object, which translates all future API to the previous API
2. We converted the tests to work with the new API, so we can keep developing with working tests
    - we try to defer join as much as we can, in order to experience the functional writing
3. Implemented the messaging API
4. Optimizations: we looked for reads that can be parallelized, 
   and run them async and propagate that Future 

Our software stack built with 3 layers:
1. SecureStorage - a persistent storage with a raw interface (as you supplied)
2. AsyncStorageAdapter - make the storage sync
3. Library - Persistent Data Structures
4. CourseAppImpl - the business logic. Uses DataTypeProxies - represents entities backed by the storage.

Data structures in the library:
Heap with random access by node id, and the 10th* queries handled relatively efficiently with an O(k^2) algorithm.
Set (linked list)
KeyValueStore/DbMap - two kind of methods to access objects in the database: 
    get a reference of specific type, or get a typed map
ScopedKeyValueStore - a KeyValueStore which is a directory withing another store (a namespace)

Combining these we can use O(logn) time for most of the actions.
By using the namespaces the use of several SecureStorage instances is redundant (saves a few milis in startup)

### Testing Summary
We tested each of the layer and stubbed it's underlying layer, each class on its own.

### Difficulties
dokka integration into gradle took sometime, although at the end it is a few lines.

### Feedback
