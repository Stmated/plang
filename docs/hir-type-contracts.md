# HIR type contracts

HIR separates expression completion, binding types, source constraints, and context-specific resolved information. There is no universal `Expression.valueTy()` accessor.

## Type meanings

| Concept | Contract |
|---|---|
| Expression completion | `Expression.ty()` describes normal completion. `VOID` means completion without a value; `DEADEND` means no normal completion. Neither is a missing type. |
| Binding type | `Dec.resolvedTy` and `Parameter.resolvedTy` describe the bound value. Their own expression completion is `VOID`; identifiers read the binding type, not the declaration/parameter completion type. |
| Source-declared constraint | `Dec.typeAnnotation`, `Parameter.typeAnnotation`, `FunctionSignature.returnTypeAnnotation`, and `Array.elementType` use non-expression `Hir.DynamicTy` containers. Their `ty` values retain source constraints, including inference placeholders; optional source expressions preserve aliases and composite type syntax. |
| Callable signature | The resolved `TyFn` supplies parameter types and `returnTy()`. `FunctionSignature.ty()` is authoritative; no duplicate return-type cache is needed. Creating a function value does not execute its body. |
| Aggregate/member shape | Construction-target, member, indexing, and spread-field queries expose the specific resolved layout or selected type required by the consumer. They do not infer types or provide a universal structural fallback. |
| Return/break collection | Execution-aware collectors use reachable payload completion types, respecting evaluation order and transfer scope. A nominal result or structural type does not establish that execution reaches a return or break. |

```inf
val n: int = 1;
n;
return n;
```

Here the declaration completes with `VOID`, its binding type is `int`, and the identifier completes with `int`. The `return` expression is `DEADEND`; its reachable payload contributes `int` to the enclosing return collection.

Structural information can remain useful when an expression cannot complete. A call whose argument returns early may still have a resolved callee signature and nominal result type for diagnostics, but the call completes with `DEADEND` and contributes no hypothetical result to return collection. `Convert.targetTy()` likewise remains a destination type even when the operand transfers control.

Not every noncontinuing expression has an aggregate layout: a diverging tuple construction has no tuple value or layout. Specific queries must preserve that distinction rather than manufacture missing structure.

Typing passes own inference and refresh resolved information after contextual typing, literal typing, conversions, or transformations refine it. Queries consume that information; they must not freeze an early answer or reconstruct inference.

Tuple typing constructs layouts without checking member validity. Tuple validation distinguishes annotation constraints, which may retain placeholders, from runtime value members, which must have resolved value types.

Declaration and parameter bindings store `resolvedTy`. Typing initializes explicit constraints and refreshes inferred bindings, and lambda lifting supplies resolved types for generated bindings. Declaration and parameter inference preserve `typeAnnotation`. Contextually inferred parameters retain their source placeholders; validation and resolved signatures read their binding types.

Function return inference preserves `returnTypeAnnotation`, including inference placeholders and aliases. The resolved `FunctionSignature.ty().returnTy()` refreshes as the body is refined. Rebuilding or lifting a signature retains that return type and uses the current resolved parameters, including generated captures.

## Type annotations

`Hir.DynamicTy` is not an expression. Annotation containers and their `ty` values are non-null; `Ty.INFER` represents an omitted or not-yet-resolved constraint.
AST-to-HIR preserves explicit annotation syntax without resolving types. HIR-to-THIR resolves builtins using the compilation target.
`Lexeme` holds an unresolved word or syntactic name and always has `Ty.VOID`. `Identifier` owns its name and resolved target; it does not retain a lexeme.
Built-in annotation leaves use `BuiltInTy`, whose type comes only from a recognized `Tys.fromString` name. Direct built-in annotations become static `DynamicTy` constraints; composite annotations retain these leaves, never typed lexemes.
Member names are string metadata on `DotAccess`. Constructor assignments retain `Lexeme` field names on their `lhs`; these are metadata, not variable references.
Static types need no source expression. Expression-backed annotations retain their source syntax and refresh the cached constraint during typing, including after an alias definition changes.
`DynamicTy.resolve` centralizes this distinction. Contextual inference updates bindings and resolved signatures, never source placeholders.

```inf
val S = struct { val value: uint8; };
val Fn = (value: uint8): uint8;
val s: S = new heap S { value = 7; };
val f: Fn = (v) => v; // v's source constraint remains INFER
```

Annotations denote supported type syntax or named type definitions, not the result type of an arbitrary runtime expression.
Comptime type evaluation is deferred.

```inf
val sample = 1;
val n: sample = 2;    // error: a runtime binding is not a type definition
val n: 1 = 2;         // error: a value literal is not a type
val get = () => 1;
val n: (get()) = 2;   // error: calls are not evaluated as type annotations
```

## Function and program results

Inferred function results and external program results union reachable return payloads with the body's normal completion type. `DEADEND` contributes no result; without reachable returns or normal completion, the result is `DEADEND`. Complete source function return annotations remain constraints, not inference targets.

```inf
val choose = (flag: bool) => { if (flag) { return 7; }; true }; // int | bool
val maybe = (flag: bool) => { if (flag) { return 7; }; val n = 8; }; // int | void
val set = () => { var n = 7; n = 8; }; // void
val early = () => { return 7; true }; // int
```

Terminal assignments and declarations complete with `VOID`, matching MIR's `UNIT`. `=` does not return its RHS. Nested function returns belong to their own scope; unreachable tails do not affect inference.

`Program.ty()` describes the externally returned value, not body completion. Source raising usually wraps the script's final expression in a synthetic `Return`, so the body can be `DEADEND` while the external result is a value or `VOID`. Raw HIR bodies can also complete normally; both representations use the same result policy.

```inf
val flag = false;
if (flag) { return 7; };
true // external result: int | bool; lowered body completion: DEADEND
```

A noncontinuing script with no reachable returns has external type `DEADEND`. MIR uses its existing `VOID` ABI signature and emits no normal return.

## Context-specific queries

`Tys.getBindingTy` selects the type for identifier targets and assignment destinations. It reads declaration and parameter `resolvedTy`, selects resolved member/indexed types, and uses completion types for other targets, including resolved function signatures. Consumers with a known declaration or parameter read `resolvedTy` directly; other consumers use the helper instead of repeating node-kind checks.

`Tys.getAssignmentContextTy` selects initializer constraints separately: declarations supply source annotations, including omitted array element types, while other assignment targets supply binding types. Prior inferred binding types are never initializer constraints.

Declaration inference resolves whole bindings or omitted array element types from the current initializer. Recursive array/tuple resolution preserves explicit source constraints; previously inferred binding types are not constraints. Inferred members refresh when the initializer changes, without modifying the annotation tree.

```inf
val whole = [10,20];                         // whole binding inferred
val values: [;2] = [10,20];                  // element type inferred; length 2 retained
val nested: (uint8,[;2]) = (10,[true,false]); // uint8 slot retained; bool array element inferred
```

There is no `?` placeholder syntax or explicit conversion-expression syntax. Contextual literal typing and numeric suffixes such as `10u8` are supported. Omitted array element types still require inference readiness checks; arbitrary naked tuple-slot inference is not a source feature.

Standalone function-signature aliases must be fully specified at their own declaration. An expected type on the left-hand side does not complete them, and reusable per-use incomplete aliases are not supported.

```inf
val Complete = (value: uint8): bool;
val Fn = (value): bool;                         // error: missing parameter type
val Constrained: Complete = (value): bool;      // error, despite the complete expected type
```

Lambdas have a separate parameter policy: a concrete expected signature supplies an entirely omitted annotation by position, or the lambda may declare a complete equivalent annotation. Parameter names need not match. Partially annotated parameters such as `(arr: [;2])` are not completed from the expected signature.

`Tys.getFunctionReturnContextTy` selects source return constraints for body contextual typing; its use-site overload supplies an expected return where needed. Previously inferred returns are not body constraints. Ordinary lambda returns are inferred from the body; partially annotated array returns infer the element type while retaining the declared length. Validation compares the actual body result collected by `HirBodyResultTyping` with the resolved signature return.

```inf
val good = ():[;2] => [10,20];
val bad = ():[;2] => [10,20,30]; // error: actual result length 3, signature return length 2
```

A body ending in a synthetic `Return` can complete with `DEADEND` while its collected result is an array. Body completion is not the actual result type, and return constraints must not overwrite the actual result's length.

Fixed array lengths are exact at binding/reassignment, argument, field, and return boundaries. Existing unspecified-length arrays such as `[;int;]` remain supported; they are not minimum-length constraints.
Array lengths are value expressions, not type expressions. Existing constant folding is retained; identifier lengths currently remain unspecified rather than being evaluated at comptime.

`Tys.getCallableSignature` selects a single resolved `TyFn` for call typing and argument contexts. `Tys.getCallableValueTy` also retains function-containing unions for compatibility diagnostics; neither query exposes nonfunction aggregate shapes or infers missing types. A union does not establish a single callable signature.

Callable lookup follows syntactic result wrappers, bindings, and conversion destinations without entering function bodies or return/break payloads. Higher-order calls read their resolved callee's return signature, not a cached hypothetical result. Member/indexed calls read `Tys.getMemberTy`/`Tys.getIndexedTy`; conditionals select resolved branch information and include `VOID` for missing arms.

```inf
val apply = (fn: (value: uint8): uint8): uint8 => fn(1);
val use = () => ({ return true; apply; })((v) => v);
use()
```

The target still supplies `apply`'s signature for argument typing and diagnostics. The call completes with `DEADEND`; only `true`, not the nominal `uint8` call result, contributes to `use`'s return type.

`Tys.getConstructionTargetTy` and `Tys.getMemberReceiverTy` expose only resolved struct/tuple layouts for their respective contexts. They follow result wrappers, resolved bindings, conversion destinations, call return signatures, construction targets, and selected members. They do not infer layouts, enter function bodies or transfer payloads, or reconstruct tuples from their entries. A diverging tuple construction has no layout.

`Tys.getMemberField` looks up a `DotAccess` name in its receiver's current layout. `DotAccess.memberTy()` and `Tys.getMemberTy` read the selected type independently of completion. Field selections are query results, never cached on expressions.
`HirFieldResolution` checks field names and resolves member completion during common typing. Preparation may defer unavailable layouts; final resolution reports unknown field names.

AST-to-HIR constructs `DotAccess` directly. Nested accesses each have their own receiver; calls and indexing operate on the complete accessed member.

```inf
s.inner.values[0] // indexing a DotAccess whose receiver is another DotAccess
```

`NewByBlock.fields` contains `Assignment` nodes with `Lexeme` field names on the `lhs` and values on the `rhs`. Assignments complete with `VOID` or `DEADEND`; field value types come from the `rhs`.
Contextual typing and validation receive the enclosing construction through `visitNewByBlockField`; `Tys.getInitializerField` looks up the expected field without a stored owner or parent reference.
MIR lowering looks up field indices in the current layout while preserving initializer evaluation order. Only the constructor assignment's direct `lhs` is protected from identifier resolution; assignments nested in its `rhs` retain ordinary resolution and validation.

```inf
val S = struct { val value: uint8; val flag: bool; };
val use = () => (new heap S { value = 7; flag = { return true; }; }).value;
use()
```

The construction still supplies `S`'s layout, and the path selects `uint8`. Both expressions complete with `DEADEND`; only `true` contributes to `use`'s return type.

`Tys.getIndexingReceiverTy` reads the resolved receiver for array or literal tuple indexing. `Tys.getIndexingAccessorTy` distinguishes element indices from array/range slices, including noncontinuing accessors. These queries follow result wrappers, bindings, conversion destinations, resolved call returns, selected members/indexed types, and conditional branches. They consume existing types without entering transfer payloads or manufacturing layouts for diverging tuples.

`Array.arrayTy` and `Range.rangeTy` retain resolved array/slice information independently of completion. Range bounds use completion types, not return payloads or unreachable sequence tails. Incompatible bound types produce an `INVALID` element type; a transfer makes the range `DEADEND` without losing its slice identity.

`ArrayAccess.indexedTy` retains the selected element or slice type independently of completion. Common typing refreshes it after receiver refinement; `Tys.getIndexedTy` supplies binding, callable, and member queries. Tuple indices use the literal's direct `ty()` and value; computed, noninteger, negative, and out-of-range indices remain errors.

```inf
val values = [7u8];
val use = () => values[{ return true; }];
use()
```

The access selects `uint8` but completes with `DEADEND`; only `true` contributes to `use`'s return type.

`HirSpreadShape.fields` exposes resolved tuple/struct fields in layout order for call arity, named binding, contextual typing, validation, and MIR field loads. It follows the same resolved layout sources as construction/member queries. `availableFields` lets preparation defer unresolved operands.

A noncontinuing operand can retain known fields for diagnostics. Without a resolved layout it supplies no slots; a diverging tuple construction never gains a hypothetical layout. Lowering evaluates the operand before loading fields and emits no call when it transfers control.

```inf
val pair = (a: uint8, b: uint8) => a;
pair(...(b = 2, a = 1))
```

The spread keeps field order `b, a`; named binding selects parameters `b, a` without reordering the source construction.

## Context selection is not reachability

`FindResultExpressionsVisitor` selects syntactic final expressions and conditional branches for contextual typing. `findReturns` also selects explicit return payloads without entering nested functions. These selections may include unreachable source expressions so diagnostics and contextual typing remain available.

`FindReturnTypesVisitor` and `FindBreakTypesVisitor` instead use `HirExecutionVisitor` to collect potentially executed transfers. They honor eager evaluation, short-circuiting, and unreachable suffixes; nested function returns and nested loop breaks belong to their own scopes. Structural queries and syntactic contextual selection must not replace this execution-aware collection.

## Consumer inventory

Production consumers are grouped by the information they need. Pass names below refer to `src/main/java/org/inf/hir/passes/`; other HIR classes are in `org.inf.hir`.

### Expression completion

| Current consumer | Required information | Migration |
|---|---|---|
| `HirTyCommonVisitorPass.visitBinaryOperation`, `visitNot`, `visitConditional` | Operand/branch completion and existing operation-specific result rules. | Ty 13-14 complete |
| `HirTyCommonVisitorPass.visitArray` | Resolved `arrayTy` when evaluation completes; `DEADEND` when an element or length transfers. | Ty 13 complete |
| `HirTyCommonVisitorPass.visitRange` | Bound completion determines `rangeTy`; transfers do not supply hypothetical bound values. | Ty 13 complete |
| `Hir.Expressions`, `Hir.Block`, `Hir.Argument`, `Hir.TupleEntry` | Sequence/block completion and wrapped expression completion, not an unconditional syntactic last-child result. | Ty 13-15 complete |
| `HirTyCommonVisitorPass.visitAssignment` | Assignment completion remains `VOID` or `DEADEND`; function results use completion, not the RHS type. | Ty 11, 14-15 complete |
| `Hir.Convert` | Completion via `ty()`; conversion destinations via `targetTy()`. | Ty 13-15 complete |

### Binding types

| Current consumer | Required information | Migration |
|---|---|---|
| `Hir.Identifier.ty` through `Tys.getBindingTy` | Resolved declaration/parameter binding, or the referenced function's resolved signature. | Ty 2-3 complete |
| `HirTyCommonVisitorPass.visitDec`, `visitAssignment` inference | Stored `resolvedTy` binding, separate from source annotations and `VOID` completion. | Ty 2, 4, 15 complete |
| `HirTyCommonVisitorPass.visitParameter`; `HirFnTyVisitorPass.fnToTyFn` | Stored `resolvedTy` binding; signatures consume it, and common typing refreshes explicit constraints without resetting contextual inference from source placeholders. | Ty 3, 5, 15 complete |
| `ThirToMirLowering.declaration`, `assignment` | `Dec.resolvedTy()` for local allocation and stores, never the declaration's `VOID` completion. | Ty 2 complete |
| `HirTyCommonVisitorPass.visitStruct` | `Dec.resolvedTy()` for field layout construction. | Ty 2 complete |
| Assignment destinations in `HirFunctionContextVisitor`, `HirTupleContextVisitorPass`, `HirIntegerLiteralTypingVisitorPass`, `HirFunctionValidationVisitorPass`, `HirTupleValidationVisitorPass`; compound-assignment destination in integer typing | Contextual typing uses `Tys.getAssignmentContextTy`; validation and compound assignments use `Tys.getBindingTy`. Member access and indexing select their resolved member/element types. | Ty 2-4, 8-9 complete |

### Source-declared constraints

Annotation reads select source constraints or inference placeholders, never previously inferred binding or return types.

| Current consumer | Required information | Migration |
|---|---|---|
| Declaration inference in `HirDeclarationTyping`, called by `HirTyCommonVisitorPass`; assignment contexts in function/tuple/integer contextual typing | Resolve whole bindings and omitted array elements, including nested tuple/array constraints. Contexts read source constraints, never previously inferred bindings. | Ty 4, 16 complete |
| `HirFunctionTyping.contextualize`; `HirFunctionParameterValidationVisitorPass.checkParameters` | Source parameter constraints versus resolved parameter types supplied by context. | Ty 5 complete |
| `HirFnTyVisitorPass.fnToTyFn`; `HirTyCommonVisitorPass.visitFunction`, `visitFunctionSignature` | Source return constraint versus the resolved `TyFn` return and parameter types. | Ty 5-6 complete |
| Return contexts in `HirFunctionContextVisitor`, `HirTupleContextVisitorPass`, `HirIntegerLiteralTypingVisitorPass`; return checks in function/tuple validation | Source/use-site return constraints for contextual typing; resolved signatures for validation. | Ty 6 complete |
| `HirLambdaLiftingTransformerPass` generated declarations, parameters, and signatures | Binding identities and resolved capture/signature types through necessary HIR transformations. | Ty 2-6 complete |

`TyParam.ty()` and MIR signature parameter types remain value types; they are not HIR parameter-expression completion types.

### Callable signatures

| Current consumer | Required information | Migration |
|---|---|---|
| `HirTyCommonVisitorPass.visitCall` target reads | `Tys.getCallableSignature` supplies the resolved callee `TyFn` and nominal call result, independent of target/argument completion. | Ty 7 complete |
| Call target reads in `HirFunctionContextVisitor`, `HirTupleContextVisitorPass.linkArguments`, `HirIntegerLiteralTypingVisitorPass`, `HirFunctionValidationVisitorPass`, `HirTupleValidationVisitorPass` | `Tys.getCallableSignature` supplies resolved parameters for argument binding, contextualization, and validation. | Ty 7 complete |
| `HirFunctionValidationVisitorPass.check` actual-type reads, including `visitNewByBlockField` | `Tys.getCallableValueTy` supplies function-valued information for diagnostics without interpreting it as reachable completion. Field constraints come from the enclosing construction's current layout. | Ty 7-8 complete |

### Aggregate/member shapes

| Current consumer | Required information | Migration |
|---|---|---|
| `HirTyCommonVisitorPass.visitNewByCtor`, `visitNewByBlock`; initializer contexts and checks in function/integer/tuple/array passes | `Tys.getConstructionTargetTy` supplies construction layouts. `Tys.getInitializerField` selects the expected field independently of completion. | Ty 8 complete |
| `HirTyCommonVisitorPass.visitDotAccess`; member-write checks in `HirTupleValidationVisitorPass.requireWritable`; callable member lookup | `Tys.getMemberField` selects members against `Tys.getMemberReceiverTy`. `Tys.getMemberTy` reads the selected type; write validation reads the receiver layout. | Ty 8 complete |
| `HirTyCommonVisitorPass.visitArrayAccess`; array-access checks in tuple validation | `Tys.getIndexingReceiverTy`, `Tys.getIndexingAccessorTy`, and `Tys.getIndexedTy` separate receiver and element/slice types from completion. | Ty 9 complete |
| `HirTupleAccess.index`, `availableIndex` | The integer literal's direct type and value for tuple-index diagnostics. | Ty 9 complete |
| `HirSpreadShape.fields`; spread precheck in `HirFunctionContextVisitor` | Resolved static tuple/struct fields for spread arity, names, and binding, including known layouts after transfers. No layout for a diverging tuple construction. | Ty 10 complete |
| `HirTupleTyping.resolve` | Tuple completion/layout produced by typing, not a new universal structural query. Contextual destination layout remains separate metadata. | Ty 14 complete |

### Return/break collection

| Current consumer | Required information | Migration |
|---|---|---|
| `HirFunctionReturnTyping`, called by `HirTyCommonVisitorPass.visitFunction` | Shared `HirBodyResultTyping` unions reachable returns with resolved body completion; terminal assignments/declarations contribute `VOID`. | Ty 11-12 complete |
| `HirTyCommonVisitorPass.visitProgram` | Shared body-result policy for the external script result, distinct from body completion. | Ty 12 complete |
| `HirTyCommonVisitorPass.visitLoop` | Reachable break payload types from `FindBreakTypesVisitor`; retain existing loop rules during migration. | Ty 13-14 complete |
| `FindReturnTypesVisitor`, `FindBreakTypesVisitor` | Reachable payload completion via `ty()`, collected with execution-aware traversal. | Reuse in Ty 11-13 |

`Hir.Program.ty()` is an external return type, not its body's completion type. `Hir.Return.ty()` and `Hir.LoopBreak.ty()` describe the transfer (`DEADEND`), not the payload.

## Type selection boundaries

There are no universal value-type caches, setters, or forwarding adapters. Neither `Tys.getValueTy` nor `getStructuralTy` is a replacement: callers choose the specific type concept they require.

Named context-specific `Tys` helpers centralize type selection without performing inference. Production typing, queries, lowering, tree printing, and type assertions do not consume the universal accessor.

`HirTransformer.transformAssignment` and lambda-lifted calls retain completion types. Generated bindings and signatures retain their resolved types; callable results and construction layouts derive from those authoritative sources instead of duplicate caches.

`ToStringTreeHirVisitor` distinguishes completion, source annotations, resolved bindings, contextual tuple destinations, and the named array/range/member/indexing metadata. Type assertions use those specific concepts.

Ty 1 changes no compiler behavior. Ty 11-12's approved function and script rules use `VOID` for terminal assignments/declarations and merge reachable returns with normal fallthrough. These contracts do not authorize changing `=` to return a value or adding syntax.
