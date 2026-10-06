# Function-valued fields

Tuple and struct fields can store functions. Call the field with ordinary positional, named, or spread arguments.

```inf
val pair = (a: int, b: int) => a * 10 + b;
val service = (call = pair,);
service.call(1, 2);           // 12
service.call b = 2, a = 1;    // 12
service.call(...(1, 2));      // 12

val Fn = (v: int): int;
val S = struct { val fn: Fn; };
val s = new heap S { fn = (v) => v * 2; };
s.fn(5);                     // 10
```

The receiver and function field evaluate once, before arguments. Arguments evaluate left-to-right in source order, even when named arguments reorder parameter positions. Nested fields and returned receivers work: `outer.inner.fn(5)` and `make().fn(5)`. Group computed function values: `(service.calls[0])(5)` or `(service.make())(5)`.

## Lambda parameter inference

An expected function signature supplies omitted lambda parameter types by position. Parameter names remain those written in the lambda; named calls through a typed field use the field's declared signature.

```inf
val Fn = (value: int): int;
val f: Fn = (x) => x * 2;

val S = struct { val fn: Fn; };
val s = new heap S { fn = (x) => x * 2; };
s.fn(value = 5);              // 10
s.fn = (x) => x * 3;
s.fn(5);                     // 15

val apply = (fn: Fn, value: int) => fn(value);
apply((x) => x * 2, 5);       // 10
```

Context also reaches lambdas returned by blocks or conditional branches. Explicit parameter types are not overwritten; incompatible parameter counts, variadic shapes, and types are errors. Without an expected signature, lambda parameter types still require annotations.

Use-site signatures are resolved before lambda parameter linking. Linking fills omitted parameter annotations. A stateless parameter check then compares the resolved declarations before dependent bodies are typed, preserving parameter diagnostics. Ordinary type resolution determines expression, call, and return types; complete function compatibility is checked afterward. Passes communicate only through the HIR tree.

Function compatibility is checked at assignments, field initializers, callback arguments, and explicit or implicit function returns. These checks compare resolved types and apply to already-typed function values as well as inline lambdas; they do not infer missing types.

Use a signature alias such as `Fn` for struct fields; inline function-type annotations are not yet supported. Captured lambdas cannot yet be stored in fields: their lifted signatures require extra capture arguments, and incompatible function-pointer conversions are rejected. Fresh spread tuples containing untyped callback lambdas and function-type aliases inside tuple annotations remain unsupported.
