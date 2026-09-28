# MIR

MIR lowers typed HIR into a module of functions, each with its own control-flow
graph. Function calls are not CFG edges. Definitions and external declarations
belong to the module, not to executable instruction lists.

## Representation

* A function owns its signature, locals, entry block and basic blocks.
* A block contains ordered instructions and exactly one separate terminator:
  jump, conditional branch, return, or unreachable.
* Successors are derived from that terminator. Predecessors are computed by
  analyses, rather than stored as another mutable description of the graph.
* An operand is an immutable temporary value, constant, function reference or
  the payload-free `Void` value. Reading an operand never executes an expression.
* A place is a local storage location or a field/element location. `Load` produces
  a value; `Store` writes a value and produces no result.
* Source declaration identity selects a local. Names are diagnostic labels only.

Blocks are not lexical scopes and do not have result types. A source block can
lower into part of one basic block or into many basic blocks. Loops become jumps;
there are no loop instructions.

## Lowering contracts

Evaluation is left-to-right in source order. Named arguments and field
initializers are evaluated before their resulting values are reordered into
parameter/layout order. Logical AND/OR use short-circuit control flow.

Executable instructions belong to exactly one block. Consumers must not
recursively emit operands, infer loads, repair CFG edges, or infer function
return types from traversal order.

`MirVerifier` validates completed modules before backend lowering, including
ownership, termination, types, definition dominance and definite initialization
of locals. Structurally unreachable source statements are diagnosed while
lowering, rather than emitted after a terminator.
