# MIR

The Mid-Level Intermediate Representation takes the hierarchical THIR (Typed High-Level Intermediate Representation)
and converts it into a CFG (Control Flow Graph).

This is so that we have one more level where we manage our own structure before we send it down to be converted into LLVM IR, and then finally the executable byte code.

The MIR should have as few, or none, of the syntactical sugar of the higher levels of representations.
It does not know the concept of loops or other flow control, and instead uses jumps.

## Basic concept of the MIR
* Graph begins with a `start` `node`.
* Each `node` has a list of `predecessor` and `successor` `edges`.
* Each `edge` has a `source` and a `destination` `node`
* Each `node` has a list of `instructions`.
* Each `instruction` may make use of zero or more `operands`.
* Each `operand` is either the result of another `instruction` or a `literal`.
* Each `node` must end with (and **only** end with) an `instruction` that branches to one or more new `edges` or *terminate* with a terminal instruction (such as `return`).
  * There will never be a `return` in the middle of `instructions`. All `instructions` of a node must always run (unless terminates exceptionally). 

The graph does not follow any function calls, it will only resolve its local CFG.
It is up to other kinds of optimization to figure out any inlining or other advanced techniques.
