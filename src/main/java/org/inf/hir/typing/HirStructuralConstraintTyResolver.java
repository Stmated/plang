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

    final var ty = switch (e) {
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
        yield Tys.structuralTy(lhst);
      }
      case Hir.Parameter p -> {
        var constraint = (p.typeAnnotation() != null) ? resolve(p.typeAnnotation()) : null;
        if (Tys.isInferred(constraint)) {
          constraint = Tys.declaredConstraintTy(p.ty());
          if (Tys.isInferred(constraint)) {
            constraint = Tys.structuralTy(p.ty());
          }
        }

        // TODO: Check if this might always be the same as above, just without the field redirection.
        //        I think it should be, or we should attempt to make it so in the future.
        //        Or not, and remove the concept of "declaredConstraintTy" from TyInfo and such, since it can be fetched from the type annotation's structuralTy.
        yield constraint;
      }
      default -> Tys.structuralTy(e.ty());
    };

    if (Tys.isInferred(ty)) {

      // For constraints, `INFERRED` is the same as "none"
      return null;
    }

    return ty;
  }
}
