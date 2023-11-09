package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrBinaryOperation extends AbstractMirInstr {

  MirInstr lhs;
  MirBinaryOperationKind kind;
  MirInstr rhs;
  Ty ty;

  @Override
  public String toString() {
    return STR."\{lhs.toShortString()} \{kind} \{rhs.toShortString()}";
  }

  public MirInstr lhs() {
    return lhs;
  }

  public MirBinaryOperationKind kind() {
    return kind;
  }

  public MirInstr rhs() {
    return rhs;
  }

  @Override
  public Ty ty() {
    return ty;
  }
}
