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

/// Resolves source type syntax without treating arbitrary value expressions as types.
final class HirTypeAnnotationResolver {

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

  private Ty resolvedType(final Hir.Expression expr) {
    final var cached = resolved.get(expr);
    if (cached != null) {
      return cached;
    }
    final var type = switch (expr) {
      case Hir.Identifier identifier -> resolvedType(definitions.definition(identifier.target()));
      case Hir.Array array -> array.arrayTy();
      case Hir.Union union -> {
        final var types = new Ty[union.elements().length];
        for (var i = 0; i < types.length; i++) {
          types[i] = resolvedType(union.elements()[i]);
        }
        final var unionType = ArrayUtils.any(types, Tys::containsInferred) ? Ty.INFER : Tys.union(types);
        union.unionTy(unionType);
        yield unionType;
      }
      case Hir.Tuple tuple -> {
        final var entries = tuple.children();
        final var fields = new TyField[entries.length];
        for (var i = 0; i < fields.length; i++) {
          final var entry = entries[i];
          fields[i] = new TyField(entry.label() == null ? null : entry.label().name(), resolvedType(entry.value()));
        }
        final var tupleType = new TyStruct(fields, true).intern();
        tuple.ty(tupleType);
        yield tupleType;
      }
      default -> expr.ty();
    };
    if (!Tys.containsInferred(type)) {
      resolved.put(expr, type);
    }
    return type;
  }
}
