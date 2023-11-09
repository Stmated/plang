package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrJump extends AbstractMirInstr {

  MirNode node;

  @Override
  public boolean isTerminal() {
    return true;
  }

  @Override
  public String toString() {
    return STR."Jump To '\{node.name()}'";
  }

  @Override
  public Ty ty() {
    return Ty.VOID;
  }
}
