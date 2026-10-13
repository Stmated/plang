package org.inf.hir;

import lombok.extern.slf4j.Slf4j;
import org.inf.hir.typing.HirStructuralConstraintTyResolver;
import org.inf.ty.Ty;
import org.inf.ty.util.TypeComparison;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/// Same functionality as [DynamicTyDefault] but redirects all source-of-truth to the target expression
@Slf4j
public record DynamicTyRedirector(Hir.Expression expression) implements DynamicTy<DynamicTyRedirector> {

  /// Refresh expression-backed constraints; static constraints need no resolution.
  @Override
  public DynamicTy<? extends DynamicTy<?>> resolve(final java.util.function.Function<Hir.Expression, Ty> resolver) {

    final var newExpr = new AtomicReference<Hir.Expression>();
    final var newTy = new AtomicReference<Ty>();
    final Function<Hir.Expression, Ty> inner = e -> {

      final var resolved = HirStructuralConstraintTyResolver.resolve(e); // resolver.apply(e);

      newExpr.set(e);
      newTy.set(resolved);

      return resolved;
    };

    // We do not care about the result, since we never store the ty, we want to base it on the child expression only.
    inner.apply(this.expression);

    if (newExpr.get() == null) {
      log.warn("Received a new null expression, not sure what to do here...");
    }

    final var currentTy = this.ty();

    final var sameExpr = newExpr.get() == this.expression;
    final var similarExpr = Objects.equals(newExpr.get(), this.expression);

    final var sameTy = newTy.get() == currentTy;
    final var similarTy = Objects.equals(newTy.get(), currentTy);

    final var eqStrExpr = sameExpr ? "same" : (similarExpr ? "similar" : "new");
    final var eqStrTy = sameTy ? "same" : (similarTy ? "similar" : "new");

    if (currentTy == newTy.get()) {
      return this;
    } else if (Objects.equals(currentTy, newTy.get())) {
      log.info("{}, {} expression {} & {} ty, returning ourselves :)", this, eqStrExpr, eqStrTy, newExpr.get());
      return this;
    } else if (TypeComparison.same(currentTy, newTy.get())) {

      if (Objects.equals(this.expression, newExpr.get())) {
        log.info("{} {} expression {} & {} ty, returning ourslves :)", this, eqStrExpr, eqStrTy, newExpr.get());
        return this;
      } else {
        log.warn("{}, {} expression '{}' & {} ty '{}', creating new DynamicTyRedirector :(", this, eqStrExpr, newExpr.get(), eqStrTy, newTy.get());
        return new DynamicTyRedirector(newExpr.get());
      }
    } else {
      if (similarExpr) {

        log.warn("{}, {} expression '{}' & {} ty '{}' -> '{}', creating new DynamicTyDefault :(",
          this, eqStrExpr, newExpr.get(), eqStrTy, currentTy, newTy.get()
        );
        return new DynamicTyDefault(newTy.get(), newExpr.get());
      } {

        log.warn("{}, {} expression '{}' -> '{}' & {} ty '{}' -> '{}', creating new DynamicTyDefault(Ty, Expr) :(",
          this, eqStrExpr, this.expression, newExpr.get(), eqStrTy, currentTy, newTy.get()
        );
        return new DynamicTyDefault(newTy.get(), newExpr.get());
      }
    }
  }

  @Override
  public void visit(final HirVisitor visitor) {
    // TODO: This might make it so we visit the same expression twice!
    //        This should likely "solve itself" once we remove the DynamicTy and instead rely on `TyInfo` and proper annotation constraint resolving
    // TODO: We really should not be visiting here... if things break because we do not visit, then something else is off.
    visitor.visitChild(expression);
  }

  @Override
  public DynamicTyRedirector transform(final HirTransformer transformer) {

    // TODO: This should also not transform anything, I think. Probably a problem we get rid of as well when we get rid of the DynamicTy.
    final var transformed = Objects.requireNonNull(expression.transform(transformer));
    if (transformed == expression) {
      return this;
    }
    return new DynamicTyRedirector(transformed);
  }

  @Override
  public String toString() {
    return "%s: %s".formatted(expression.getClass().getSimpleName(), expression);
  }

  @Override
  public Ty ty() {
    if (this.expression == null) {
      return Ty.INFER;
    }

    final var structuralTy = HirStructuralConstraintTyResolver.resolve(this.expression);

    return Objects.requireNonNullElse(
      structuralTy,
      Ty.INFER
    );
  }

  @Override
  public boolean hasDirectTy() {
    return this.expression != null;
  }
}
