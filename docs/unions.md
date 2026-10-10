# Union types

`|` combines types in annotations and named type definitions. Between integer values, it remains bitwise OR.

```inf
val NumberOrFlag = int | bool;
val Alias = NumberOrFlag;
val value: Alias = 7;
val identity = (v: NumberOrFlag): NumberOrFlag => v;
identity(value)
```

```inf
val bits = 1 | 2;                  // integer value: 3
val values = [7, true;int | bool;2];
val pair: (int | bool, bool) = (7, true);
```

Union alternatives must denote types, not values or calls. A type alias is not a runtime value.

```inf
val sample = 7;
val invalid: sample | bool = true; // error: sample is a runtime binding
val invalid: int | 7 = 7;          // error: 7 is a value
val invalid = int | 7;            // error: mixed type/value operands
```

Direct chains are flattened in source order. Resolved aliases are flattened when constructing the represented type. Duplicate alternatives can simplify to a single type: `int | int` represents `int`.

## Compiler representation

`Hir.Union.elements` retains the source expressions, including alias references. `unionTy` holds the represented type and refreshes during typing; it starts as `Ty.INFER` and can resolve to a `TyUnion` or a simplified single type.

`Union.ty()` is `Ty.VOID`: the type expression does not produce a runtime value. An enclosing `DynamicTy` retains the source expression and caches its annotation constraint separately. Runtime union values use the existing tagged MIR representation.

Explicit annotation unions are raised from AST to HIR. Outside annotations, `BIT_OR` remains provisional until name resolution distinguishes type definitions from integer operands. Union aliases emit no runtime locals and require no closure captures.

The Java result bridge cannot export union variants containing array or function pointers. These variants can still be used within Inf.
