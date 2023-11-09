package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Load the value of a previous store.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrLoad extends AbstractMirInstr {

  MirInstrStore store;

  @Override
  public Ty ty() {
    return store.ty();
  }
}
