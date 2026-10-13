package org.inf.ty;

import jakarta.annotation.Nullable;
import org.inf.ty.util.Tys;

/// Expression completion, retained structure, and source requirements; construct through `Tys.typeInfo`.
public record TyInfo(@Nullable Ty completion, @Nullable Ty structural, @Nullable Ty declaredConstraint) implements Ty {

  public TyInfo {
    completion = Tys.completionTy(completion);
    structural = Tys.structuralTy(structural);
    declaredConstraint = Tys.completionTy(declaredConstraint);
  }

  @Override
  @Nullable
  public Ty completionTy() {
    return completion;
  }

  @Override
  @Nullable
  public Ty structuralTy() {
    return structural;
  }

  @Override
  @Nullable
  public Ty declaredConstraintTy() {
    return declaredConstraint;
  }

  @Override
  public String toString() {
    if (declaredConstraint == null) {
      return "(completion=%s, structural=%s)".formatted(completion, structural);
    }
    return "(completion=%s, structural=%s, declaredConstraint=%s)".formatted(completion, structural, declaredConstraint);
  }
}
