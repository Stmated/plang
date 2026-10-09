package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;

/// Checks field names against existing layouts and resolves member completion without caching field selections.
@UtilityClass
public class HirFieldResolution {

  public static void resolve(final Hir.DotAccess access, final boolean availableOnly) {
    final var owner = Tys.getMemberReceiverTy(access.target());
    final var field = Tys.getField(owner, access.name());
    if (!availableOnly && owner != null && field == null) {
      throw new IllegalArgumentException("Unknown field: %s".formatted(access.name()));
    }
    if (!availableOnly && owner == null && !Tys.isInferred(access.target().ty())
      && access.target().ty() != Ty.DEADEND) {
      throw new UnexpectedExpressionException(access.target());
    }
    final var memberTy = field == null ? Ty.INFER : field.ty();
    access.ty(access.target().ty() == Ty.DEADEND ? Ty.DEADEND : memberTy);
  }

  public static void resolve(final Hir.NewByBlock construction, final boolean availableOnly) {
    final var owner = Tys.getConstructionTargetTy(construction.target());
    for (final var initializer : construction.fields()) {
      if (!availableOnly && owner != null && Tys.getInitializerField(construction, initializer) == null) {
        throw new IllegalArgumentException("Unknown struct field: %s".formatted(initializer.lhs()));
      }
    }
  }
}
