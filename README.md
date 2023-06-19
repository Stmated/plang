
# Plang

Example code snippets in `/src/test/resources/plang`

Experimental language for learning purposes

Features/ideas that will be attempted:
* As few different syntax for things as possible
* As few "magic" keywords as possible
* Quick to type
* Flexible type system
* Memory safety (by maybe implementing borrow-checking)
  * With Allocators as in Zig
* Thread safety (by maybe implementing actors)
  * No locks, no synchronizations
* Composition over inheritance
* No nulls
* Meta-programming, able to reference your own code in other parts of the code
* No-overhead context variables that can be moved through unknown pipeline
  * Can also be used as language-level dependency injection
* No thrown exceptions, but forced and natural error handling
* Simple data structures that can be easily copied
* Types are structurally compared
  * ```val x = struct {a: uint8}``` == ```val y = struct {a: uint8}```
