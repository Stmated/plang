package org.inf.ty;

import jakarta.annotation.Nullable;

/// Expression completion, retained structure, and source requirements; construct through `Tys.typeInfo`.
public record TyInfo(@Nullable Ty completionTy, @Nullable Ty structuralTy, @Nullable Ty declaredConstraintTy) implements Ty {

  @Override
  public String toString() {
    if (declaredConstraintTy == null) {
      return "(completion=%s, structural=%s)".formatted(completionTy, structuralTy);
    }
    return "(completion=%s, structural=%s, declaredConstraint=%s)".formatted(completionTy, structuralTy, declaredConstraintTy);
  }
}
