package org.inf.mir;

import org.inf.ty.Ty;
import org.inf.ty.TyUnion;

import java.util.Objects;

/** Java-facing representation of a language tagged union. */
public record MirUnionValue(TyUnion type, int variant, Object payload) {
  public MirUnionValue {
    Objects.requireNonNull(type);
    if (variant < 0 || variant >= type.types().length) {
      throw new IllegalArgumentException("Union tag out of range: " + variant);
    }
    if ((type.types()[variant] == Ty.VOID) != (payload == null)) {
      throw new IllegalArgumentException("Only the Void variant has no payload");
    }
  }
}
