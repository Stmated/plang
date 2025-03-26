package org.inf.ty;

import java.util.EnumSet;
import java.util.Objects;
import lombok.Builder;

@Builder(toBuilder = true)
public record TyValueNumberPrecisioned(
  RealKind kind,
  BitWidth width,
  int precision,
  boolean signed,
  EnumSet<TyFlags> flags
) implements TyValueNumber {

  @Override
  public TyValueKind getValueKind() {

    return switch (kind) {
      case DOUBLE -> TyValueKind.DOUBLE;
      case FLOAT -> TyValueKind.FLOAT;
    };
  }

  @Override
  public byte radix() {
    return 10;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TyValueNumberPrecisioned that = (TyValueNumberPrecisioned) o;
    return Objects.equals(width, that.width) && precision == that.precision && signed == that.signed && kind == that.kind
           && Objects.equals(flags, that.flags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, width, precision, signed, flags);
  }
}
