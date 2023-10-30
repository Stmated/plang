package com.github.stmated.plang.mir;

public record MirIdentifierId(String name, int id) {

  public String getUniqueName() {
    return STR."\{name()}_\{id()}";
  }

  @Override
  public String toString() {
    return this.getUniqueName();
  }
}
