package com.github.stmated.plang.mir.model;

public interface MirOperand {

  default public String toShortString() {
    return this.toString();
  }
}
