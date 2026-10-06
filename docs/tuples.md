# Positional tuples

Tuples preserve comma-based boundaries through HIR and type checking:

| Source | Shape |
|---|---|
| `(x)` | Grouped expression |
| `(x,)` | Singleton tuple |
| `(x, y)` or `(x, y,)` | Pair |
| `((x, y),)` | Singleton containing a pair |
| `((x, y))` | Grouped pair |

Only commas owned by the current parentheses mark that group's shape.
In `outer(inner x, y)`, the comma belongs to the implicit `inner` call.
In `outer((inner x), y)`, it belongs to the outer argument list.
A trailing comma after an implicit-call argument remains an error;
use `((inner x),)` for a singleton containing that call.

Semicolon-separated sequences and loop headers retain their existing behavior.
Function parameter lists and explicit argument lists retain their boundaries.
`()` is unchanged; first-class empty tuples are deferred.

Calls consume only their outer argument list: `f(x, y)` has two arguments,
while `f((x, y))` has one tuple argument. With `t = (x, y)`, `f t` also passes
one tuple. Whitespace before parentheses does not change explicit call syntax.
Tuples do not implicitly spread into arguments. Use [explicit spreading](call-spreading.md): `f(...t)` or `f ...t`.

## Types and flow

Slot types are inferred independently, including nested tuples. Annotations
such as `(int, bool)`, `(int,)`, and `((int, bool), int)` are supported in
bindings, parameters, and returns.

```inf
val t: (int, bool) = (10, true);
val identity = (value: (int, bool)): (int, bool) => value;
identity(t);
```

Existing tuple objects require the same recursive shape and slot types. Whether a
numeric width was explicit is ignored, but its width and signedness must match.
Fresh constructions support [contextual integer typing and widening](tuple-contextual-typing.md)
in bindings, arguments, and returns: `(1, 2)` can satisfy `(uint8, uint8)`.
They also support [positional-to-named and reordered matching](tuple-matching.md) under an expected tuple type.
Conversions that change an existing tuple's layout remain unsupported.
Tuples and structs with exactly matching ordered fields are
[structurally compatible](named-tuples.md#types-and-references).

A normally completing `void` expression cannot occupy a slot. An element that
returns or otherwise never completes makes the whole construction non-returning.
Later elements remain statically checked, but do not contribute executed returns.
A block that performs side effects and finishes with a value is a valid element.

## Construction and reads

Positional tuples can be constructed and read at runtime:

```inf
val t = (10, true);
t[0]; // 10
val nested = ((10, true),);
nested[0][1]; // true
```

Indices are zero-based integer literals, including existing radix and width
spellings such as `0x1` and `1u8`. Negative, out-of-range, and non-integer indices
are errors. Computed indices such as `t[i]` and `t[0 + 1]` are deferred.

Elements execute left-to-right exactly once. A non-returning element skips the
remaining elements; a partially returning element still permits construction
on paths that continue.

Assignment shares the tuple object rather than copying its slots, like structs
and arrays. Tuple slot writes (`t[0] = x`, `t[0] += x`) are rejected for now.
An array or struct contained in a slot retains its existing mutation behavior.

## Storage and function boundaries

Bindings, reassignment, array elements, struct fields, parameters, and returns share the tuple reference. 
Reassignment replaces that reference; aliases still refer to the original tuple. 
Existing references require matching shapes and slot types. Fresh constructions can
use contextual typing in variable bindings/reassignment, arguments, and returns;
struct-field and array-element contexts still require exact matches.

```inf
val make = (x: int): (int, bool) => (x, true);
val identity = (t: (int, bool)): (int, bool) => { return t; };
var t = make(10);
val original = identity t;
t = make(20);
original[0]; // 10

val S = struct { val pair: (int, bool); };
val instance = new heap S { pair = t; };
val values = [instance.pair];
instance.pair[0]; // 20
values[0][1]; // true
```

Returning tuple to the Java host remains unsupported; return a scalar instead.

Tuple entries have optional labels as metadata, separately from call arguments.
See [named and mixed tuples](named-tuples.md) for label syntax and reads.
