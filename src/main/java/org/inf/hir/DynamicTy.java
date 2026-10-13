package org.inf.hir;

import org.inf.ty.Ty;

public interface DynamicTy<T extends DynamicTy<?>> {
  DynamicTy<? extends DynamicTy<?>> resolve(java.util.function.Function<Hir.Expression, Ty> resolver);

  void visit(HirVisitor visitor);

  DynamicTy<T> transform(HirTransformer transformer);

  @Override
  String toString();

  Ty ty();

  /// TODO: Should eventually be removed -- there shouldn't be a need to check for this
  default boolean hasDirectTy() {
    return this.ty() != null;
  }

  Hir.Expression expression();
}
