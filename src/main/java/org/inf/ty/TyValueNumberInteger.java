package org.inf.ty;

import lombok.Builder;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@Builder(toBuilder = true)
public record TyValueNumberInteger(byte radix, BitWidth width, boolean signed, Set<TyFlags> flags) implements TyValueNumber {

  @Override
  public TyValueKind getValueKind() {
    return TyValueKind.INTEGER;
  }

  @Override
  public String toString() {
    if (width.explicit()) {
      return (signed ? "" : "u") + "int" + width.value() + (radix == 10 ? "" : "base" + radix);
    }
    return (signed ? "" : "u") + "int(" + width.value() + "?)" + (radix == 10 ? "" : "base" + radix);
  }

  @Override
  public boolean isConstant() {
    return flags.contains(TyFlags.CONSTANT);
  }

  @Override
  public boolean isImmutable() {
    return flags.contains(TyFlags.IMMUTABLE) || flags.contains(TyFlags.CONSTANT);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TyValueNumberInteger that = (TyValueNumberInteger) o;
    return radix == that.radix
      && Objects.equals(width, that.width)
      && signed == that.signed
      && Objects.equals(flags, that.flags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(radix, width, signed, flags);
  }
}
