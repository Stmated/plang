---
name: inf-compiler
description: Use this skill for changes to the Inf compiler written in Java.
---

# Inf Compiler

Consider the compiler pipeline, including:
* lexing,
* parsing, 
* Parse → AST,
* AST → HIR raising,
* HIR → THIR raising (typed HIR, a "pseudo-step" inside HIR raising)
* THIR → MIR lowering,
* MIR -> LLVM lowering,
* Execution.

Make the smallest change that addresses the task.
Use existing tests where possible.

Avoid cloning arrays or lists, instead use `Collections.unmodifiableList` or `Collections.unmodifiableSet` to return immutable views of mutable collections.
Expect the programmer to not modify returned arrays/collections, rather than developing expensive safeguards against it.

Never use custom logic for traversing AST, HIR or MIR nodes, always use the existing visitor pattern. `XyzVisitor` interface and node `#visit` method.

When developing a new feature, avoid setting default values that are too specific.
It is better to let a caller decide what the default value should be if nothing is found by the feature.
