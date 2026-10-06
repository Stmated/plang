# Calling functions with tuples and structs

Use `...` to pass aggregate fields as separate arguments. Ordinary calls still pass one aggregate value.

```inf
val pair = (a: int, b: int) => a * 10 + b;
val args = (1, 2);
pair(...args); // 12
pair ...args;  // 12
pair(...(b = 2, a = 1)); // 12
pair(1, ...(2,)); // 12

val S = struct { val b: int; val extra: bool; val a: int; };
val settings = new heap S { b = 2; extra = true; a = 1; };
pair(...settings); // 12; extra is ignored
```

- Named slots match parameter names. Unnamed slots fill the next unbound parameter in source order.
- Unknown spread names are ignored. Unknown explicit names, missing parameters, and excess positional values are errors.
- Extra positional values feed existing varargs, with the usual numeric promotions.
- Spreading is shallow: a nested tuple remains one value. Fresh nested tuples retain contextual typing for tuple parameters.

## Overrides and evaluation

Later values override earlier bindings when either binding comes from a spread:

```inf
pair(...(a = 3, b = 2), a = 1); // 12
pair(a = 3, ...(a = 1, b = 2)); // 12
pair(...(a = 3, b = 4), ...(a = 1, b = 2)); // 12
```

Two successive explicit bindings for the same parameter remain an error, including after an explicit override of a spread. Overridden values still evaluate and are type-checked. Ignored fields still evaluate when constructing the spread operand.

The call target evaluates first, then arguments evaluate left-to-right exactly once. Each spread operand evaluates once; its supplied fields are read before the next argument evaluates. Scalar fields are captured at that point. Nested aggregates retain shared references. A returning or otherwise non-completing operand skips later evaluation and the call.

## Operand boundaries and limits

`...args`, `...source.args`, `...make()`, and `...values[0]` each spread one operand. Group broader expressions: `pair(...(make args))`. Commas separate call arguments; a spread does not consume the following comma.

Member calls support `service.call(...args)` and `service.call ...args`, as well as [ordinary field calls](function-fields.md) such as `service.call(1, 2)`. `service call ...args` is not equivalent: adjacency nests calls to the right and does not introduce member lookup.

Only statically known tuple and struct shapes are supported. Arrays, runtime-length expansion, labeled spreads (`name = ...args`), and spreading inside tuple constructions are unsupported.
