# Named and mixed tuples

Use `name = value` for named value entries and `name: Type` in annotations.
Labeled and unlabeled entries may appear in any order. Labels are metadata:
they neither resolve nor assign a surrounding variable.

```inf
val t: (a: int, bool, b: int) = (a = 10, true, b = 20);
t.a;    // 10
t[1];   // true
t.b;    // 20
t[2];   // 20

val nested = (outer = (inner = 30,),);
nested.outer.inner; // 30
nested[0][0];       // 30
```

Both named singleton values and types require a trailing comma: `(a = 10,)`
and `(a: int,)`. `(a = 10)` remains an ordinary grouped assignment.
Duplicate labels are errors, including in unreachable entries.

Direct entries of comma-marked tuples and direct [call arguments](call-arguments.md) interpret `=` as a label.
Assignments still return `void`, so `(a = (foo = 10),)` is not a valid tuple
value. Use a block when an entry needs an assignment followed by a value:

```inf
var foo = 0;
val t = (a = { foo = 10; foo; },);
```

Typed value entries such as `(a: uint8 = 20,)` are deferred. Instead, annotate
the tuple: `val t: (a: uint8,) = (a = 20,)`.

## Types and references

Existing references require the same field count, order, labels, and recursive slot types.
[Fresh constructions](tuple-matching.md) match explicit labels first, then fill remaining slots positionally. Named entries may be reordered into the expected layout without reordering source evaluation.
[Contextual tuple typing](tuple-contextual-typing.md) applies to each matched slot.

Tuples and structs with exactly matching layouts are interchangeable, sharing
the original object without copying or reallocating:

```inf
val S = struct { val a: int; val b: bool; };
val t = (a = 10, b = true);
val s: S = t;
s.a = 20;
val again: (a: int, b: bool) = s;
again[0]; // 20; t.a observes the same change
```

Compatibility does not widen existing slots or reorder fields. A fresh tuple
assigned to a struct type must already match; struct contexts do not infer
tuple integer conversions.

Named and mixed tuples retain left-to-right, exactly-once evaluation and shared
references across bindings, aggregates, parameters, and returns.
`t[index]` counts every slot, including labeled slots; indices remain integer
literals. Writes through tuple-typed slots are deferred, both `t.a = x` and
`t[0] = x`, including compound assignments. Struct-typed views and contained
arrays/structs retain their existing mutation behavior.

Calls keep their existing boundaries: `f((a = 10,))` passes one tuple;
`f(a = 10)` is a named call argument. The previous `f(a: 10)` spelling is rejected. Tuples are not implicitly spread.
