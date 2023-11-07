package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirReturn extends MirInstr {

  MirInstr instr;

  public MirReturn(MirInstr instr) {
    this.instr = Objects.requireNonNull(instr, "Return operand not allowed to be null");
  }

  @Override
  public boolean isTerminal() {
    return true;
  }

  @Override
  public String toString() {
    return STR."return \{instr.toShortString()}";
  }

  @Override
  public Ty ty() {
    return instr.ty();
  }

  public MirInstr instr() {
    return instr;
  }
}
