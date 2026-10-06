# Matching fresh tuples

An expected tuple type maps fresh entries into its declared layout. Explicit labels match by name first; unlabeled entries then fill unclaimed destination slots in source order. Fully positional constructions match by position.

```inf
val fn4 = (t: (p1: uint8, p2: uint8)): uint16 => t.p1 + t.p2;
fn4((1, 2));
fn4((p1 = 1, p2 = 2));
fn4((p2 = 2, p1 = 1));
fn4((2, p1 = 1)); // p1 = 1, p2 = 2

val t: (a: uint8, bool, b: uint16) = (true, b = 1000, a = 255);
t.a;  // 255
t[1]; // true
t.b;  // 1000
```

The destination controls named and indexed reads. Entries still evaluate left-to-right exactly once in **source order**, not destination order. An exiting entry skips later evaluation and allocation.

Unknown or duplicate labels and missing or extra entries are errors. Source labels cannot be silently discarded, even for a fully positional destination.

Matching applies in tuple bindings/reassignment, function arguments, explicit/implicit returns, nested fresh tuples, conditional branches, and final block expressions. [Contextual integer typing](tuple-contextual-typing.md) uses each matched destination slot.

## Existing references and limits

Only fresh constructions gain these matching rules. Existing tuple references require the same ordered labels and recursive slot types; they are not renamed, reordered, widened, or copied:

```inf
val original = (1u8, 2u8);
// Error: existing positional reference does not match named slots.
val named: (p1: uint8, p2: uint8) = original;
// Explicit fresh construction is allowed.
val fresh: (p1: uint8, p2: uint8) = (original[0], original[1]);
```

Nested existing references remain shared when their exact layouts match. [Tuple/struct compatibility](named-tuples.md) and struct-field/array-element contexts retain exact-layout rules. Tuple-slot writes remain unsupported. `fn4((1, 2))` passes one tuple, not two arguments; [explicit call spreading](call-spreading.md) uses `...`.
