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
* Add discovered needs to the `TODO.md` file, or update changed requirements from found issues on related features.

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

## implementation rules

* Prefer an isolated transformer/pass class over adding feature logic to a main lowerer/raiser. Each pass should perform a limited job over tree nodes.
* Prefer an isolated transformer/pass class over adding code to another transformer/pass that is closely related but deals with another area of responsibility. For example a "Handle spread visitor" should not have "Handle return type inference" code inside of it.
* Always use existing `XyzVisitor` interfaces and node `#visit` methods to traverse AST, HIR, and MIR nodes. Never write custom traversal logic.
* For a new language feature that needs explaining, create a `*.md` file in `docs\`. Use the `docs-writing` skill.
* Do not recreate large type inference/investigation inside new THIR passes, use the resolution of previous passes and fill in the blanks. If refactoring is required to split some passes into more granular passes to be able to resolve them in order, and with narrowed responsibilities, then that is recommended. But ask first.
* You must always run all tests in the project to validate against any regressions, before counting things as done.
* Do not make validation/assertion of nodes a focus when implementing a functionality inside a pass.
  * Focus on solving the narrow scope of the pass, and leave any requirement of validation to a separate pass.
  * It is still allowed to fail early if something is obviously wrong, but do not spend resources heavily validating early something that will fail later.
* It is better to separate a large feature into smaller, localized steps to be aded to `todo.md`. If a feature grows large, ask to split it up.
* Never introduce a new expression node class, or a field to an existing node, without being extremely explicit, and explaining why it is fully required.

In the project the word `Ty` is used for its internal representation of a type, to not clash with the Java `Type` class.

### Needing result of multiple passes
If you are unit testing a certain pass, then test that pass's logic specifically.
If a certain pass need the result of other passes, then it is strongly preferred to do the whole `Inf.[...]` methods that use the whole compile pass.
So avoid code like:
```java
HirTyCommonVisitorPass.resolveAvailableTypes(root);
HirFunctionContextualTypingVisitorPass.pass(root);
HirTyCommonVisitorPass.resolveAvailableTypes(root);
HirTupleContextVisitorPass.pass(root);
```
and instead just do:
```java
final var root = Inf.codeToThir(root);
```

It is however allowed to manually call two different THIR passes, if the purpose is to test if two passes are specifically compatible with each other.

## Code style

* Do not read/follow checkstyle file.
* Avoid cloning arrays or lists as defensive safeguards. Expect callers not to modify returned arrays/collections.
* For lists and sets, use `Collections.unmodifiableList` or `Collections.unmodifiableSet` if you must protect from modifications. Do not clone them.
* Avoid overly specific default values for new features. Let the caller decide the default when nothing is found.
* For enum category checks, prefer a descriptive boolean helper method, such as `public boolean isXorY() { return this == X || this == Y; }`.
* Prefer `String.formatted()` over string concatenation such as `"Hello, " + name`.
* Prefer Lombok annotations such as `@Data`, `@Value`, or `@UtilityClass` to reduce boilerplate.
* Prefer `final` for parameters, fields, and local variables where possible.
* Never nest ternary expressions. Use explicit if-conditions instead.
* Avoid `.forEach` and instead use a regular for-loop.
* Never write heavy logic inside a constructor. Simple one-liners are allowed.
* Prefer word `expr` over `expression`.

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
