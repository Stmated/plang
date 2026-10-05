---
name: inf-compiler
description: Use this skill for changes to the Inf compiler written in Java.
---

# Inf Compiler

* Make the smallest change that addresses the task.
* Use existing tests where possible.
* Always ask me clarifying questions rather than making assumptions about the task.
* Always attempt to investigate and plan first, then summarize the plan and ask for confirmation before implementing.

## Compiler pipeline
Consider the compiler pipeline, including:
* lexing,
* parsing, 
* Parse → AST (Abstract Syntax Tree),
* AST → HIR (High Intermediate Representation) raising,
* HIR → THIR raising (typed HIR, a "pseudo-step" inside HIR raising)
* THIR → MIR (Middle-level Intermediate Representation) lowering,
* MIR → LLVM lowering,
* Execution.

## Coding Instructions
It is important to put the changes in the correct compiler pipeline step.

It is important to prefer implementing a new transformer/pass class to make an isolated change for a feature, rather than adding logic to a main lowerer/raiser class.
The more isolated and separated a feature can be made, so it does its limited job in its own pass over a set of tree nodes, the better.

If the feature is a new language feature that needs explaining, then create a new `*.md` file in `/docs` to document it.
Be sure to use the `docs-writing` skill to write the documentation.

Strongly prefer asking several questions that are more narrow and specific, rather than listing many overarching related decisions/options and asking for a simple "yes"/"no" for all of them.
Rather than asking "should we do X and Y and Z", ask "should we do X like A or B?" and then "should we do Y like C or D?" and then "should we do Z like E or F?"

When adding a new function, if the parameter name is not obvious with its intent, then add a comment describing its purpose.
Do not add comments to all parameters, or add superfluous comments, but do it if it is not obvious what the parameter is for.

If a conditional is highly complex, for example considering 3 or more boolean algebra conditions, then consider either:
* Adding a very short comment inside the branch about what the current state means, or
* Refactor the conditional into a separate function with a descriptive name, and call that function in the conditional.
  * Only do this if the separate function would not need lots of arguments to be passed in.

In the project the word `Ty` is used for its internal representation of a type, to not clash with the Java `Type` class.

## Code style / formatting / idiomaticity

Avoid cloning arrays or lists, instead use `Collections.unmodifiableList` or `Collections.unmodifiableSet` to return immutable views of mutable collections.
Expect the programmer to not modify returned arrays/collections, rather than developing expensive safeguards against it.

When developing a new feature, avoid setting default values that are too specific.
It is better to let a caller decide what the default value should be if nothing is found by the feature.

For an enum, rather than having a helper method like `enum.isXorY()`, prefer adding a public boolean `this == Enum.X || this == Enum.Y` inside `Enum`.

Prefer modern Java comments in Markdown format, inside `///` comments.
Also prefer backtick code sections over `{@code some.code()}`.

Prefer using `String.formatted()` rather than String concatenation using `"Hello, " + name`.

Prefer using lombok annotations to decrease boilerplate, such as `@Data`, `@Value` or `@UtilityClass`.

## Traversing AST, HIR, and MIR nodes
Never use custom logic for traversing AST, HIR or MIR nodes, always use the existing visitor pattern. `XyzVisitor` interface and node `#visit` method.

## AST
The AST should be as abstract and agnostic as possible.
Its purpose is to group a lexed stream of words into a tree structure that explains the code structure rather than being an accurate representation of the Inf code.
It is rather up to the HIR stage to represent the code in its logical groups/aggregations.

## HIR
HIR and THIR are the same tree node classes (for now),
where the first is the High-Level Intermediate Representation, and second is Typed High-Level Intermediate Representation.

The HIR will represent things as the logical structures of something, such as a predicate, loop, or lambda function.

## Language references
See `*.inf` files in `src/test/resources/inf` for examples of the Inf language.
* Examples of **valid** Inf code in `src/test/resources/inf/valid*`,
* Examples of **invalid** Inf code in `src/test/resources/inf/invalid*`,
* Examples of **theoretical** Inf code in `src/test/resources/inf/theoretical*`.
  * These are examples of what the language could look like, and can be used as input for ideas, but not necessarily as reference for how to solve something.
