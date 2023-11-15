package com.github.stmated.plang.mir.model;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirNodeEntry extends MirNode {

  MirFnSignature fnSignature;

  public MirNodeEntry(String name, MirFnSignature fnSignature) {
    super(name);
    this.fnSignature = fnSignature;
  }
}
