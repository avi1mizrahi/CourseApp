# CourseApp: Assignment 1

## Authors
* Alon Tavor, 307915561
* Avi Mizrahi, 200668945

### Previous assignment
200668945-307915561

## Notes

### Implementation Summary
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
We tested each of the layer and stubbed it's underlying layer, each class on its own.

### Difficulties
Trying to encapsulate the Storage access is challenging. 

### Feedback
The Heap implementation fills quite unnecessary and time consuming.
Although there are implementations out there, it should be modifies and tested, and it takes time. 
