package org.inf.hir.passes;

import org.inf.hir.Hir;
import org.inf.hir.HirTypeDefinitions;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.util.Tys;
import org.inf.util.ArrayUtils;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/// Resolves source type syntax without treating arbitrary value expressions as types.
public final class HirTypeAnnotationResolver {

  private final HirTypeDefinitions definitions;
  private final Map<Hir.Expression, Ty> resolved = new IdentityHashMap<>();

  HirTypeAnnotationResolver(final Hir.Expression root) {
    definitions = new HirTypeDefinitions(root);
  }

  Ty resolve(final Hir.Expression expr) {
    if (!definitions.isType(expr)) {
      throw new IllegalArgumentException("Type annotations require types, not value expressions");
    }
    return resolvedType(expr);
  }

  public Ty resolveSilently(final Hir.Expression expr) {
    return resolvedType(expr);
  }

  private Ty resolvedType(final Hir.Expression expr) {
    final var cached = resolved.get(expr);
    if (cached != null) {
      return cached;
    }
    final var type = switch (expr) {
      case Hir.Identifier identifier -> resolvedType(definitions.definition(identifier.target()));
      case Hir.Array array -> Tys.structuralTy(array.ty());
      case Hir.Union union -> union.unionTy();
      case Hir.Tuple tuple -> tuple.resolvedTy();
      case Hir.Dec dec -> dec.resolvedTy();
      case Hir.Struct s -> s.ty();
      case Hir.Parameter param -> param.resolvedTy();
      case Hir.Block block -> resolvedType(block.children());
      case Hir.Return ret -> resolvedType(ret.expression());
      default -> Tys.completionTy(expr.ty());
    };

    // TODO: Replace the "containsInferred"? We should remove the whole cache anyway....
    if (!Tys.containsInferred(type)) {
      resolved.put(expr, type);
    }
    return type;
  }
}
