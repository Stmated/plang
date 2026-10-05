
# PASSIVELY ACTIVE
* Hold off on the whole interpreter/Comptime stuff for now, until the whole compiler has been bootstrapped into its own language (so can reuse compiler code)

# TODO

## Must fix to get things working again!

* Need a MIR -> S-Expression printer, and replace almost all test case with these comparisons instead!

## Next

### Tuple 1: Preserve grouping and tuple boundaries (done)
* First isolated item: retain group-owned `Ast.Comma` nodes through implicit-call grouping, including a trailing comma. No `Paren` metadata, new tuple AST hierarchy, or runtime changes.
* Agreed syntax: `(x)` groups, `(x,)` is a singleton tuple, `(x, y)` is a pair. Nested parentheses must preserve nested tuples. Keep existing `()` behavior; first-class empty tuples are deferred.
* Only commas belonging to the current group mark its tuple shape. Preserve existing implicit-call comma ownership, function parameter lists, and semicolon-separated sequences/loop headers.
* Done when AST grouping tests distinguish these forms without changing existing call grouping. Keep existing HIR behavior until Tuple 2 consumes the distinction.

### Tuple 2: Generalize tuple entries and type positional tuples (done)
* Depends on Tuple 1. `Hir.Tuple` contains ordered `Hir.TupleEntry` entries with optional labels. Tuple entries and call arguments have separate responsibilities; calls retain `Hir.Argument`. Labels are metadata, not variable references.
* Reuse/generalize `TyStruct` and `TyField` for aggregate layouts with unnamed slots. Preserve existing named struct behavior; sharing a layout representation does not decide future tuple/struct source compatibility.
* Raise positional tuple values separately from `Hir.Expressions`. Infer heterogeneous/nested slot types and resolve tuple annotations such as `(int, bool)` in bindings, parameters, and returns.
* Preserve flow typing: a non-returning element makes construction non-returning. Reject invalid element types and incompatible shapes explicitly; do not reinterpret an incompatible aggregate pointer.
* Agreed compatibility: matching recursive shapes and slot types, ignoring only numeric explicit-width metadata. No width/signedness conversions. Reject normally completing `void` slots; statically check later elements after a non-returning element without including them in executed-flow analysis.
* Extract only the outer argument list for a call: `f(x, y)` has two arguments; `f((x, y))` has one tuple argument. Keep `Hir.Argument[]` on calls for now.
* Done when HIR/THIR tests cover singleton/nested tuples, annotations, and call boundaries. Runtime tuple lowering remains unsupported until Tuple 3.

### Tuple 3: Construct positional tuples and read elements (done)
* Depends on Tuple 2. Agreed semantics: assignment/passing shares the tuple object, like existing structs/arrays; it does not copy slots.
* Reuse `Mir.NewStruct`, field places, and LLVM aggregate allocation/layout. Generalize names/helpers only where required; no parallel tuple instruction family or allocator redesign.
* Support construction and reads such as `val t = (10, true); t[0]`. Generalize existing access handling for tuple slots, with literal integer indices as the first slice; diagnose negative/out-of-range/non-integer indices and defer computed indices.
* Implemented consecutive bracket reads (`t[0][1]`) and all existing integer literal index spellings. Direct and compound tuple-slot writes are explicitly rejected.
* Evaluate elements left-to-right exactly once, including nested tuples and non-returning elements. Do not evaluate later elements after control flow exits.
* Done when MIR verification and LLVM execution cover singleton/heterogeneous/nested tuples and scalar results from element reads. Tuple element writes remain deferred.

### Tuple 4: Store, pass, and return tuple references (done)
* Depends on Tuple 3. Cover tuple bindings/reassignment, tuples stored in existing aggregates, and tuple-valued function parameters and explicit/implicit returns.
* Preserve the shared object across these boundaries. Exercise functions returning newly constructed tuples to ensure storage survives the callee.
* Start with matching slot types and shapes. Verify ordinary and parenthesis-free calls pass a tuple as one argument, including lifted functions; no spreading or call-representation rewrite.
* Implemented direct indexing of tuple-valued struct fields, array types inside tuple annotations, and consistent lifted-function binding types. Return annotations do not become runtime captures.
* Done when execution tests cover these compositions, nested element reads, evaluation order, and rejection of arity/type mismatches. Observe returned tuples inside Inf and return scalars to the test harness; host tuple marshalling is separate.

### Tuple 5: Contextual element typing and conversions (done)
* Depends on Tuple 4; not a prerequisite for calling functions with already-compatible positional tuples.
* Separate literal construction under an expected tuple type from conversion of an existing tuple object. This is needed for examples returning `(1, 2)` as `(uint8, uint8)`.
* Agreed: unsuffixed integer literals adopt an expected integer slot type only when their value fits. Explicitly typed literals retain their source type; other integer elements allow only lossless widening. Other slot types remain exact-match.
* Existing tuple objects, including nested references, must already match. No rebuilding, conversion allocations, or incompatible-layout pointer casts; explicit fresh construction from element reads can widen.
* Implemented tuple contexts in bindings/reassignment, arguments, and explicit/implicit returns, including final block expressions and conditional branches. Struct-field and array-element contexts remain exact-match; no general type-inference rewrite.
* Covered nested constructions, rejection of unsafe conversions, shared unchanged references, left-to-right exactly-once evaluation, and non-returning flow. See `docs/tuple-contextual-typing.md`.

### Tuple 6: Named and mixed tuple syntax and access
* Depends on Tuple 4. Extend the same entries/layout, not a separate named-tuple node family.
* Before implementation, confirm value labels (`p1 = 1`) versus type labels (`p1: uint8`), singleton named tuples, mixed-entry rules, and duplicate labels.
* Scope this item to construction, inferred/declared shapes that already match, and named/positional reads. A tuple label must not assign or resolve a surrounding variable.
* Done when raising and execution cover named entries without changing ordinary assignment, struct initialization, or existing named calls.

### Tuple 7: Compatibility between positional and named tuples
* Depends on Tuple 6 and, if element conversions are needed, Tuple 5.
* Preserve the requested examples `fn4((1, 2))` and `fn4((p1 = 1, p2 = 2))` for a parameter typed `(p1: uint8, p2: uint8)`.
* Before implementation, decide label identity, positional-to-named matching, named-field reordering, mixed matching, and tuple/struct compatibility. Account for shared references when a conversion changes layout.
* Keep these rules separate from function argument spreading. Cover missing/extra/duplicate labels and source-order evaluation independently of destination order.

### Tuple 8: Unify the call argument container
* Depends on Tuple 4; coordinate with Tuple 6 for labels. Explicitly deferred from the initial tuple work.
* Replace `Hir.Argument[]` on calls with the shared tuple container, preserving existing binding/vararg behavior and closure-capture insertion.
* This is representation unification, not implicit unpacking: `f(t)` still has one argument. Do not allocate a runtime tuple merely to lower an ordinary argument list.
* Keep spreading in the separate tuple-call feature below. Cover ordinary, named, implicit, and lifted calls before changing their semantics.

### First-class tuple values and types (prerequisite for tuple calls)
* Split into the isolated items above; implement one item per task. Items 1-4 provide the initial positional tuple feature. Items 5-8 are separate follow-ups, not one combined implementation.
* Agreed: positional tuples first, comma-based singleton syntax, shared aggregate references, and call-container unification later. Named/mixed tuples use the same building blocks.
* Language goal: as few constructs as possible, with generic nodes expressing advanced concepts; a function call can ultimately be viewed as a function reference plus an argument tuple.
* Deferred decisions/work: first-class empty tuples and their relation to `void`, tuple element mutation, computed indices, destructuring, tuple operators, and value expressions used as types (`(1, 2)` in `fn2`). None is required for the initial positional feature.
* AST/HIR preserve comma-based boundaries and THIR types positional tuples. MIR/LLVM support positional construction, literal-index reads, aggregate storage, and shared references across bindings and function calls/returns; named tuple typing remains unsupported. `valid_parse/valid_tuple.inf` is syntax coverage, not execution coverage.
* Reuse the existing AST grouping, HIR/THIR raising, MIR verifier/lowering, and LLVM execution tests for each item. No interpreter/comptime work, ownership system, general UFC work, or new testing framework.

### Better S-expression toString-ifier
One which instead uses reflection, so it does not need big and unruly visitors with lots of manual adding of fields.

### Calling functions with tuples (and structs) (possibly unpacking, exploding)
* Positional tuple calls depend on Tuple 1-4 above; named tuple expansion additionally needs the named-tuple items. Do not make every deferred tuple feature a prerequisite. Investigation stopped before implementation planning beyond the findings below.
* Current calls use `Hir.Argument[]`, separately from `Hir.TupleEntry[]`. Explicit calls extract only their outer argument list, preserving a nested tuple as one argument. Tuple spreading remains unsupported.
* Recommendation, not yet decided: explicit, shallow spread (`f(...args)` / `f ...args`). Keep `f(args)` as one argument; automatic unpacking makes tuple-taking functions ambiguous and must not change grouping based on a signature. Manual element arguments remain an alternative once tuple access works.
* `Ast.Spread` and the `...` token already exist, but ordinary expression raising does not support spread and implicit-call grouping rejects it as an argument start. Define operand boundaries and comma ownership before extending both call forms; preserve parameter-vararg syntax.
* Struct types, field access, and named-argument matching already exist. MIR call lowering matches labels against parameter names and rejects unknown, duplicate, missing, and excess arguments. Complete optional/default-argument support is not a prerequisite.
* On resuming, define positional versus named tuple expansion, struct fields matched by name, mixed explicit/spread arguments, collisions, extra fields, and vararg interaction. Start with statically known shapes, not runtime-length array expansion.
* Preserve source evaluation order, evaluate each spread operand once, and specify when its fields are read relative to later arguments. Reordering named arguments into parameter order must not reorder side effects.
* `service.call ...args` fits existing member-call syntax. `service call ...args` is not equivalent: adjacency nests calls to the right. Treat that spelling as a separate syntax/UFC decision, not merely omitted parentheses.
* The "arguments" to a function call is just a tuple (or *should* be, at least).
  * So it should be possible to call a function with a tuple, and have it unpacked into the function's parameters.
  * Either automatically unpacked, or with a special syntax like `const result = service.call(...args)` where `args` is a tuple.
    * Help me decide what is most viable. But I do want the syntax to feel as clean/hackable as possible, to make it easy to write.
  * A struct could be seen as a tuple of named values, so it should be possible to call a function with a struct, and have it unpacked into the function's parameters.
    * Same restrictions should apply as for the tuples, whatever is decided.
    * The argument matching should be based on the names and not position.
  * I need to know of caveats and problems and considerations to make this a good idea/feature
  * Of course a syntax like `const result = service call ...args` should work, since parenthesis are optional.
  * If you have alternative ideas of how and when to destructure manually or automatically, then tell me.

### Optional arguments, default values for parameters, named arguments

### Partial functions, for currying/composition

### Allocators, to be able to specify stack vs heap, and if something is by-reference or by-value


### Universal function calls
* Identify source forms that could be universal function calls.
* Preserve potential call sites in THIR.
* Collect callable declarations for call-site lookup.
* Define the lookup order for potential calls.
* Resolve unambiguous universal function calls.
* Retain unresolved calls until later resolution passes.
* Need a way to allow the global export of a function for full UFC.
  * For example `person::eats(cake)` where `eats` is a function in Person struct, but if it is exported/visible, one could call it with `eats(person, cake)`
  * This would clutter up the global namespace, and make IDE autocomplete a hassle if ALL functions could be called this way.
    * Instead if it is not exported/visible, then it is only callable as `Person::eats(person, cake)` or `person.eats(cake)`.
  * So need a way to specify for a function that it is visible.
  * It could be as "ugly" as simply not supporting it in the syntax for the function declaration, and instead forcing an alias as `const eats = Person::eats` in the file you need it available.
    * The above alias is different from `const eats = person.eats` which is a bound method reference, and the former is a function that takes a Person as first argument (since it is a static reference to the function inside the struct).
* Report calls needing disambiguation syntax.
* Test resolved, unresolved, and ambiguous calls.
* Make sure it is somewhat expected that a function inside a struct is not considered "in an object" but rather "is a function with the first argument being an instance of the struct".

```
Investigate and prepare to implement `Universal function calls`.
Ask me any clarifying questions before starting implementation.
If you have warnings or problems with the idea, then tell me.
I am well aware that the feature can make some syntax ambiguous
```

### Type usage
* Model copying, moving, referencing, and child mutation metadata for types.
* Needs investigation for how to best be represented, but likely as a modifier on the type of the expression, so the type contains its restrictions.
  * Then identity comparison of "is type A same as B" needs expansion, where an "immutable int" is same (or not same) as "mutable int" depending on context.
* Track usage settings during type checking.
* Reject invalid copy, move, reference, and mutation operations.
* Model immutable and non-copyable types.
* Use counts only for linear or bounded resources; arbitrary quotas have no clear value.

### THIR and comptime
* Define the first supported THIR evaluator expressions.
* Implement a THIR expression evaluator instead of using MIR.
* Test THIR expression evaluation.
* Define comptime function values and calls.
* Execute simple comptime functions during compilation.
* Expose expressions as comptime values.
* Let comptime functions return transformed expressions.
* Replace transformed expressions before runtime compilation.
* Test comptime expression transformations.

### Comptime type expression transformations
* Expose type usage settings to comptime expression transformations.

### Packages
* Define the minimal package model and package metadata.
  * A "package" should be a directory with a package file (`package.inf`).
  * A package should be a simple struct which implements trait `Package` that gives back common package-needed metadata like name, version.
  * This package struct is then what is exportable. It is up to it to decide what is exported.
    * Currently there is no support for visibility modifiers or access control, so all would be public. This is out of scope for now.
* Load a package into a compilation session.
* Make linked packages discoverable by the compilation session.
* Define package export declarations.
* Export symbols from a package.
* Define package import declarations.
* Resolve package imports against linked packages.
* Add a package resolver for external package references.

### Package imports
* Bind imported symbols in the importing package.
* Type-check calls to imported symbols.
* Compile calls to imported package code.
* Test package exports, imports, and cross-package calls.

### Core library and bindings
* Create a minimal core library package.
* Export its public symbols.
* Link the core library into test compilation sessions.
* Import the core library from test code.
* Call imported core-library functions from test code.

### Core library comptime functions instead of reserved keywords
* Implement comptime `val`, `var`, and `const` functions in the core library.
  * `val a = 10` where `val` is a function that takes a `Hir.Assignment` and returns a `Hir.Assignment` with immutable type for `a`.
  * `const b = 20` where `const` is a function that takes a `Hir.Assignment` and returns a `Hir.Assignment` with constant type for `b`.
* Use usage settings to implement immutable and mutable bindings.
* Remove reserved `val` and `var` keywords.
* Test core-library `val`, `var`, and `const` bindings.

### Type visibility and access control
* Able to specify visibility and access control for types, fields, and methods.
* Implemented as comptime functions like "val", "var", et cetera. Takes an expression and return a modified expression with the visibility/access control applied.
* Needs investigation for how to best be represented, but likely as a modifier on the type of the expression, just like "val" and "var" are modifiers on the type of the expression.

## Other

* Should `"val f = (t: (uint8, uint8)) => t; f({ (1, 2) })"` actually be valid? What does it actually MEAN/DO?

* Redo the lexer and parser after the new ideas for the language:
  - Almost everything is a Tuple
  - Everything is an expression
  - As much as possible is "Label" (type declarations, function return types, tuple positional naming, etc)
  - Everything(?) is first-class
  - All keywords are user-declared (such as "var", "val" and "const" is a Comptime function that alters the type)
  - Universal Function Call Syntax
  - Prefix, infix and postfix function call syntax (with all operators being a function (which in turn are probably inlined to native code))
  - Make "var" and "val" optional, and make "val" default

* Investigate if it works with a different syntax between "assign" and "label" for things like tuple/struct, etc.
  * Currently there is so disambiguity between `x = 10` and `x: int` and `x: 10` between structs and tuples and assignments and type labels.

* Line and columns saved to the tokens/nodes, for syntax debugging

* TokenToAstRaisingTest (and others) should be using snapshot testing instead, and compare the AST output to a snapshot file
  * Right now the test itself becomes too brittle, and it's hard to reason about the depth of the structure and all that.

* Investigate some way of upgrading "groups" of types where something similar changes for all of them.
  * For example that for one step/context of the code, some properties are not required, but in later code they are
  * There needs to be a way of making them compatible with each other, so that later stages are allowed for earlier stages
  * ie. the changes to the types need/should be more restrictive for the changed types -- but perhaps not needed to be declared? Checked at callsites?

* Structures should be able to say "this type is optional, and if it is not specified then it is the same value as this other property"
  * So that for `BlogPost` if "id" is not specified then it is same as "title"
  * Would be nice if this could be a computed property, where if it is not specified then it is calculated each time.
  * Should still be possible to set a value, which then makes it behave like a regular property again.

* Broaden/specify the definition of `PostfixExpression` to support things like `++` and `--`

* Higher Kinded Types (HKT), with inspiration from Haskell.
  * Being able to represent a `House<Person>` and a `Car<Person>` as `$T<Person>`

## Test cases

* Create tests that checks exact result of:
  * Result<(String, String), Error>
  * something<unit8>(2)

# Namespaces/packages/imports/structures
Each file is just a "struct" that is nested in its parent directories.
If multiple exported things in one file, then we need to figure out how to handle it.
* Special consideration that aggregates them?
* Always same aggregation even if just exports one struct? For consistency

## Packages
A package is just a file `package.toml` (or YAML, or both, have not decided yet).
In it, we describe the package and what it exports and how.
The package file should be able to be placed in any directory, and relates to all contained within.

## Imports
Imports should be like any other referenced "object" in the code, but fetched externally.
The import should in essence be a private "field" in the file's struct.
Imports should be able to be included anywhere, even inside a function.
Must be able to be different versions.
But must also be able to be "pinned" to a specific version by a package file.
Resolved by the comptime compiler, which will fetch the correct version and include it "in-place".

OR! Perhaps the "package file" is just another code file with name "package.inf"
Then the struct inside is required to implement a specific interface, which is then used to resolve things.

The import system should be highly flexible, where the basic components are reusable.
So there is one simple comptime function that fetches a file from a URI.
Then we can build abstractions on top of that, with different resolvers, repositories, versioning, caching, etc.
It should be possible to inspect this code and see what it does!

```ts
// Will get its version from a package file
math = import("math")

// Will be a specific version
math= import("math", version = "2.0.1")

// Can still get its version from a package file
math = import("math", version = ">2.0.1")
math = import("math", version = ">=2.0.1")
math = import("math", version = "2.0.1 - 2.1.9")

// Versions can be combined with logical operators.
// Perhaps useful for packages where the version is not known yet (it's a lib), but we want to support multiple.
// It will then be up to the user of the package to specify a version, hence one of them will be defined.
m2or3 = import("math", version = "2.0.1 || 3.0.5")
m2and3 = import("math", version = "2.0.1 && 3.0.5")

// The logical operator ones need to be imported with a specific structure.
const m2_1 = m2or3["2.0.1"];
const m2_2 = m2or3["2.0.x"];
const m3_1 = m2or3["3.0.5"];

// Somehow needs some kind of logic to know for certain that if one is defined, then the other isn't. Flow-control-typed.
const m2_3 = m2or3.earliest;
const m3_2 = m2or3.latest;

// OR, this is done by some other flow-control mechanism, like:
if (m2or3::version == "2.0.1") {
    // Now type of "m2or3" is known to be "2.0.1", and can be used as such.
} else if (m2or3::version == "3.0.5") {
    // Same thing but for "3.0.5"
}

// To support this there might be a need for some kind of "static interface" where we can say a struct must contain a static property like "version" here.

// To make things like `if (m2or3::version.startsWith("2"))` possible, we need to somehow make it obvious that `version` is a comptime static property.
// ie. it must be known at compile-time, and must be resolvable at compile-time, and any predicate it is involved in will be executed at comptime.
// This would mean that effectively the "if" is removed from the compiled code that is executed at runtime.
// Should this need some kind of "obvious" syntax like one of:
// * `if (@m2or3::version == @"3.0.5")`
// * `if (@(m2or3::version == "3.0.5"))`
// * `@if (m2or3::version.startsWith("3"))`
// Where perhaps all of them should be allowed... unless we can find inconsistencies/conflicts.
```
