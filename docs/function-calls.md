# Function calls without parentheses

Calls can omit parentheses around positional arguments:

```inf
val sum = (a: int, b: int) => a + b;
val answer = sum 20, 22;
```

Commas separate sibling arguments. Adjacent calls nest to the right, and the
innermost implicit call owns subsequent commas within its delimiter scope.

| Source | Equivalent explicit call |
|---|---|
| `f x, y` | `f(x, y)` |
| `f g x, y` | `f(g(x, y))` |
| `outer(inner x, y)` | `outer(inner(x, y))` |
| `outer((inner x), y)` | `outer(inner(x), y)` |
| `f x + y, z` | `f(x + y, z)` |
| `service.call x, y` | `service.call(x, y)` |

Grouping never depends on function signatures. An argument mismatch is an
error, not a reason to try another grouping.

## Boundaries

- Newlines are whitespace, including inside implicit calls. Use `;` between
  statements; formatting alone does not separate calls.
- Closing parentheses, brackets, braces, and end of input end the current
  argument scope. `else` belongs to its enclosing conditional.
- Each conditional slot and loop header/body is one ordinary expression.
  A later expression cannot become an implicit argument inside that slot.
  Use explicit calls, or group implicit calls inside parentheses or blocks.
- Ordinary postfix parsing still applies. Separate adjacent parenthesized
  conditional slots with `then`: `if (predicate x) then (f y) else { g z }`.
- `if (true) 1 2;` contains a conditional returning `1`, followed by a separate
  literal `2`; the latter still participates in the enclosing scope's usual
  implicit-return rules.
- A bare function name remains a reference. Call zero-argument functions with `f()`.

Existing postfix syntax takes precedence: `f (x)` is an explicit call and
`f [x]` is indexing, regardless of whitespace. Use explicit calls for an array
as the first argument (`f([x])`), negative first arguments (`f(-1)`), and
block or lambda arguments. `f - 1` remains subtraction.
