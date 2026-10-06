package org.inf.hir;

import lombok.experimental.UtilityClass;
import org.inf.exceptions.UnexpectedExpressionException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/// Call-list boundaries are distinct from tuple values inside individual entries.
@UtilityClass
public class HirCallArguments {

  public static Hir.Expression of(final Hir.TupleEntry[] entries) {
    return switch (entries.length) {
      case 0 -> null;
      case 1 -> entries[0];
      default -> new Hir.Tuple(entries, null);
    };
  }

  public static List<Hir.TupleEntry> entries(final Hir.Expression arguments) {
    return switch (arguments) {
      case null -> List.of();
      case Hir.TupleEntry entry -> Collections.singletonList(entry);
      case Hir.Tuple tuple -> Collections.unmodifiableList(Arrays.asList(tuple.children()));
      default -> throw new UnexpectedExpressionException(arguments);
    };
  }
}
