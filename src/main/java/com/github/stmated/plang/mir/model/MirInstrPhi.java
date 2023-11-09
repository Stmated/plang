package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * The phi operand of the result of a branching. That is the result of the final instruction of the pass or fail nodes. The result could be
 * nothing.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrPhi extends AbstractMirInstr {

  MirInstr[] operands;
  MirNode[] from;
  Ty ty;

  @Override
  public String toString() {

    final var strings = new String[operands.length];
    for (var i = 0; i < strings.length; i++) {
      strings[i] = STR."\{operands[i].toShortString()} from \{from[i].toShortString()}";
    }

    return STR."Φ \{String.join(" OR ", strings)}";
  }

  @Override
  public Ty ty() {
    return ty;
  }
}
