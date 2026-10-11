package org.inf.hir;

import jakarta.annotation.Nonnull;
import org.inf.ty.Ty;

import java.util.Objects;

/// A source type constraint, not a runtime expression or an inferred binding type.
///
/// @param ty         By developer given
/// @param expression
public record DynamicTy(@Nonnull Ty ty, Hir.Expression expression) {

  public DynamicTy {
    Objects.requireNonNull(ty);
  }

  public DynamicTy() {
    this(Ty.INFER, null);
  }

  public DynamicTy(final Ty ty) {
    this(ty, null);
  }

  public DynamicTy(final Hir.Expression expression) {
    this(Ty.INFER, Objects.requireNonNull(expression));
  }

  public DynamicTy computeIfAbsent(final java.util.function.Supplier<Ty> supplier) {
    if (expression == null) {
      return this;
    }
    final var resolved = Objects.requireNonNullElse(supplier.get(), Ty.INFER);
    return resolved == ty ? this : new DynamicTy(resolved, expression);
  }

  /// Refresh expression-backed constraints; static constraints need no resolution.
  public DynamicTy resolve(final java.util.function.Function<Hir.Expression, Ty> resolver) {
    if (expression == null) {
      return this;
    }
    final var resolved = Objects.requireNonNullElse(resolver.apply(expression), Ty.INFER);
    return resolved == ty ? this : new DynamicTy(resolved, expression);
  }

  public void visit(final HirVisitor visitor) {
    if (expression != null) {
      visitor.visitChild(expression);
    }
  }

  public DynamicTy transform(final HirTransformer transformer) {
    if (expression == null) {
      return this;
    }
    final var transformed = Objects.requireNonNull(expression.transform(transformer));
    return transformed == expression ? this : new DynamicTy(ty, transformed);
  }

  @Override
  public String toString() {
    return expression == null ? ty.toShortString() : expression.toString();
  }
}
