package org.inf.ty.util;

import org.inf.ty.Ty;
import org.inf.ty.TyValueNumber;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class TyNamedConstants {

  private static final List<NamedConstant> NAMED_CONSTANTS = namedConstants();

  private static List<NamedConstant> namedConstants() {
    final var constants = new ArrayList<NamedConstant>();
    final var fields = Ty.class.getFields();
    Arrays.sort(fields, Comparator.comparing(Field::getName));
    for (final var field : fields) {
      final var name = field.getName();
      final var underscoreIndex = name.indexOf('_');
      final var lastIndex = name.lastIndexOf('_');

      // Remove any short prefixes for the display name, such as `BW_`
      // `BW_EXPLICIT_16` becomes `EXPLICIT_16`.
      final String displayName;
      if (underscoreIndex != -1) {

        if (underscoreIndex != lastIndex) {
          displayName = name.substring(underscoreIndex + 1);
        } else {
          if (underscoreIndex < 4) {
            displayName = name.substring(underscoreIndex + 1);
          } else {
            displayName = name;
          }
        }
      } else {
        displayName = name;
      }

      try {
        constants.add(new NamedConstant(field.get(null), displayName));
      } catch (final IllegalAccessException exception) {
        throw new IllegalStateException("Cannot read Ty constant: " + name, exception);
      }
    }
    return Collections.unmodifiableList(constants);
  }

  public static String constantName(final Object value) {
    for (final var constant : NAMED_CONSTANTS) {
      if (constant.matches(value)) {
        return constant.name();
      }
    }
    return null;
  }

  private record NamedConstant(Object value, String name) {

    private boolean matches(final Object candidate) {
      if (value instanceof final TyValueNumber expected && candidate instanceof final TyValueNumber actual) {
        // Some numeric equals implementations omit flags; snapshots must not hide those differences.
        return expected.equals(actual) && Objects.equals(expected.flags(), actual.flags());
      }
      return value.equals(candidate);
    }
  }
}
