# CourseApp: Assignment 0

## Authors
* Alon Tavor, 307915561
* Avi Mizrahi, 200668945

## Notes

### Implementation Summary
We choose to build the stack with 3 layers:
1. storage - a persistent storage with a raw interface (as you supplied)
2. KeyValueStore - higher level persistent map
3. CourseApp - the business logic

CourseApp stores persistent data by URIs, 
for that we created a proxy classes (Token/User) so we can query the storage easily.

### Testing Summary
we tested each of the layer and stubbed it's underlying layer.

### Difficulties
the most difficult issue is to work with string encodings and escaping, no one deserve that :)

### Feedback
It will be nice to leave the bits and bytes behind