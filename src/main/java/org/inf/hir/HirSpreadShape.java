package org.inf.hir;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.inf.ty.TyField;
import org.inf.ty.util.Tys;

/// Static aggregate slots shared by spread consumers; does not evaluate the operand.
@UtilityClass
public class HirSpreadShape {

  public static TyField[] fields(final Hir.Spread spread) {
    final var fields = availableFields(spread);
    if (fields != null) {
      return fields;
    }
    throw new IllegalArgumentException("Spreading requires a statically known tuple or struct, not %s".formatted(spread.ty()));
  }

  /// Preparation may encounter an unresolved operand; a noncontinuing operand without a layout has no slots.
  @Nullable
  public static TyField[] availableFields(final Hir.Spread spread) {
    final var struct = HirStructLayoutVisitor.find(spread.value());
    if (struct != null) {
      return struct.fields();
    }
    if (Tys.isDeadend(spread.ty())) {
      return new TyField[0];
    }
    return null;
  }
}
