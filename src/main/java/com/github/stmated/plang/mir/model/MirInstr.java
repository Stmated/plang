package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.mir.MirIdentifierId;
import com.github.stmated.plang.ty.Ty;
import lombok.Data;

@Data
public abstract class MirInstr {

  private MirIdentifierId name;

  boolean isTerminal() {
    return false;
  }

  String toShortString() {
    return this.toString();
  }

  public abstract Ty ty();

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }
}
