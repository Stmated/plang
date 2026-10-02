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
Tuples do not implicitly spread into arguments.

## Types and flow

Slot types are inferred independently, including nested tuples. Annotations
such as `(int, bool)`, `(int,)`, and `((int, bool), int)` are supported in
bindings, parameters, and returns.

```inf
val t: (int, bool) = (10, true);
val identity = (value: (int, bool)): (int, bool) => value;
identity(t);
```

These examples type-check; runtime tuple construction is not implemented yet.

Compatibility requires the same recursive shape and slot types. Whether a
numeric width was explicit is ignored, but its width and signedness must match.
For example, `(1, true)` matches `(int, bool)`, but `(1, 2)` does not match
`(uint8, uint8)`. Tuple conversions and tuple/struct compatibility are deferred.

A normally completing `void` expression cannot occupy a slot. An element that
returns or otherwise never completes makes the whole construction non-returning.
Later elements remain statically checked, but do not contribute executed returns.
A block that performs side effects and finishes with a value is a valid element.

Tuple entries have optional labels as metadata, separately from call arguments.
Named/mixed tuple typing, element access, and runtime lowering remain unsupported.
