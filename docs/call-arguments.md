# Call arguments

Named arguments use `name = value`, like named tuple entries.

```inf
val pair = (a: int, b: int) => a * 10 + b;
pair(b = 2, a = 1); // 12
pair b = 2, a = 1; // Same parenthesis-free call.

val read = (t: (int, bool)) => t[0];
read(t = (10, true)); // One tuple argument, not two arguments.
```

Only direct call arguments interpret `=` as a label. Labels do not resolve or assign surrounding variables. `(a = 1)` outside a call remains a grouped assignment; `f((a = 1))` passes that assignment expression, not a named argument. To assign and then supply a value, use `f(a = { variable = 1; variable; })`.

Arguments evaluate once, left-to-right in source order, even when names reorder their parameter positions. Without spreads, unknown, duplicate, missing, and excess arguments remain errors. Tuples are never implicitly unpacked; [explicit spreading](call-spreading.md) passes tuple or struct fields as separate arguments and permits spread-related overrides.
