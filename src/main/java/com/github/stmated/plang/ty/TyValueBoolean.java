package com.github.stmated.plang.ty;

import java.util.Objects;

public record TyValueBoolean() implements TyValue {

  @Override
  public TyValueKind getValueKind() {
    return TyValueKind.BOOLEAN;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getValueKind());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TyValue that = (TyValue) o;
    return getValueKind() == that.getValueKind();
  }
}
