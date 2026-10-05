package org.inf.hir;

import lombok.experimental.UtilityClass;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.ty.TyStruct;

import java.util.HashSet;

/// Matches labels before positional entries without changing source evaluation order.
@UtilityClass
public class HirTupleMatching {

  /// Returns source-to-destination indices, or null for an incompatible arity.
  /// Arity mismatches remain subject to flow-aware tuple validation.
  public static int[] match(final Hir.Tuple tuple, final TyStruct destination) {
    final var entries = tuple.children();
    final var fields = destination.fields();
    final var indices = new int[entries.length];
    final var bound = new boolean[fields.length];
    final var labels = new HashSet<String>();
    for (var i = 0; i < entries.length; i++) {
      final var label = entries[i].label();
      if (label == null) {
        continue;
      }
      if (!labels.add(label.name())) {
        throw new IllegalArgumentException("Duplicate tuple label: " + label.name());
      }
      var index = -1;
      for (var j = 0; j < fields.length; j++) {
        if (label.name().equals(fields[j].name())) {
          index = j;
          break;
        }
      }
      if (index < 0) {
        throw new InvalidTypeConversionException("Unknown tuple label: " + label.name(), tuple.ty(), destination);
      }
      indices[i] = index;
      bound[index] = true;
    }
    if (entries.length != fields.length) {
      return null;
    }
    var positional = 0;
    for (var i = 0; i < entries.length; i++) {
      if (entries[i].label() != null) {
        continue;
      }
      while (bound[positional]) {
        positional++;
      }
      indices[i] = positional;
      bound[positional++] = true;
    }
    return indices;
  }
}
