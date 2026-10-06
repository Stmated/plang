package org.inf.ty;

import org.inf.ty.util.Tys;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public interface Ty {

  Ty INFER = Tys.intern(new TyNamed("INFER"));
  Ty INVALID = Tys.intern(new TyNamed("INVALID"));
  Ty UNKNOWN = Tys.intern(new TyNamed("UNKNOWN"));
  Ty DEADEND = Tys.intern(new TyNamed("DEADEND"));
  Ty VOID = Tys.intern(new TyNamed("VOID"));

  BitWidth BW_8 = new BitWidth(8, false);
  BitWidth BW_16 = new BitWidth(16, false);
  BitWidth BW_32 = new BitWidth(32, false);
  BitWidth BW_64 = new BitWidth(64, false);

  BitWidth BW_EXPLICIT_8 = new BitWidth(8, true);
  BitWidth BW_EXPLICIT_16 = new BitWidth(16, true);
  BitWidth BW_EXPLICIT_32 = new BitWidth(32, true);
  BitWidth BW_EXPLICIT_64 = new BitWidth(64, true);

  Set<TyFlags> NO_FLAGS = Collections.unmodifiableSet(EnumSet.noneOf(TyFlags.class));

  TyValueNumberInteger SHORT = Tys.intern(new TyValueNumberInteger((byte) 10, BW_16, true, NO_FLAGS));
  TyValueNumberInteger USHORT = Tys.intern(new TyValueNumberInteger((byte) 10, BW_16, false, NO_FLAGS));

  TyValueNumberInteger INTEGER = Tys.intern(new TyValueNumberInteger((byte) 10, BW_32, true, NO_FLAGS));
  TyValueNumberInteger UINTEGER = Tys.intern(new TyValueNumberInteger((byte) 10, BW_32, false, NO_FLAGS));
  TyValueNumberInteger LONG = Tys.intern(new TyValueNumberInteger((byte) 10, BW_64, true, NO_FLAGS));
  TyValueNumberInteger ULONG = Tys.intern(new TyValueNumberInteger((byte) 10, BW_64, false, NO_FLAGS));

  TyValueNumberInteger CHAR = Tys.intern(new TyValueNumberInteger((byte) 10, BW_8, false, NO_FLAGS));

  TyValueNumberInteger INTEGER_BINARY = Tys.intern(new TyValueNumberInteger((byte) 2, BW_32, true, NO_FLAGS));
  TyValueNumberInteger INTEGER_OCTAL = Tys.intern(new TyValueNumberInteger((byte) 8, BW_32, true, NO_FLAGS));
  TyValueNumberInteger INTEGER_HEX = Tys.intern(new TyValueNumberInteger((byte) 16, BW_32, true, NO_FLAGS));

  TyValueNumberPrecisioned FLOAT16 = Tys.intern(new TyValueNumberPrecisioned(RealKind.FLOAT, BW_16, 4, true, NO_FLAGS));
  TyValueNumberPrecisioned FLOAT = Tys.intern(new TyValueNumberPrecisioned(RealKind.FLOAT, BW_32, 7, true, NO_FLAGS));
  TyValueNumberScaled DECIMAL = Tys.intern(new TyValueNumberScaled(new BitWidth(128, false), 10, true, NO_FLAGS));
  TyValueNumberPrecisioned DOUBLE = Tys.intern(new TyValueNumberPrecisioned(RealKind.DOUBLE, BW_64, 16, true, NO_FLAGS));
  TyValueNumberPrecisioned FLOAT64 = Tys.intern(Ty.DOUBLE);

  TyValueBoolean BOOLEAN = Tys.intern(new TyValueBoolean());

  TyValueString STRING = Tys.intern(new TyValueString());

  default Ty intern() {
    return Tys.intern(this);
  }

  default String toShortString() {
    return this.toString();
  }

  record TyNamed(String name) implements Ty {

    @Override
    public String toString() {
      return this.name;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final var that = (TyNamed) o;
      return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }
}
