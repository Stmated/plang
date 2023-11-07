package com.github.stmated.plang.mir;

import lombok.Value;

@Value
public class MirIdentifierId {

  String name;
  String label;
  int id;

  public String getUniqueName() {
    return STR."\{name()}_\{id()}";
  }

  public String label() {

    if (label != null) {
      return label;
    }

    return name;
  }

  @Override
  public String toString() {
    return this.getUniqueName();
  }
}
