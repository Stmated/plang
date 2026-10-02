package org.inf.hir.passes;

import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.util.TupleTypes;

final class HirTupleTyping {

  private HirTupleTyping() {
  }

  static void resolve(Hir.Tuple tuple, boolean annotation) {
    final var entries = tuple.children();
    if (entries.length == 0) {
      throw new IllegalArgumentException("Empty tuple types and values are not supported");
    }
    final var fields = new TyField[entries.length];
    var diverges = false;
    for (var i = 0; i < entries.length; i++) {
      final var entry = entries[i];
      if (entry.label() != null) {
        throw new IllegalArgumentException("Named and mixed tuples are not supported yet");
      }
      final var value = entry.value();
      if (annotation && !(value instanceof Hir.TyExpr || value instanceof Hir.Tuple
        || value instanceof Hir.Identifier && value.ty() instanceof TyStruct struct && !struct.hasUnnamedFields())) {
        throw new IllegalArgumentException("Tuple annotations require types, not value expressions");
      }
      final var type = value.ty();
      if (!annotation && type == Ty.DEADEND) {
        diverges = true;
      } else {
        TupleTypes.requireElementType(type);
        fields[i] = new TyField(null, type);
      }
    }
    // A diverging construction has no aggregate value or layout, even if some entries have values.
    final var type = diverges ? Ty.DEADEND : new TyStruct(fields).intern();
    tuple.ty(type);
    tuple.valueTy(type);
  }
}
