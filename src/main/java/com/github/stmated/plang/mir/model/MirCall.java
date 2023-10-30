package com.github.stmated.plang.mir.model;

public record MirCall(MirOperand target, MirOperand[] arguments) implements MirInstruction, MirOperand {

}
