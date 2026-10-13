package org.inf.hir.typing;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;

/// Resolves the preferred constraint [org.inf.ty.Ty] from a [Hir.Expression].
/// The constraint type is the type that the developer selected for the expression.
@UtilityClass
public class HirStructuralConstraintTyResolver {

  public static Ty resolve(Hir.Expression e) {

    return switch (e) {
      case Hir.Union union -> Tys.structuralTy(union.unionTy());
      case Hir.Identifier id -> {
        if (id.target() != null) {
          yield resolve(id.target());
        } else {
          // TODO: Check why this can happen, if it is allowed/expected or not.
          yield Tys.structuralTy(id.ty());
        }
      }
      case Hir.Dec dec -> {
        final var constraint = Tys.structuralTy(dec.typeAnnotation().ty());
        if (Tys.isInferred(constraint)) {
          if (dec.initialization() != null) {
            // TODO: This should be removed/changed, since we are in a recursive mindset, Dec and Assignment should be completely separate.
            final var init = dec.initialization();
            final var rhst = resolve(init.rhs());
            yield Tys.structuralTy(rhst);
          }
        }

        yield constraint;
      }
      case Hir.Assignment a -> {
        // TODO: This can likely be something else... there's complexity here that likely seems more difficult than it actually is.
        final var lhst = resolve(a.lhs());
        //final var rhst = resolve(a.rhs());
        yield Tys.structuralTy(lhst);
      }
      default -> Tys.structuralTy(e.ty());
    };
  }
}
