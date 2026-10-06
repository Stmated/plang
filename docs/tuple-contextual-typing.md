# Contextual tuple typing

An expected tuple type guides fresh constructions in variable declarations,
variable reassignment, function arguments, and explicit or implicit returns.
Context reaches nested constructions, conditional branches, and final block
expressions.

```inf
val make = (): (uint8, uint8) => (1, 2);
val read = (t: (uint8, uint8)) => t[0] + t[1];
val t: (uint8, uint8) = if (true) then (3, 4) else { (5, 6) };
read(t);
```

## Integer slots

Unsuffixed integer literals adopt the expected slot type only when their value
fits. `255` fits `uint8`; `256` and `-1` do not. Explicitly typed literals,
including `1i32`, `1u8`, and `1L`, retain their source type.

Other integer elements may widen only when every source value fits:

| Conversion | Allowed |
|---|---|
| `uint8` to `uint16` | Yes |
| `int8` to `int16` | Yes |
| `uint8` to `int16` | Yes |
| `int8` to `uint16` | No: negative values cannot fit |
| `uint8` to `int8` | No: unsigned maximum cannot fit |
| `int32` to `uint8` | No: narrowing |

```inf
val x = 255u8;
val t: (int16, (uint8,)) = (x, (1,));
t[0]; // 255
```

Arithmetic keeps its existing inference: `(1 + 2,)` cannot satisfy `(uint8,)`,
but can satisfy `(int64,)` by widening the resulting integer. Float and other
non-integer slot types must match exactly.

## References and limits

Existing tuple objects are never rebuilt or reinterpreted as another layout,
even when their integer slots could widen. With `val original = (1u8,)`,
`val widened: (uint16,) = original` is an error. Explicit construction
`val widened: (uint16,) = (original[0],)` works.

The same restriction applies to nested tuple references. Matching nested tuples,
arrays, and structs remain shared; contextual construction does not copy them.
Struct-field initializers and array-element contexts retain exact-match rules.

Elements execute left-to-right exactly once. Exiting an element skips later
elements and the unfinished tuple's allocation; later source elements remain statically checked.
For [named and mixed tuples](named-tuples.md), [fresh tuple matching](tuple-matching.md) selects each element's expected slot: labels match first, then unlabeled entries fill remaining positions. Destination order does not change source evaluation order.
Tuple spreading and general contextual inference remain unsupported.

## Compiler responsibilities

Context linking records destination layouts on fresh tuple nodes. Literal typing adapts fitting unsuffixed integers; common typing then resolves expression types before the conversion pass inserts lossless slot conversions. Passes communicate only through the HIR tree, not shared resolution state.
