# Must TODO/fix to get things working again!

-- Currently none

# TODO

## Smarter incorporation of DEADEND/ty contexts

### Investigation and inspection helpers (done)

Canonical terminology and contracts are in [Inf type contexts](../../../../CONTEXT.md) and [HIR type contracts](../../../../docs/hir-type-contracts.md).

* Completion type: normal expression completion; `VOID` completes without a value, `DEADEND` does not complete.
* Resolved structural type: aggregate/selected type retained for typing, not proof that its runtime value or layout exists.
* Declared constraint: source annotations and their explicit `INFER` placeholders.
* Expected type: contextual input; `Tuple.contextualType` determines destination layout and is not a duplicate result.
* Binding type: resolved declaration/parameter value, separate from their `VOID` completion.
* Exit payload type: a reachable return/break operand, separate from the transfer's `DEADEND` completion. Body results combine reachable return payloads with normal completion.
* Migrated production DEADEND comparisons to `Ty.isDeadend` and null-safe `Tys.isDeadend`. Initial representation inspection helpers are superseded by the explicit typed projections below.
* Retained `Ty.INFER`: null commonly means unresolved pass state or unavailable information, whereas annotation containers require a non-null inference placeholder.
* No new expression fields: forwarding expressions already expose their child type; new caches would add synchronization without resolving a context distinction.

### Completion/structural wrappers (done)

* `TyCompletion` replaces duplicate tuple/array/range/indexed storage; existing shape accessors project from `ty`. Declared constraints, expected layouts, and binding types stay separate.
* Explicit `completionTy()` and `structuralTy()` projections use `Tys.withCompletion` to flatten and normalize pairs without losing widths, flags, or unresolved state. Scaled-number equality includes flags so interning preserves them.
* Explicit projections avoid a context enum that conflates expression queries such as body results with stored type information.
* Transfers have no structural value. Retained tuple metadata does not manufacture available layouts; callable, member, spread, and indexing queries preserve their context-specific rules.
* Completion consumers and backend boundaries project explicitly. Branch unions discard transferring arms: `int | DEADEND | VOID` completes with `int | VOID`; all-transferring branches remain `DEADEND`.

### Explicit typed projections (done)

* `Ty.asCompletion(Class)` and `Ty.asStructural(Class)` select a view and inspect its kind without exposing completion wrappers. Null-safe `Tys` equivalents return null for unavailable or nonmatching projections.
* Removed public context-free `is/as/ifIs`. Use typed projections for kind lookup and `completionTy()/structuralTy()` for merging, comparison, and switches. Keep `isDeadend()` for completion flow.
* Structural inspection does not establish layout availability or collect body results; those expression-level queries retain their existing policies.

### Wrapper query simplification (deferred)

* Consider reducing callable/layout/indexing visitor special cases where projections now supply the needed information. Keep syntactic result selection and layout availability distinct from stored structural metadata.

### Layout metadata versus available layouts (deferred investigation)

* Purpose: make it harder to accidentally use `asStructural(TyStruct.class)` as permission to construct, access, or spread a tuple. This is an API clarity follow-up, not a known runtime regression.
* Scope: audit layout reads in `HirStructLayoutVisitor`, `HirIndexingTypeVisitor`, `HirSpreadShape`, and their construction/member/indexing callers. Metadata-only reads may use typed projections; available-layout reads must use the existing expression-level queries.
* First evaluate clearer query naming/documentation. Only introduce a query-result distinction for known layout, no layout, and unresolved layout if callers genuinely need those separate states; do not add a `Ty` reachability flag or expression cache.
* Preserve the contrast: `({ return true; }, 7)` retains tuple field metadata but supplies no layout or spread slots. A call such as `make({ return true; })` may supply a known return layout for diagnostics despite noncompletion; a blanket DEADEND layout rejection would be wrong.
* Completion criterion: layout-dependent callers consistently use the layout queries; source-driven cases cover both contrasts, including no allocation or field loads after a transfer. Do not reconstruct tuple layouts from entries.

### Shared function body-result validation (deferred investigation)

* Purpose: distinguish the actual combined body result from completion, callable diagnostics, and declared return constraints. Neither `asCompletion` nor `asStructural` can collect reachable return payloads.
* Scope: compare `HirTupleValidationVisitorPass.visitFunction`'s body-completion check and `HirFunctionValidationVisitorPass.visitFunction`'s callable/implicit-return check with `HirArrayValidationPass`'s existing `HirBodyResultTyping.resolve(body)` check.
* Decide whether actual-result checks should consistently use `HirBodyResultTyping.resolve`, while keeping per-source return/implicit-result diagnostics separate. Do not replace callable diagnostics wholesale, drop validation of unreachable source expressions, or infer from a constrained signature's return type.
* Preserve examples: `() => { return 7; true }` has DEADEND body completion but result `int`; `(flag: bool) => { if (flag) { return 7; }; true }` has result `int | bool`. Nested function returns and loop breaks remain scoped; unreachable tails contribute no result.
* Completion criterion: every check advertised as comparing the actual function result uses the shared execution-aware collector, and source-driven cases cover tuple/function-valued returns mixed with fallthrough. Reuse existing visitors; do not add a body-result projection to `Ty` or a duplicate result cache.

### Additional coverage (deferred)

* Extend source-driven completion/structural projection coverage for diverging tuples, arrays, ranges, calls, and member/index accesses.
* Cover wrapper normalization, nesting, comparison/interning, and backend projection without losing explicit type properties.
* Cover mixed `int`/transfer/`VOID` branches and ensure reachable return/break payloads remain separate from structural information.
* Cover changing expected tuple layouts across repeated typing, and unresolved fields versus explicit `INFER` constraints.

### Original wrapper proposal

Currently for most elements that can give back a result type, it has two different `Ty`,
one for what it could give as runtime, and one which is what the expressions could potentially give if they comleted.

Currently it seems like the only difference between for example a `Tuple#ty` and `Tuple#resolvedTy`,
is that the first might be `Ty.DEADEND` and the second is not (instead it is the structural equivalence that *could* be intended).

I feel like there could be great gains in simplicity if the `Ty` interface was changed.
For example, instead of having `Ty.DEADEND` then there is a `TyDeadend` class.
It wraps another `Ty` and returns `true` for a new `isDeadend()` method.
Then to make all the other `ty instanceof TyUnion` or similar work properly, there would instead in `Ty` be default methods:
* `<T extends Ty> boolean is(Class<T> c)`
* `<T extends Ty> T as(Class<T> c)` (can give null)
* `<T extends Ty, R> R ifIs(Class<T> c, Function<T, R> func)`
* maybe something else if there is a noticeable need for something else.

They would then unwrap the `Ty` if it is a `TyDeadend`.

This would make it easier to store the `ty` for an expression since we wouldn't need the pretty much duplicate types on many expressions.

The `Ty` could then also have other wrappers to signify other scenarios/contexts for the Ty, if it can differ depending on it.
Need to separate types that are for:
* Runtime (containing DEADEND, etc)
* Dev intent
* Resolved inference
* et cetera. I need your help detecting different "contexts" for types, for example for a tuple what the differences between
  fields `ty`, `resolvedTy` and `contextualType` actually is and what they are for/what they signify.

I want better/idiomatic/accurate termionology for these different contexts.
So that I can accurately have default interface methods on `Ty` for things like:
* `asDevTypingTy()`
* `asRuntimeTy()`
* `asResultTy()`
* et cetera

Where none of these names are guidance, they are only examples. I want you to investigate what different contexts there are.
For example the different type contexts when fetching the ty of conditional that has three different branches of: `int | DEADEND | void`
Or whatever else kind of mixes there might be.

This would mean there needs to be some simple normalization functions, so that if someone creates a theoretical:
`final var newTy = Ty.typedVsRuntime(typedTy, runtimeTy)`
Then if they are both the same, it would just return one of them, instead of creating a new `TyRuntimeWrapper`.
Take heed that the names are only theoretical, I want you to investigate better names.

I want this to be done as limited/narrow as possible, even though I understand it would change a whole lot of code.
So focus first on making things just *functional*, find areas where code could be made easier because of it, and add it as deferred items in todo.

Do not add lots of new test cases that test this new functionality, instead focus on getting current existing test cases to pass.
Add new items to TODO for deferred test cases that are needed to get proper coverage.

If you deem it worthwhile, you can split this feature into different milestones in this TODO file.
For example, perhaps it is a good idea to move `ty instanceof TyUnion` and similar code (like those previously mentioned) over to use a helper function,
before actually making any changes to any structures/logic.
Just as one example, this would also conclude an `isDeadend()` method, rather than doing `ty == Ty.DEADEND` in code.

If it would help, it might be a good idea to before that make it easier to expect ty to never be null, so `ty.isDeadend()` won't crash.
Or in cases where it could be null, it could use `Tys#isDeadend(Ty)` which `Ty#isDeadend` would delegate to anyway.
Perhaps it would be safe to say that a null `ty` is what means `INFER` and that `Ty.INFER` can be deleted. Investigate this as part of it.

Part of this is also to investigate if it would help to add a specific `ty` field to some `Hir.Expression` sub-types that currently do not have them.
If that would help in how easy it is to investigate a result/return/runtime type from a certain node.
It is NOT worth it to add an additional `ty` to an expression if all it would mean is that it copies it from its `this.expression.ty()`

All this implies that you should decide if it is a good idea, and only implement it if it makes the compiler EASIER to understand/write.
If you have similar ideas other than this, that would simplify the `Ty` handling, then propose those instead.

## Better terminology for types (done)
The context glossary and contracts are documented under "Smarter incorporation of DEADEND/ty contexts".
Further context-specific resolver design remains deferred under "Different resolvers"; naming a context does not make its type interchangeable with completion or transfer payloads.

## Better separation of "ty()"
The new `DynamicTy` is still not a good ideas -- since it allows us to create types which are not actually syntactically supported
  We can create a `DynamicTy` that contains a `ty` that can only be created through code
  This create problems in test cases where we create situations that are not actually yet needed to be supported, or cases that actually cannot happen.
  Need to run an investigation to find all locations where a `DynamicTy` is created as a workaround, and then work to remove all of them.
  Likely this means that we should remove `DynamicTy` and instead keep new fields in the specific expressions for differentiating what dev set and what is resolved.
Currently for example a `Hir.Union` will always have `ty()` `Ty.VOID`, but it essentially gives back a `TyUnion` when "ran" (since types are never ran).
So it feels a bit weird to have `Hir.Union` state that it gives `Ty.VOID` since it will never be ran.
Same thing goes for `Hir.Array` where it acts a bit weird since if no elements given then it is a "type" but otherwise a "value"
I think the existence of `ty()` on all expressions is what confuses things.
Perhaps it would be better to not have `ty` on the interface, and let each expression class only contain the required fields for its metadata,
Then each of them will have different rules depending on context, if it should adhere to DEADEND or not, et cetera.
Because as things stand, it feels weird to have a `ty()` on the interface, since one form of Ty is not more *right* than the other, it's all context-based.

See if DEADEND can be improved, so there is no need for two separate fields in an Expr.
  So instead we can say "this is the ty of the expr, but it is DEADEND. So you can use one for guessed typing, but it ain't gonna happen in runtime"

`Hir.Conditional` should likely have a `resolvedTy` which is what it would give if it completed, and not DEADEND

## Different resolvers (requires 'Better separation of "ty()"'
then we have a set of resolvers which acts differently depending on the context of the usage of the expression.
For example `TyResolverForResult`, `TyResolverForReturn`, `TyResolverForBreak`, `TyResolverForTypeAnnotation`, etc.

## Updated test cases, thorough rework of how tests are done

* Find test cases that create invalid code structures. Type-only nodes in value positions were removed with `Hir.TyExpr`; review other synthetic fixtures separately.
* Restore source-driven constructor layout and return-flow coverage when constructor-call syntax can be raised reliably. Unrepresentable constructor fixtures are deferred.
* Should use more s-expression printing
* Should use more test cases found in files
* Should use better ways of storing expected output for a snapshot test
* Should have ways of giving a kind of test matrix for the different test files
  * This way we can test that all things run the same way both from LLVM and the interpreter
* Global/common test value sources, such as one that gives all optimization levels
* Rather than testing that for example spreading works for every scenario, there should be specialized test cases that test that all different permutations of
  spreading works. This will keep the tests more focused on the area of their need/use rather than creating very complex test cases that become like a test
  matrix of all mixes of all features.
  * There is still need for tests which mixes different features to see that they play along well together, but that should be focused tests where that
    purpose is more explicit, such as a "see that spreading works with lambda lifting" and "see that spreading works with array creation" and "see that
    spreading works with captured lambda lifting" (these are just examples, do not need to implement these specific ones)
* Look into changing s-expression strings wrapped in ' so that inline test cases do not need to escape 

## Updated passes
* Go through each pass separately and try to simplify them, then at the end ask the agent to write findings that other agents can make use of for further passes.
* Special consideration should be taken to the extremely ugly Hir.Call and Hir.Function visitors that do lots of special spread/tuple work
  * IFF there is need for this special lookup, I am sure it can be simplified into some outside visitor that can do things better.
  * It is just too extremely ugly to have all this structure-specific code inside the visitors

## Change `allocator` fields to Expression
The allocator expression should be just Hir.Expression.
It should be a Hir.Lexeme at first, not Hir.Identifier.
It is then up to later passes to properly find the proper allocator.
Allocators themselves are not supported yet, support is deferred.
So right now they will always stay Hir.Lexeme, and not be validated.

## Constant array-length folding
* Fix integer addition in `JavaUtil.add`: `[;int;(1 + 1)]` currently gets length `0`. Keep arithmetic folding separate from annotation representation.

## Standalone declaration references
* Register standalone declarations in identifier resolution: `var n: uint8; n = 7; n` currently fails to find `n`. Keep this separate from the Ty migration.

## Indexing syntax: remaining work

* Support unparenthesized call-result indexing: `make()[0]`. Currently use `(make())[0]` to preserve the intended receiver.
* Raise range accessors such as `values[0..1]` from AST to HIR. Keep slice syntax/lowering separate from the Ty migration's existing indexing type rules.

## Construction-target evaluation

* Evaluate runtime effects in construction-target wrappers during MIR lowering. Targets currently supply only layouts; lambda capture discovery also skips
  these expressions. Keep type-only identifiers distinct from runtime values.

## Dynamically resolvable type annotations: deferred

* Define comptime type values and resolve expressions that calculate or obtain a type from metadata.
* Distinguish a type-valued result from an ordinary expression's value/completion type. Calls, literals, blocks, and runtime bindings are currently rejected as annotations.
* Preserve source constraints separately from inferred bindings when adding dynamic resolution to `Hir.DynamicTy`.
* Resolve dynamic array-length constraints explicitly; identifier lengths currently become unspecified, not evaluated constant lengths.

## Stating function type for field inside struct

Should support:

```inf
val S = struct { val fn: (v: int): int };
```

Or, if you strongly advise it as such because of difficult parsing rules/changes:

```inf
val S = struct { val fn: ((v: int): int) };
```

Update test cases that uses a type alias like `val Fn = (v: int): int;` for struct fn fields with inline types.
But keep some test case that still tests that both work, both alias and inline.

## Stored closures

* Preserve captured environments when functions are stored in fields or passed as values. Lambda lifting currently adds capture parameters only to directly
  resolved calls; incompatible stored signatures are rejected.

## Conversion expressions: investigation

* Investigate explicit conversion expressions; syntax is undecided, possibly `(uint8) 10` or `10 as uint8`. Neither is currently supported.
  Keep this separate from existing contextual literal typing (`val n: uint8 = 10`) and numeric literal suffixes (`10u8`).

## Array length constraints: investigation

* Investigate minimum-length constraints separately from exact fixed sizes; no syntax is chosen. Fixed lengths are exact at binding/reassignment, argument,
  field, and return boundaries. Existing unspecified-length arrays such as `[;int;]` are supported, not minimum-length constraints.

## Contextual lambda typing: remaining work

* Infer callback parameter types inside fresh spread tuples before validating their resolved parameter declarations and the spread shape.
* Allow function-signature aliases inside tuple annotations, such as `val callbacks: (fn: Fn,)`.
* Support tuple return types in function-signature declarations.

## Calling function with spread arrays: remaining work

* Support spreading arrays into calls: `val args = [1, 2]; add(...args)`. Only allow static array lengths, runtime array length is deferred until a later stage.

## Tuple construction: remaining work

* Replace repeated whole-tree preparation with dependency-aware type resolution, without moving resolution back into tuple context or conversion visitors.
* Support spreading fields into a new tuple: `val args = (1, 2); val combined = (0, ...args);`, producing `(0, 1, 2)`. Call spreading does not yet support this
  construction syntax.
* Support empty tuple values: `val args = ();`, allowing `f(...args)` to supply zero arguments.

## Unification of "struct" and "tuple"

* A tuple should in essence just be a short-form of a struct declaration
* They should be convertable to and from each other if they exactly match
* It should be possible to "explode"/"unpack" a struct as well to a function call and other locations, just like with a tuple

## Simplify `Hir.Array`
Look into the fields of Array, seems there is duplication of how the array ty and element ty and such are stored.
Seems like the element ty can be fetched from the array ty, and that the Array expression can always give back `TyValueArray`

## Unification of Array and Tuple

* An array of unbounded size and a tuple with only positional rest parameters should be equivalent
* Difference then being that a Tuple type should be able to be defined as `(bool, uint8...)`
* Investigate possibility/feasibility of supporting something like `(bool..., uint8..., bool)`
    * Which means it can be any number of booleans, then any number of uint8, then one last bool
    * This feature is absolutely not required and should only be supported if it can be "easily" done and would be considered idiomatic to the rest of the
      language

## Unification of "named arguments"

* Function calls now use `=` for named arguments: `fn(a = 10)`. The old `:` spelling is rejected.
    * To be able to assign a value inside a nested expression, it must be done using new `:=` syntax like `fn(a = foo := 10)`
        * The main difference to `=` is that `=` returns `void` but `:=` returns RHS value.
* Give feedback on viability of this change, if it is a good idea or not.

## Incomplete function-signature aliases: investigation

* Investigate reusable incomplete function-signature aliases such as `val Fn = (value): bool`. Decide whether inference is independent per use or shared,
  and how it relates to generics. These declarations are currently invalid, even with a complete expected type on the left-hand side.


## Pointer and union annotation inference: deferred

* Pointer syntax remains deferred. Union syntax is supported; inference for incomplete union members and contextual selection among numeric alternatives remains deferred.

## Partial and generic tuple annotations: investigation

* Investigate partial/generic tuple annotations, preferably named types such as `(uint8,$T)` so the inferred type can be referenced elsewhere.
  There is no `?` placeholder syntax; generic semantics and syntax remain undecided.


## Change interpreter executor

ThirToMirLoweringTest should not execute InterpreterCodeExecutor.
If the test is made for testing the interpreter, then it should be inside an interpreter test class

* Normalize suffixed integer constant content before interpreter execution: `val args = (2u8,); args[0]` currently passes `2u8` to `BigInteger`.

## Optional arguments, default values for parameters, named arguments

## Fixes regarding tuples and spread

* Most uses of `instanceof Hir.Spread`  feels suspect; there is something fishy going on.

## Partial functions, for currying/composition

## Allocators, to be able to specify stack vs heap, and if something is by-reference or by-value

## Universal function calls

* Decide whether `service call ...args` should mean `service.call ...args`. The space-separated spelling currently nests calls; it does not look up a member of
  `service`.
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

## Type usage

* Model copying, moving, referencing, and child mutation metadata for types.
* Needs investigation for how to best be represented, but likely as a modifier on the type of the expression, so the type contains its restrictions.
    * Then identity comparison of "is type A same as B" needs expansion, where an "immutable int" is same (or not same) as "mutable int" depending on context.
* Track usage settings during type checking.
* Reject invalid copy, move, reference, and mutation operations.
* Model immutable and non-copyable types.
* Use counts only for linear or bounded resources; arbitrary quotas have no clear value.

## THIR and comptime

* Define the first supported THIR evaluator expressions.
* Implement a THIR expression evaluator instead of using MIR.
* Test THIR expression evaluation.
* Define comptime function values and calls.
* Execute simple comptime functions during compilation.
* Expose expressions as comptime values.
* Let comptime functions return transformed expressions.
* Replace transformed expressions before runtime compilation.
* Test comptime expression transformations.

## Comptime type expression transformations

* Expose type usage settings to comptime expression transformations.

## Packages

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

## Package imports

* Bind imported symbols in the importing package.
* Type-check calls to imported symbols.
* Compile calls to imported package code.
* Test package exports, imports, and cross-package calls.

## Core library and bindings

* Create a minimal core library package.
* Export its public symbols.
* Link the core library into test compilation sessions.
* Import the core library from test code.
* Call imported core-library functions from test code.

## Core library comptime functions instead of reserved keywords

* Implement comptime `val`, `var`, and `const` functions in the core library.
    * `val a = 10` where `val` is a function that takes a `Hir.Assignment` and returns a `Hir.Assignment` with immutable type for `a`.
    * `const b = 20` where `const` is a function that takes a `Hir.Assignment` and returns a `Hir.Assignment` with constant type for `b`.
* Use usage settings to implement immutable and mutable bindings.
* Remove reserved `val` and `var` keywords.
* Test core-library `val`, `var`, and `const` bindings.

## Type visibility and access control

* Able to specify visibility and access control for types, fields, and methods.
* Implemented as comptime functions like "val", "var", et cetera. Takes an expression and return a modified expression with the visibility/access control
  applied.
* Needs investigation for how to best be represented, but likely as a modifier on the type of the expression, just like "val" and "var" are modifiers on the
  type of the expression.

## Other

* HirTyIdentifierToTyTransformerPass feels wrong. It converts from Identifier to BuiltInTy
  * It should not be "Identifier" at this stage, since it is not actually pointing to something (which means it should have been replaced earlier)
  * We should not keep the "Identifier" anyway, since in essence it will mean an unresolved Identifier in the tree, which should not be allowed

* See `// TODO: WRONG "explicit", needs context` comments. The explicitness of built-in types can be wrong (I think)
  * Right now is is not consistent. `1uint8` should be "explicit" for the literal, `uint8` should be explicit, `int` is explicit (default 32), `1` is NOT explicit

* Should `"val f = (t: (uint8, uint8)) => t; f({ (1, 2) })"` actually be valid? What does it actually MEAN/DO?

* Support spreading in most locations of the code
    * Support spreading inside tuple constructions
    * Support labeled spreads (`name = ...args`)
    * Support spreading of an array into another/new array

* Make more use of `Ty.INVALID` when doing the type investigation
  * So we can know "something earlier decided this is invalid, so we will not try to work with it, but will do what we can"

* Go through all test cases where we expect throws exceptions, and figure out it those could be more specific exceptions instead
  * So when we throw and then catch and assert, we can assert on the type and metadata instead of the thrown message text

* Support Arrays, runtime-length expansion for spreads (?)
    * Seems very dangerous. Perhaps a good place to start developing error handling.
        * ie. that if calling convention cannot be statically verified, then should always return `Result<T, Error>`

* Consider if `Hir.Spread` return type should not be a *new* struct type, but with same fields
    * So they are structurally equivalent, but loses any "nominal" identity

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
