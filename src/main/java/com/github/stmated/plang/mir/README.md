# MIR

The Mid-Level Intermediate Representation takes the hierarchical THIR (Typed High-Level Intermediate Representation)
and converts it into a CFG (Control Flow Graph).

This is so that we have one more level where we manage our own structure before we send it down to be converted into LLVM IR, and then finally the executable byte code.

The MIR should have as few, or none, of the syntactical sugar of the higher levels of representations.
It does not know the concept of loops or other flow control, and instead uses jumps.
