# HIR type contracts

HIR separates expression completion, binding types, source constraints, and context-specific resolved information. There is no universal `Expression.valueTy()` accessor.

## Type meanings

| Concept | Contract |
|---|---|
| Expression completion | `Expression.ty()` carries the resolved type; `Tys.completionTy(expression.ty())` selects normal completion. `VOID` means completion without a value; `DEADEND` means no normal completion. Neither is a missing type. |
| Binding type | `Tys.structuralTy(Dec.ty())` and `Tys.structuralTy(Parameter.ty())` describe the bound value. Their own completion is `VOID`; `HirExprs.getBindingTy` combines that structure with the initialization assignment's typed noncompletion for identifier reads. |
| Source-declared constraint | Declaration/parameter `TyInfo.declaredConstraint` retains source requirements, including inference placeholders. `DynamicTy` preserves annotation syntax and the annotation resolver's input. Function-return and array-element constraints still use their `DynamicTy` containers. |
| Callable signature | The resolved `TyFn` supplies parameter types and `returnTy()`. `FunctionSignature.ty()` is authoritative; no duplicate return-type cache is needed. Creating a function value does not execute its body. |
| Aggregate/member shape | Construction-target, member, indexing, and spread-field queries expose the specific resolved layout or selected type required by the consumer. They do not infer types or provide a universal structural fallback. |
| Resolved structural type | `Tys.structuralTy(expression.ty())` projects retained typing information from the same `ty` storage; typed inspection uses `Tys.asStructural`. A tuple's retained structure is not automatically an available runtime layout. |
| Expected type | `Tuple.contextualType` is a destination supplied by contextual typing, including its slot order and labels. It is an input to resolution, not a duplicate of the resolved structural type or a flag on it. |
| Return/break collection | Execution-aware collectors use reachable payload completion types, respecting evaluation order and transfer scope. A nominal result or structural type does not establish that execution reaches a return or break. |

The canonical context names are defined in [Inf type contexts](../CONTEXT.md).

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

Declaration and parameter typing refreshes structural bindings without changing completion `VOID` or turning earlier inference into source restrictions. Contextually inferred parameters retain their source placeholders; validation, signatures, struct fields, and MIR storage select binding structure.

Function return inference preserves `returnTypeAnnotation`, including inference placeholders and aliases. The resolved `FunctionSignature.ty().returnTy()` refreshes as the body is refined. Rebuilding or lifting a signature retains that return type and uses the current resolved parameters, including generated captures.

### Binding storage and annotation resolution

Declarations and parameters store `TyInfo` in their ordinary `ty` field. Annotation refresh updates its declared constraint; binding inference updates its structure. `DynamicTy.ty` resolves annotations only, never omissions supplied by an initializer or use-site context. Its broader ownership refactor is deferred.

```inf
val values: [;2] = [true, false]; // Annotation resolves to [;INFER;2]; binding resolves to [;bool;2].
val Fn = (value: uint8): uint8;
val f: Fn = (p) => p;            // p has no source annotation; its binding resolves to uint8.
```

Keeping these answers distinct lets passes infer again without mistaking an earlier inferred type for an explicit requirement. Preserved syntax alone does not supply every constraint: null `DynamicTy.expression` covers omitted, static, and generated annotations.

Initializer noncompletion is queried, not stored again on the binding:

```inf
val implicit = { return true; };       // Constraint INFER; no completing bound value.
val explicit: bool = { return true; }; // Constraint bool; still no completing bound value.
val wide: uint16 = 255u8;              // Binding destination uint16, not initializer uint8.
```

Both noncompleting declarations remain `VOID` nodes; their assignments and identifier reads complete with `DEADEND`. Binding queries retain known structure without copying the declaration's constraint or a transfer payload. Noncompletion never changes a constraint into `DEADEND`.

`Dec.initialization` is a non-owning reference to its current assignment, excluded from traversal, equality, hashing, and tree output. Assignment construction/replacement maintains it. Binding completion reads that assignment's existing typed completion in constant time; initializer changes require retyping, as with other cached HIR expression types. Type-definition queries use the current RHS instead of a declaration-to-RHS index.

Generated captures use structural value types as parameter requirements, not the original initialization flow. Rebinding identifiers refreshes body typing before the lifted signature is consumed. Array-element and function-return containers retain their separate inference policies.

## Type inspection

`Ty.asCompletion` and `Ty.asStructural` select a projection before inspecting its kind; completion wrappers are transparent to callers.
Both return null when the selected projection is unavailable or has a different kind. Use the corresponding `Tys` helpers when the type itself may be null.
There are no public context-free `is`, `as`, or `ifIs` methods. Test a typed projection for null rather than adding a separate kind check.

```java
final var function = Tys.asStructural(type, TyFn.class);
final var number = type.asCompletion(TyValueNumber.class);
final var diverges = Tys.isDeadend(expression.ty());
```

`Tys.isDeadend(null)` is false. An unresolved type is not evidence of divergence.
`Ty.INFER` is an explicit inference placeholder; null usually means resolution is unavailable or a pass has not populated a field yet. `Tys.isInferred` accepts both for existing inference decisions, but they remain distinct representations.
Use `isDeadend` for completion decisions rather than comparing directly with the sentinel.

### Shared type information

`TyInfo` stores `completion`, `structural`, and `declaredConstraint` directly. Their accessors return the stored answers; there is no generic resolved projection.

| View | Meaning |
|---|---|
| `completionTy()` | Normal expression completion, including `VOID` and `DEADEND`. |
| `structuralTy()` | Retained structural type, independently of completion; `DEADEND` itself has no structure. |
| `declaredConstraintTy()` | Source requirement; null means no recorded constraint, `INFER` means an omitted requirement. |

```java
final var constraint = new TyValueArray(Ty.INFER, 2);
final var booleans = new TyValueArray(Ty.BOOLEAN, 2);
final var binding = Tys.typeInfo(Ty.VOID, booleans, constraint);
binding.completionTy();         // VOID
binding.structuralTy();         // [;bool;2]
binding.declaredConstraintTy(); // [;INFER;2]

final var integers = new TyValueArray(Ty.INTEGER, 2);
final var refined = Tys.withStructural(binding, integers); // Completion and source constraint stay unchanged.
final var explicit = Tys.withDeclaredConstraint(refined, integers); // Current answers stay unchanged.
```

Declaration and parameter nodes use this representation. Inference remains the responsibility of typing passes; the helpers replace selected answers without inferring or validating.

```java
final var transfer = Tys.typeInfo(Ty.DEADEND, null, Ty.INFER);
transfer.completionTy(); // DEADEND: no normal completion
transfer.structuralTy(); // null: no retained value shape
```

Null completion is unresolved information; null structure is unavailable structural information, including the expected absence on `DEADEND`. Neither is `INFER` or `VOID`. Explicit static and generated constraints remain recorded even without source syntax. Expected use-site types, transfer payloads, body results, and layout availability are not additional stored projections.

`Tys.typeInfo` flattens input containers: completion and constraint arguments contribute their completion type; structure contributes its structural type. To copy a container's source requirement, pass its `declaredConstraintTy()` explicitly. The stored projections do not nest `TyInfo` wrappers.

Normalization interns containers by the identities of all three selected answers, preserving source and alias objects rather than substituting equal projections from another binding. A plain type is sufficient only when its structural view matches the supplied structure and the constraint is absent; even an equal explicit constraint or `INFER` requires a container. Numeric widths and flags are preserved.

Container equality includes all three answers. `TypeComparison.same` compares completion and structure, ignoring source constraints; value compatibility selects the appropriate value projection. `Tys.containsInferred` checks completion and structure, not source placeholders; absent structure on `DEADEND` is not an inference hole. Checking the declared constraint separately still detects its omissions.

Binding noncompletion reuses typed initialization completion, not a fourth projection or a separately stored binding sentinel. Destination checks select structure even when obtaining the bound value cannot complete.

### Completion and structural composition

```java
final var type = Tys.withCompletion(Ty.DEADEND, arrayType);
type.completionTy();                     // DEADEND
type.asCompletion(TyValueArray.class);    // null: no array value completes
type.asStructural(TyValueArray.class);    // retained array shape
Tys.withCompletion(arrayType, arrayType); // arrayType, without a wrapper
Tys.withCompletion(Ty.DEADEND, null);     // DEADEND, without a structural value
```

`Tys.withCompletion` composes expression completion and retained structure without copying source constraints. A `DEADEND` second argument has no retained structure. Matching completion/structure keep the plain type; distinct answers use `TyInfo`. Use `Tys.typeInfo` with an explicit constraint or the refinement helpers when source requirements must be retained.

Use the null-safe `Tys` projections when resolution may be unavailable. `Tys.isDeadend`, common-denominator resolution, and branch unions consume completion. A branch merge drops transferring arms without including their retained structures or source constraints.

The four aggregate/indexing nodes store one container when completion and retained structure differ, not duplicate type fields. Their annotations and expected tuple layouts remain separate. Transfers retain `DEADEND` with no structure, never their return/break payloads.

`asStructural(TyStruct.class)` exposes metadata, not an available layout: a diverging tuple may match while construction/member/spread queries still have no layout. Neither typed projection collects function body results; `HirBodyResultTyping.resolve(body)` combines reachable returns with normal completion.

MIR value types and LLVM type resolution select completion, so wrappers do not become runtime layouts or ABI types. Array lowering reads the structural array type before evaluating elements and emits no allocation if evaluation transfers.

### Available layouts

Use expression queries for layout-dependent operations, not `asStructural(TyStruct.class)`. That projection retains fields for typing and type syntax without establishing layout availability.

| Query | Purpose |
|---|---|
| `HirExprs.getConstructionTargetLayout(target)` | Available tuple/struct layout for construction and initializer constraints. |
| `HirExprs.getMemberReceiverLayout(receiver)` | Available layout for member lookup and write diagnostics. |
| `HirStructLayoutVisitor.findAvailableLayout(expr)` | Shared availability policy, also used by `HirSpreadShape`. Null means no single resolved layout; callers do not need separate unresolved/nonaggregate states. |
| `HirExprs.getIndexingReceiverTy(receiver)` | Diagnostic receiver type: a diverging tuple supplies `DEADEND`, not its retained fields; a call may supply its current return layout. |

```inf
({ return true; }, 7) // Retained tuple fields, but no available layout or spread slots.
val make = (flag: bool) => (value = 7, true);
make({ return true; }) // Known return layout for diagnostics, but no completing value.
```

Layout availability is not reachability. Calls, members, and indexing may expose known types after a transfer; lowering must still stop evaluation without emitting the unfinished allocation, call, or field load. Queries read current bindings/signatures and never reconstruct tuple layouts from entries. `Tuple.contextualType` remains a destination for contextual typing, not evidence that construction completes.

## Type annotations

`Hir.DynamicTy` is not an expression. Annotation containers and their `ty` values are non-null; `Ty.INFER` represents an omitted or not-yet-resolved constraint.
AST-to-HIR preserves explicit annotation syntax without resolving types. HIR-to-THIR resolves builtins using the compilation target.
`Lexeme` holds an unresolved word or syntactic name and always has `Ty.VOID`. `Identifier` owns its name and resolved target; it does not retain a lexeme.
Built-in annotation leaves use `BuiltInTy`, whose type comes only from a recognized `Tys.fromString` name. Direct and composite annotations retain these leaves as source expressions, never typed lexemes.
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

`Hir.Union` retains type-member expressions and caches their represented type in `unionTy`, independently of its `VOID` completion. Annotation resolution follows union alias definitions rather than their runtime binding types. Union aliases have no runtime storage or closure captures. See [Union types](unions.md) for syntax and the distinction from integer bitwise OR.

```inf
val sample = 1;
val n: sample = 2;    // error: a runtime binding is not a type definition
val n: 1 = 2;         // error: a value literal is not a type
val get = () => 1;
val n: (get()) = 2;   // error: calls are not evaluated as type annotations
```

## Function and program results

Inferred function results and external program results union reachable return payloads with the body's normal completion type. `DEADEND` contributes no result; without reachable returns or normal completion, the result is `DEADEND`. Complete source function return annotations remain constraints, not inference targets.

An `int | DEADEND | VOID` branch merge therefore completes with `int | VOID`, not a diverging wrapper. If every arm transfers, completion is `DEADEND`; reachable return and break payloads are collected separately for their own scopes.

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

`HirExprs.getBindingTy` reads declaration/parameter structure and the current initialization assignment's typed completion. Identifiers consume that completion/structure pair. `HirExprs.getBindingStructureTy` reads destinations and captures without inspecting initialization completion. Other targets supply selected member/indexed types or their ordinary completion.

`HirExprs.getAssignmentContextTy` selects initializer constraints separately: declarations supply declared constraints, including omitted array elements, while other targets supply binding structure. Prior inferred declaration types are never initializer constraints.

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

`HirExprs.getFunctionReturnContextTy` selects source return constraints for body contextual typing; its use-site overload supplies an expected return where needed. Previously inferred returns are not body constraints. Ordinary lambda returns are inferred from the body; partially annotated array returns infer the element type while retaining the declared length. Validation compares the actual body result collected by `HirBodyResultTyping` with the resolved signature return.

```inf
val good = ():[;2] => [10,20];
val bad = ():[;2] => [10,20,30]; // error: actual result length 3, signature return length 2
```

A body ending in a synthetic `Return` can complete with `DEADEND` while its collected result is an array. Body completion is not the actual result type, and return constraints must not overwrite the actual result's length.

Fixed array lengths are exact at binding/reassignment, argument, field, and return boundaries. Existing unspecified-length arrays such as `[;int;]` remain supported; they are not minimum-length constraints.
Array lengths are value expressions, not type expressions. Existing constant folding is retained; identifier lengths currently remain unspecified rather than being evaluated at comptime.

`HirExprs.getCallableSignature` selects a single resolved `TyFn` for call typing and argument contexts. `HirExprs.getCallableValueTy` also retains function-containing unions for compatibility diagnostics; neither query exposes nonfunction aggregate shapes or infers missing types. A union does not establish a single callable signature.

Callable lookup follows syntactic result wrappers, bindings, and conversion destinations without entering function bodies or return/break payloads. Higher-order calls read their resolved callee's return signature, not a cached hypothetical result. Member/indexed calls read `HirExprs.getMemberTy`/`HirExprs.getIndexedTy`; conditionals select resolved branch information and include `VOID` for missing arms.

```inf
val apply = (fn: (value: uint8): uint8): uint8 => fn(1);
val use = () => ({ return true; apply; })((v) => v);
use()
```

The target still supplies `apply`'s signature for argument typing and diagnostics. The call completes with `DEADEND`; only `true`, not the nominal `uint8` call result, contributes to `use`'s return type.

`HirExprs.getConstructionTargetLayout` and `HirExprs.getMemberReceiverLayout` expose only available struct/tuple layouts for their respective contexts. They follow result wrappers, resolved bindings, conversion destinations, call return signatures, construction targets, and selected members. They do not infer layouts, enter function bodies or transfer payloads, or reconstruct tuples from their entries. A diverging tuple construction or noncompleting declaration initialization supplies no binding layout.

`HirExprs.getMemberField` looks up a `DotAccess` name in its receiver's current layout. `HirExprs.getMemberTy` reads the selected type independently of completion. Field selections are query results, never cached on expressions.
Common typing checks field names and resolves member completion. Preparation may defer unavailable layouts; final resolution reports unknown field names.

AST-to-HIR constructs `DotAccess` directly. Nested accesses each have their own receiver; calls and indexing operate on the complete accessed member.

```inf
s.inner.values[0] // indexing a DotAccess whose receiver is another DotAccess
```

`NewByBlock.fields` contains `Assignment` nodes with `Lexeme` field names on the `lhs` and values on the `rhs`. Assignments complete with `VOID` or `DEADEND`; field value types come from the `rhs`.
Contextual typing and validation receive the enclosing construction through `visitNewByBlockField`; `HirExprs.getInitializerField` looks up the expected field without a stored owner or parent reference.
MIR lowering looks up field indices in the current layout while preserving initializer evaluation order. Only the constructor assignment's direct `lhs` is protected from identifier resolution; assignments nested in its `rhs` retain ordinary resolution and validation.

```inf
val S = struct { val value: uint8; val flag: bool; };
val use = () => (new heap S { value = 7; flag = { return true; }; }).value;
use()
```

The construction still supplies `S`'s layout, and the path selects `uint8`. Both expressions complete with `DEADEND`; only `true` contributes to `use`'s return type.

`HirExprs.getIndexingReceiverTy` reads the resolved receiver for array or literal tuple indexing. `HirExprs.getIndexingAccessorTy` distinguishes element indices from array/range slices, including noncontinuing accessors. These queries follow result wrappers, bindings, conversion destinations, resolved call returns, selected members/indexed types, and conditional branches. They consume existing types without entering transfer payloads or manufacturing layouts for diverging tuples.

The structural projection of `Array.ty()` and `Range.ty()` retains resolved array/slice information independently of completion. Range bounds use completion types, not return payloads or unreachable sequence tails. Incompatible bound types produce an `INVALID` element type; a transfer makes the range `DEADEND` without losing its slice identity.

The structural projection of `ArrayAccess.ty()` retains the selected element or slice type independently of completion. Common typing refreshes it after receiver refinement; `HirExprs.getIndexedTy` supplies binding, callable, and member queries. Tuple indices use the literal's direct `ty()` and value; computed, noninteger, negative, and out-of-range indices remain errors.

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
| `Hir.Identifier.ty` through `HirExprs.getBindingTy` | Bound-value completion plus retained structure, or the referenced function's resolved signature. | Shared bindings complete |
| `HirTyCommonVisitorPass.visitDec`, `visitAssignment` inference | Structural binding and declared constraint, separate from declaration completion `VOID`; initialization completion is refreshed by typing and reused by queries. | Shared bindings complete |
| `HirTyCommonVisitorPass.visitParameter`; `HirFnTyVisitorPass.fnToTyFn` | Structural parameter binding; explicit constraints refresh without resetting contextual inference from source placeholders. | Shared bindings complete |
| `ThirToMirLowering.declaration`, `assignment` | Structural destination for locals and stores; a diverging RHS produces no binding storage. | Shared bindings complete |
| `HirTyCommonVisitorPass.visitStruct` | Structural declaration bindings for field layouts. | Shared bindings complete |
| Assignment destinations in contextual typing and validation; compound assignments in integer typing | `HirExprs.getAssignmentContextTy` supplies contextual constraints; destination checks use `HirExprs.getBindingStructureTy`. | Shared bindings complete |

### Source-declared constraints

Annotation reads select source constraints or inference placeholders, never previously inferred binding or return types.

| Current consumer | Required information | Migration |
|---|---|---|
| Declaration inference in `HirDeclarationTyping`, called by `HirTyCommonVisitorPass`; assignment contexts in function/tuple/integer contextual typing | Resolve whole bindings and omitted array elements, including nested tuple/array constraints. Contexts read source constraints, never previously inferred bindings. | Ty 4, 16 complete |
| `HirFunctionTyping.adhereToConstraint`; `HirFunctionParameterValidationVisitorPass.checkParameters` | Source parameter constraints versus resolved parameter types supplied by context. | Ty 5 complete |
| `HirFnTyVisitorPass.fnToTyFn`; `HirTyCommonVisitorPass.visitFunction`, `visitFunctionSignature` | Source return constraint versus the resolved `TyFn` return and parameter types. | Ty 5-6 complete |
| Return contexts in `HirInferredFunctionSignatureVisitor`, `HirTupleContextVisitorPass`, `HirIntegerLiteralTypingVisitorPass`; return checks in function/tuple validation | Source/use-site return constraints for contextual typing; resolved signatures for validation. | Ty 6 complete |
| `HirLambdaLiftingTransformerPass` generated declarations, parameters, and signatures | Binding identities and resolved capture/signature types through necessary HIR transformations. | Ty 2-6 complete |

`TyParam.ty()` and MIR signature parameter types remain value types; they are not HIR parameter-expression completion types.

### Callable signatures

| Current consumer | Required information | Migration |
|---|---|---|
| `HirTyCommonVisitorPass.visitCall` target reads | `HirExprs.getCallableSignature` supplies the resolved callee `TyFn` and nominal call result, independent of target/argument completion. | Ty 7 complete |
| Call target reads in `HirInferredFunctionSignatureVisitor`, `HirTupleContextVisitorPass.linkArguments`, `HirIntegerLiteralTypingVisitorPass`, `HirFunctionValidationVisitorPass`, `HirTupleValidationVisitorPass` | `HirExprs.getCallableSignature` supplies resolved parameters for argument binding, contextualization, and validation. | Ty 7 complete |
| `HirFunctionValidationVisitorPass.check` actual-type reads, including `visitNewByBlockField` | `HirExprs.getCallableValueTy` supplies function-valued information for diagnostics without interpreting it as reachable completion. Field constraints come from the enclosing construction's current layout. | Ty 7-8 complete |

### Aggregate/member shapes

| Current consumer | Required information | Migration |
|---|---|---|
| `HirTyCommonVisitorPass.visitNewByCtor`, `visitNewByBlock`; initializer contexts and checks in function/integer/tuple/array passes | `HirExprs.getConstructionTargetLayout` supplies available construction layouts. `HirExprs.getInitializerField` selects the expected field independently of completion. | Ty 8 complete |
| `HirTyCommonVisitorPass.visitDotAccess`; member-write checks in `HirTupleValidationVisitorPass.requireWritable`; callable member lookup | `HirExprs.getMemberField` selects members against `HirExprs.getMemberReceiverLayout`. `HirExprs.getMemberTy` reads the selected type; write validation reads the receiver layout. | Ty 8 complete |
| `HirTyCommonVisitorPass.visitArrayAccess`; array-access checks in tuple validation | `HirExprs.getIndexingReceiverTy`, `HirExprs.getIndexingAccessorTy`, and `HirExprs.getIndexedTy` separate receiver and element/slice types from completion. | Ty 9 complete |
| `HirTupleAccess.index`, `availableIndex` | The integer literal's direct type and value for tuple-index diagnostics. | Ty 9 complete |
| `HirSpreadShape.fields`; spread precheck in `HirInferredFunctionSignatureVisitor` | Resolved static tuple/struct fields for spread arity, names, and binding, including known layouts after transfers. No layout for a diverging tuple construction. | Ty 10 complete |
| `HirTupleTyping.resolve` | Tuple completion/layout produced by typing, not a new universal structural query. Contextual destination layout remains separate metadata. | Ty 14 complete |

### Return/break collection

| Current consumer | Required information | Migration |
|---|---|---|
| `HirFunctionReturnTyping`, called by `HirTyCommonVisitorPass.visitFunction` | Shared `HirBodyResultTyping` unions reachable returns with resolved body completion; terminal assignments/declarations contribute `VOID`. | Ty 11-12 complete |
| `HirTyCommonVisitorPass.visitProgram` | Shared body-result policy for the external script result, distinct from body completion. | Ty 12 complete |
| `HirTyCommonVisitorPass.visitLoop` | Reachable break payload types from `FindBreakTypesVisitor`; retain existing loop rules during migration. | Ty 13-14 complete |
| `FindReturnTypesVisitor`, `FindBreakTypesVisitor` | Reachable payload completion via `Tys.completionTy`, collected with execution-aware traversal. | Reuse in Ty 11-13 |

`Hir.Program.ty()` is an external return type, not its body's completion type. `Hir.Return.ty()` and `Hir.LoopBreak.ty()` describe the transfer (`DEADEND`), not the payload.

## Type selection boundaries

There are no universal value-type caches or forwarding adapters. Type projections select existing information, not expression contexts: callable signatures, bindings, body results, and available layouts still require their specific queries.

Named context-specific `HirExprs` helpers centralize type selection without performing inference. Production typing, queries, lowering, tree printing, and type assertions do not consume the universal accessor.

`HirTransformer.transformAssignment` and lambda-lifted calls retain completion types. Generated bindings and signatures retain their resolved types; callable results and construction layouts derive from those authoritative sources instead of duplicate caches.

`ToStringTreeHirVisitor` distinguishes completion, source annotations, resolved bindings, contextual tuple destinations, and the named array/range/member/indexing metadata. Type assertions use those specific concepts.

Ty 1 changes no compiler behavior. Ty 11-12's approved function and script rules use `VOID` for terminal assignments/declarations and merge reachable returns with normal fallthrough. These contracts do not authorize changing `=` to return a value or adding syntax.
