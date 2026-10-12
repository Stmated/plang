package org.inf.ty;

import jakarta.annotation.Nullable;
import org.inf.ty.util.Tys;

/// Expression completion and resolved structure; construct through `Tys.withCompletion`.
public record TyCompletion(@Nullable Ty completion, @Nullable Ty structural) implements Ty {

  public TyCompletion {
    completion = Tys.completionTy(completion);
    structural = Tys.structuralTy(structural);
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
  public String toString() {
    return "(completion=%s, structural=%s)".formatted(completion, structural);
  }
}
