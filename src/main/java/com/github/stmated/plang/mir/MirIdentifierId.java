package com.github.stmated.plang.mir;

import java.util.Objects;
import lombok.Value;

@Value
public class MirIdentifierId {

  String name;
  String label;
  int id;

  public MirIdentifierId(String name, String label, int id) {
    this.name = Objects.requireNonNull(name);
    this.label = label;
    this.id = id;
  }

  public String getUniqueName() {

    if (id == 0) {
      return name;
    }

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
