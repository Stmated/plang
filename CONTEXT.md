# Inf type contexts

Inf distinguishes what an expression can produce from the constraints and structural information used to type it.

## Language

**Completion type**:
The type produced by normal expression completion. `VOID` means completion without a value; `DEADEND` means no normal completion.
_Avoid_: Runtime type, result type when referring only to normal completion

**Resolved structural type**:
The aggregate layout or selected value type established by typing, independently of whether evaluating the expression completes. It is not a control-transfer payload or evidence of reachability.
_Avoid_: Developer intent type, hypothetical runtime value

**Declared constraint**:
A source annotation that restricts a binding, parameter, array element, or function result. Omitted information remains an inference placeholder even when the corresponding value type has been resolved.
_Avoid_: Inferred type, expected type when referring to source annotations

**Expected type**:
A destination type supplied by the surrounding use site for contextual typing. For fresh tuples it also determines the destination layout.
_Avoid_: Resolved structural type, contextual flag

**Binding type**:
The resolved type of a value bound to a declaration or parameter, distinct from the completion of the declaration itself.
_Avoid_: Declaration result type

**Exit payload type**:
The completion type of a reachable value carried by `return` or `break`, belonging to its enclosing function/program or loop.
_Avoid_: Completion type of the transfer expression

**Body result type**:
The combination of reachable return payload types and normal body completion, used for function and external program results.
_Avoid_: Body completion type
