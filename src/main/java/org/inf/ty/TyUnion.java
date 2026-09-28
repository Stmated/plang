package org.inf.ty;

import java.util.Arrays;

/** The types array is shared and must not be mutated after construction. */
public record TyUnion(Ty[] types) implements Ty {

  public TyUnion {
    if (types == null || types.length < 2) {
      throw new IllegalArgumentException("A union must be of two or more types");
    }

    for (final var type : types) {
      if (type == null) {
        throw new IllegalArgumentException("No kind is allowed to be null");
      }
    }
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof TyUnion union && Arrays.equals(types, union.types);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(types);
  }

  @Override
  public String toString() {


    final var typeStrings = new String[types.length];
    for (var i = 0; i < types.length; i++) {
      typeStrings[i] = types[i].toShortString();
    }

    return "(" + String.join(" | ", typeStrings) + ")";
  }
}
