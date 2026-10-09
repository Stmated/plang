package org.inf.util;

import jakarta.annotation.Nonnull;
import org.inf.ty.Ty;
import org.inf.ty.TyStruct;

/// An on-demand selection from a resolved aggregate layout, not stored on expression nodes.
public record ResolvedField(@Nonnull TyStruct owner, int index) {

  public Ty ty() {
    return owner.fields()[index].ty();
  }
}
