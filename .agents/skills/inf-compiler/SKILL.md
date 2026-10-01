---
name: inf-compiler
description: Use this skill for changes to the Inf compiler written in Java.
---

# Inf Compiler

* Make the smallest change that addresses the task.
* Use existing tests where possible.

## Compiler pipeline
Consider the compiler pipeline, including:
* lexing,
* parsing, 
* Parse → AST,
* AST → HIR raising,
* HIR → THIR raising (typed HIR, a "pseudo-step" inside HIR raising)
* THIR → MIR lowering,
* MIR -> LLVM lowering,
* Execution.

## Code style / formatting / idiomaticity

Avoid cloning arrays or lists, instead use `Collections.unmodifiableList` or `Collections.unmodifiableSet` to return immutable views of mutable collections.
Expect the programmer to not modify returned arrays/collections, rather than developing expensive safeguards against it.

When developing a new feature, avoid setting default values that are too specific.
It is better to let a caller decide what the default value should be if nothing is found by the feature.

## Traversing AST, HIR, and MIR nodes
Never use custom logic for traversing AST, HIR or MIR nodes, always use the existing visitor pattern. `XyzVisitor` interface and node `#visit` method.

## Language references
See `*.inf` files in `src/test/resources/inf` for examples of the Inf language.
* Examples of **valid** Inf code in `src/test/resources/inf/valid*`,
* Examples of **invalid** Inf code in `src/test/resources/inf/invalid*`,
* Examples of **theoretical** Inf code in `src/test/resources/inf/theoretical*`.
  * These are examples of what the language could look like, and can be used as input for ideas, but not necessarily as reference for how to solve something.
