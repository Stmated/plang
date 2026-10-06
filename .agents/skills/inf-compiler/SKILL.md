---
name: inf-compiler
description: Use this skill for changes to the Inf compiler written in Java.
---

# Inf Compiler

## Workflow

* Investigate and plan first. Summarize the plan and ask for confirmation before implementing.
* Always ask clarifying questions rather than making assumptions about the task.
* Ask one narrow, specific question at a time. Do not bundle related decisions into a single yes/no question.
* Make the smallest change that addresses the task.
* Use existing tests where possible.
* Never install external tools such as Maven. If code cannot be executed, investigate the existing tool setup. See the `java-testing` skill for tool discovery and test execution.

## Compiler pipeline

Put changes in the appropriate stage:

1. Lexing.
2. Parsing → AST (Abstract Syntax Tree).
3. AST → HIR (High-Level Intermediate Representation) raising.
4. HIR → THIR (Typed High-Level Intermediate Representation), a "pseudo-step" inside HIR raising.
5. THIR → MIR (Middle-level Intermediate Representation) lowering.
6. MIR → LLVM lowering.
7. Execution.

### AST

Keep the AST abstract and agnostic. It groups a lexed stream into a tree describing code structure; logical groups and aggregations belong in HIR.

### HIR and THIR

HIR represents logical structures such as predicates, loops, and lambda functions. HIR and THIR currently use the same tree node classes; THIR is the typed form of HIR.

## Feature implementation

* Prefer an isolated transformer/pass class over adding feature logic to a main lowerer/raiser. Each pass should perform a limited job over tree nodes.
* Always use existing `XyzVisitor` interfaces and node `#visit` methods to traverse AST, HIR, and MIR nodes. Never write custom traversal logic.
* For a new language feature that needs explaining, create a `*.md` file in `docs\`. Use the `docs-writing` skill.

In the project the word `Ty` is used for its internal representation of a type, to not clash with the Java `Type` class.

## Code style

* Avoid cloning arrays or lists as defensive safeguards. Expect callers not to modify returned arrays/collections.
* For lists and sets, use `Collections.unmodifiableList` or `Collections.unmodifiableSet` if you must protect from modifications. Do not clone them.
* Avoid overly specific default values for new features. Let the caller decide the default when nothing is found.
* For enum category checks, prefer a descriptive boolean helper method, such as `public boolean isXorY() { return this == X || this == Y; }`.
* Prefer `String.formatted()` over string concatenation such as `"Hello, " + name`.
* Prefer Lombok annotations such as `@Data`, `@Value`, or `@UtilityClass` to reduce boilerplate.
* Prefer `final` for parameters, fields, and local variables where possible.
* Never nest ternary expressions. Use explicit if-conditions instead.

Prefer `ArrayUtils` (`src\main\java\org\inf\util\ArrayUtils.java`) for array operations. For example:

```java
ArrayUtils.none(items, TheClass::isTrue)
```

Prefer this over `Arrays.stream(items).noneMatch(it -> isTrue(it))`.

## Comments and complex conditionals

* Prefer Markdown documentation comments (`///`) with backticks for code rather than `{@code some.code()}`.
* When adding a function, comment a parameter's purpose only if its name does not make the intent clear.
* For complex conditionals, such as those combining three or more boolean conditions, consider either:
  * A short comment inside the branch explaining what the current state means.
  * A descriptively named helper function, only if it would not require many arguments.

## Language references

See `*.inf` files in `src\test\resources\inf`:
* `valid*`: examples of valid Inf code.
* `invalid*`: examples of invalid Inf code.
* `theoretical*`: possible language designs for inspiration, not references for implemented behavior.
