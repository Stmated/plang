package org.inf.ty;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record TyValueNumberScaled(BitWidth width, int scale, boolean signed, Set<TyFlags> flags) implements TyValueNumber {

  @Override
  public TyValueKind getValueKind() {
    return TyValueKind.DECIMAL;
  }

  @Override
  public byte radix() {
    return 10;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TyValueNumberScaled that = (TyValueNumberScaled) o;
    return Objects.equals(width, that.width) && scale == that.scale && signed == that.signed;
  }

  @Override
  public int hashCode() {
    return Objects.hash(width, scale, signed);
  }
}
