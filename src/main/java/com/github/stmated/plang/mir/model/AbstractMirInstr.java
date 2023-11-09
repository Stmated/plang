package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.mir.MirIdentifierId;
import com.github.stmated.plang.ty.Ty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public abstract class AbstractMirInstr implements MirInstr {

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private MirIdentifierId name;

  @Override
  public MirIdentifierId name() {
    return name;
  }

  public void name(MirIdentifierId iid) {
    this.name = iid;
  }

//  public abstract Ty ty();

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }
}
