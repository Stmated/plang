# PASSIVELY ACTIVE

* Hold off on the whole interpreter/Comptime stuff for now, until the whole compiler has been bootstrapped into its own language (so can reuse compiler code)

# TODO

## Must fix to get things working again!

## Next

### Calling functions with tuples (and structs) (possibly unpacking, exploding)

* Positional tuple calls depend on Tuple 1-4 above; named tuple expansion additionally needs the named-tuple items. Do not make every deferred tuple feature a
  prerequisite. Investigation stopped before implementation planning beyond the findings below.
* Current calls share compact `Hir.TupleEntry`/`Hir.Tuple` containers with tuple values. Explicit calls extract only their outer argument list, preserving a nested tuple as
  one argument. Tuple spreading remains unsupported.
* Recommendation, not yet decided: explicit, shallow spread (`f(...args)` / `f ...args`). Keep `f(args)` as one argument; automatic unpacking makes tuple-taking
  functions ambiguous and must not change grouping based on a signature. Manual element arguments remain an alternative once tuple access works.
* `Ast.Spread` and the `...` token already exist, but ordinary expression raising does not support spread and implicit-call grouping rejects it as an argument
  start. Define operand boundaries and comma ownership before extending both call forms; preserve parameter-vararg syntax.
* Struct types, field access, and named-argument matching already exist. MIR call lowering matches labels against parameter names and rejects unknown,
  duplicate, missing, and excess arguments. Complete optional/default-argument support is not a prerequisite.
* On resuming, define positional versus named tuple expansion, struct fields matched by name, mixed explicit/spread arguments, collisions, extra fields, and
  vararg interaction. Start with statically known shapes, not runtime-length array expansion.
* Preserve source evaluation order, evaluate each spread operand once, and specify when its fields are read relative to later arguments. Reordering named
  arguments into parameter order must not reorder side effects.
* `service.call ...args` fits existing member-call syntax. `service call ...args` is not equivalent: adjacency nests calls to the right. Treat that spelling as
  a separate syntax/UFC decision, not merely omitted parentheses.
* The "arguments" to a function call is just a tuple (or *should* be, at least).
    * So it should be possible to call a function with a tuple, and have it unpacked into the function's parameters.
    * Either automatically unpacked, or with a special syntax like `const result = service.call(...args)` where `args` is a tuple.
        * Help me decide what is most viable. But I do want the syntax to feel as clean/hackable as possible, to make it easy to write.
    * A struct could be seen as a tuple of named values, so it should be possible to call a function with a struct, and have it unpacked into the function's
      parameters.
        * Same restrictions should apply as for the tuples, whatever is decided.
        * The argument matching should be based on the names and not position.
    * I need to know of caveats and problems and considerations to make this a good idea/feature
    * Of course a syntax like `const result = service call ...args` should work, since parenthesis are optional.
    * If you have alternative ideas of how and when to destructure manually or automatically, then tell me.

### Unification of "struct" and "tuple"
* A tuple should in essence just be a short-form of a struct declaration
* They should be convertable to and from each other if they exactly match
* It should be possible to "explode"/"unpack" a struct as well to a function call and other locations, just like with a tuple

### Unification of Array and Tuple
* An array of unbounded size and a tuple with only positional rest parameters should be equivalent
* Difference then being that a Tuple type should be able to be defined as `(bool, uint8...)`
* Investigate possibility/feasibility of supporting something like `(bool..., uint8..., bool)`
  * Which means it can be any number of booleans, then any number of uint8, then one last bool
  * This feature is absolutely not required and should only be supported if it can be "easily" done and would be considered idiomatic to the rest of the language

### Unification of "named arguments"
* Function calls now use `=` for named arguments: `fn(a = 10)`. The old `:` spelling is rejected (Tuple 8).
    * To be able to assign a value inside a nested expression, it must be done using new `:=` syntax like `fn(a = foo := 10)`
        * The main difference to `=` is that `=` returns `void` but `:=` returns RHS value.
* Give feedback on viability of this change, if it is a good idea or not.

### Optional arguments, default values for parameters, named arguments

### Updated test cases, thorough rework of how tests are done
* Should use more s-expression printing
* Should use more test cases found in files
* Should use better ways of storing expected output for a snapshot test
* Should have ways of giving a kind of test matrix for the different test files
* Global/common test value sources, such as one that gives all optimization levels

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
    * It could be as "ugly" as simply not supporting it in the syntax for the function declaration, and instead forcing an alias as `const eats = Person::eats`
      in the file you need it available.
        * The above alias is different from `const eats = person.eats` which is a bound method reference, and the former is a function that takes a Person as
          first argument (since it is a static reference to the function inside the struct).
* Report calls needing disambiguation syntax.
* Test resolved, unresolved, and ambiguous calls.
* Make sure it is somewhat expected that a function inside a struct is not considered "in an object" but rather "is a function with the first argument being an
  instance of the struct".

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
* Implemented as comptime functions like "val", "var", et cetera. Takes an expression and return a modified expression with the visibility/access control
  applied.
* Needs investigation for how to best be represented, but likely as a modifier on the type of the expression, just like "val" and "var" are modifiers on the
  type of the expression.

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
math = import("math", version = "2.0.1")

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
