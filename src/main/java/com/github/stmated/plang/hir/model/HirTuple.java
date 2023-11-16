package com.github.stmated.plang.hir.model;

import java.util.Arrays;

public record HirTuple(HirTupleKeyValue[] children) implements HirExpression {

  @Override
  public String toString() {
    return STR."(\{String.join(", ", Arrays.stream(children).map(Record::toString).toList())})";
  }
}
