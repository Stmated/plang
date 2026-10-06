package org.inf.hir;

import lombok.experimental.UtilityClass;

@UtilityClass
public class HirCallArguments {

  public static int count(final Hir.Argument[] arguments) {
    var count = 0;
    for (final var argument : arguments) {
      count += argument.value() instanceof Hir.Spread spread ? HirSpreadShape.fields(spread).length : 1;
    }
    return count;
  }

  public static int[] bind(final Hir.Argument argument, final HirArgumentBinding binding) {
    if (argument.value() instanceof Hir.Spread spread) {
      if (argument.label() != null) {
        throw new IllegalArgumentException("A spread call argument cannot have a label");
      }
      final var fields = HirSpreadShape.fields(spread);
      final var indices = new int[fields.length];
      for (var i = 0; i < fields.length; i++) {
        indices[i] = binding.bind(fields[i].name(), true);
      }
      return indices;
    }
    return new int[]{binding.bind(argument.label() == null ? null : argument.label().name())};
  }
}
