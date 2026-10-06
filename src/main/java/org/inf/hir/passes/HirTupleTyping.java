package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTupleMatching;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.util.TupleTypes;
import org.inf.ty.util.Tys;

import java.util.HashSet;

@UtilityClass
final class HirTupleTyping {

  static void resolve(Hir.Tuple tuple) {
    resolve(tuple, false);
  }

  static void resolve(final Hir.Tuple tuple, final boolean availableOnly) {
    final var entries = tuple.children();
    if (entries.length == 0) {
      throw new IllegalArgumentException("Empty tuple types and values are not supported");
    }
    final var fields = new TyField[entries.length];
    final var contextual = tuple.contextualType();
    final var slots = contextual == null ? null : HirTupleMatching.match(tuple, contextual);
    final var labels = new HashSet<String>();
    var diverges = false;
    for (var i = 0; i < entries.length; i++) {
      final var entry = entries[i];
      final var name = entry.label() == null ? null : entry.label().name();
      if (!availableOnly && name != null && !labels.add(name)) {
        throw new IllegalArgumentException("Duplicate tuple label: " + name);
      }
      final var value = entry.value();
      final var type = value.ty();
      if (type == Ty.DEADEND) {
        diverges = true;
      } else {
        if (!availableOnly || !Tys.containsInferred(type)) {
          TupleTypes.requireElementType(type);
        }
        final var index = slots == null ? i : slots[i];
        if (slots == null) {
          fields[index] = new TyField(name, type);
        } else {
          // Preparation exposes the linked layout; final resolution uses converted entry types.
          final var field = contextual.fields()[index];
          fields[index] = new TyField(field.name(), availableOnly ? field.ty() : type);
        }
      }
    }
    // A diverging construction has no aggregate value or layout, even if some entries have values.
    final var type = diverges ? Ty.DEADEND : new TyStruct(fields, true).intern();
    tuple.ty(type);
    tuple.valueTy(type);
  }
}
