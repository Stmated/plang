
# PASSIVELY ACTIVE
* Hold off on the whole interpreter/Comptime stuff for now, until the whole compiler has been bootstrapped into its own language (so can reuse compiler code)

# TODO

## Must fix to get things working again!

## Next
### Universal function calls
* Identify source forms that could be universal function calls.
* Preserve potential call sites in THIR.
* Collect callable declarations for call-site lookup.
* Define the lookup order for potential calls.
* Resolve unambiguous universal function calls.
* Retain unresolved calls until later resolution passes.
* Report calls needing disambiguation syntax.
* Test resolved, unresolved, and ambiguous calls.

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

* Redo the lexer and parser after the new ideas for the language:
  - Almost everything is a Tuple
  - Everything is an expression
  - As much as possible is "Label" (type declarations, function return types, tuple positional naming, etc)
  - Everything(?) is first-class
  - All keywords are user-declared (such as "var", "val" and "const" is a Comptime function that alters the type)
  - Universal Function Call Syntax
  - Prefix, infix and postfix function call syntax (with all operators being a function (which in turn are probably inlined to native code))
  - Make "var" and "val" optional, and make "val" default

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
