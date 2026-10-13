package org.inf.hir;

import jakarta.annotation.Nonnull;
import org.inf.ty.Ty;

import java.util.Objects;

/// A source type constraint, not a runtime expression or an inferred binding type.
///
/// @param ty         Resolved source constraint, retaining omitted types as `INFER`
/// @param expression Source type syntax; null for omitted or static constraints
public record DynamicTyDefault(@Nonnull Ty ty, Hir.Expression expression) implements DynamicTy<DynamicTyDefault> {

  public DynamicTyDefault {
    Objects.requireNonNull(ty);
  }

  public DynamicTyDefault() {
    this(Ty.INFER, null);
  }

  /// TODO: REMOVE! VERY IMPORTANT TO REMOVE THIS! IT ALLOWS TYPES THAT ARE NOT ACTUALLY CONSTRUCTABLE! IT CREATES UNNECESSARY TEST COVERAGE!
  public DynamicTyDefault(final Ty ty) {
    this(Objects.requireNonNull(ty), null);
  }

  /// Refresh expression-backed constraints; static constraints need no resolution.
  @Override
  public DynamicTy<? extends DynamicTy<?>> resolve(final java.util.function.Function<Hir.Expression, Ty> resolver) {
    if (expression == null) {
      return this;
    }
    final var resolved = Objects.requireNonNullElse(resolver.apply(expression), Ty.INFER);
    return resolved == ty ? this : new DynamicTyDefault(resolved, expression);
  }

  @Override
  public void visit(final HirVisitor visitor) {
    if (expression != null) {
      visitor.visitChild(expression);
    }
  }

  @Override
  public DynamicTyDefault transform(final HirTransformer transformer) {
    if (expression == null) {
      return this;
    }
    final var transformed = Objects.requireNonNull(expression.transform(transformer));
    return transformed == expression ? this : new DynamicTyDefault(ty, transformed);
  }

  @Override
  public String toString() {
    return expression == null ? ty.toShortString() : expression.toString();
  }
}
