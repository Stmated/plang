package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.util.TupleTypes;

import java.util.HashSet;

@UtilityClass
final class HirTupleTyping {

  static void resolve(Hir.Tuple tuple) {
    final var entries = tuple.children();
    if (entries.length == 0) {
      throw new IllegalArgumentException("Empty tuple types and values are not supported");
    }
    final var fields = new TyField[entries.length];
    final var labels = new HashSet<String>();
    var diverges = false;
    for (var i = 0; i < entries.length; i++) {
      final var entry = entries[i];
      final var name = entry.label() == null ? null : entry.label().name();
      if (name != null && !labels.add(name)) {
        throw new IllegalArgumentException("Duplicate tuple label: " + name);
      }
      final var value = entry.value();
      final var type = value.ty();
      if (type == Ty.DEADEND) {
        diverges = true;
      } else {
        TupleTypes.requireElementType(type);
        fields[i] = new TyField(name, type);
      }
    }
    // A diverging construction has no aggregate value or layout, even if some entries have values.
    final var type = diverges ? Ty.DEADEND : new TyStruct(fields, true).intern();
    tuple.ty(type);
    tuple.valueTy(type);
  }
}
