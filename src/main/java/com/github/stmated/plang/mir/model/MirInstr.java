package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.mir.MirIdentifierId;
import com.github.stmated.plang.ty.Ty;
import lombok.Data;

public interface MirInstr {

  MirIdentifierId name();

//  void name(MirIdentifierId iid);

  default boolean isTerminal() {
    return false;
  }

  default String toShortString() {
    return this.toString();
  }

  Ty ty();
}
