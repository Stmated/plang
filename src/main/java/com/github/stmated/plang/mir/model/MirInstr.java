package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.mir.MirIdentifierId;
import com.github.stmated.plang.mir.MirTyInvestigationContext;
import com.github.stmated.plang.ty.Ty;
import lombok.Data;

public interface MirInstr {

  MirIdentifierId name();

  default boolean isTerminal() {
    return false;
  }

  default String toShortString() {
    return this.toString();
  }

  /**
   * The intrinsic result ty of the instruction itself.
   */
  Ty ty();
}
