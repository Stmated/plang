package org.inf.hir;

import lombok.experimental.UtilityClass;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.util.ArrayUtils;

import java.util.HashSet;

/// Matches labels before positional entries without changing source evaluation order.
@UtilityClass
public class HirTupleMatching {

  /// Returns source-to-destination indices, or null for an incompatible arity.
  /// Arity mismatches remain subject to flow-aware tuple validation.
  public static int[] match(final Hir.Tuple tuple, final TyStruct destination) {
    return match(
      ArrayUtils.mapToStrings(tuple.children(), entry -> entry.label() == null ? null : entry.label().name()),
      tuple.ty(), destination
    );
  }

  public static int[] match(final TyStruct source, final TyStruct destination) {
    return match(ArrayUtils.mapToStrings(source.fields(), TyField::name), source, destination);
  }

  private static int[] match(final String[] names, final Ty source, final TyStruct destination) {
    final var fields = destination.fields();
    final var indices = new int[names.length];
    final var bound = new boolean[fields.length];
    final var labels = new HashSet<String>();
    for (var i = 0; i < names.length; i++) {
      final var name = names[i];
      if (name == null) {
        continue;
      }
      if (!labels.add(name)) {
        throw new IllegalArgumentException("Duplicate tuple label: " + name);
      }
      var index = -1;
      for (var j = 0; j < fields.length; j++) {
        if (name.equals(fields[j].name())) {
          index = j;
          break;
        }
      }
      if (index < 0) {
        throw new InvalidTypeConversionException("Unknown tuple label: " + name, source, destination);
      }
      indices[i] = index;
      bound[index] = true;
    }
    if (names.length != fields.length) {
      return null;
    }
    var positional = 0;
    for (var i = 0; i < names.length; i++) {
      if (names[i] != null) {
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
