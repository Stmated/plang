package org.inf.hir.typing;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTupleMatching;
import org.inf.hir.passes.HirTypeAnnotationResolver;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.util.Tys;

import java.util.HashSet;

@UtilityClass
public final class HirTupleTyping {

  public static void resolve(
    final Hir.Tuple tuple,
    final boolean availableOnly,
    final HirTypeAnnotationResolver annotationResolver
  ) {

    final var entries = tuple.children();
    if (entries.length == 0) {
      throw new IllegalArgumentException("Empty tuple types and values are not supported");
    }

    final var fields = new TyField[entries.length];

    // TODO: This part is weird. Why do we need to match against our own type?
    final var contextual = tuple.contextualType();
    final var slots = (contextual == null) ? null : HirTupleMatching.match(tuple, contextual);

    final var labels = new HashSet<String>();
    var diverges = false;
    for (var i = 0; i < entries.length; i++) {
      final var entry = entries[i];
      final var name = entry.label() == null ? null : entry.label().name();
      if (!availableOnly && name != null && !labels.add(name)) {
        throw new IllegalArgumentException("Duplicate tuple label: " + name);
      }
      final var value = entry.value();
      final var completion = Tys.completionTy(value.ty());
      final var type = annotationResolver.resolveSilently(value);
      if (Tys.isDeadend(completion)) {
        diverges = true;
      }

      final var index = (slots == null) ? i : slots[i];
      if (slots == null) {
        fields[index] = new TyField(name, type);
      } else {
        // Preparation exposes the linked layout; final resolution uses converted entry types.
        final var field = contextual.fields()[index];
        final var fieldType = availableOnly ? HirDeclarationTyping.resolve(field.ty(), type) : type;
        fields[index] = new TyField(field.name(), fieldType);
      }
    }

    final var resolvedTy = Tys.intern(new TyStruct(fields, true));
    tuple.ty(Tys.withCompletion(diverges ? Ty.DEADEND : resolvedTy, resolvedTy));
  }
}
