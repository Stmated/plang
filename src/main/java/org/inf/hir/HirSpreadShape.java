package org.inf.hir;

import lombok.experimental.UtilityClass;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;

/// Static aggregate slots shared by spread consumers; does not evaluate the operand.
@UtilityClass
public class HirSpreadShape {

  public static TyField[] fields(final Hir.Spread spread) {
    if (spread.valueTy() instanceof TyStruct struct) {
      return struct.fields();
    }
    if (spread.ty() == Ty.DEADEND) {
      return new TyField[0];
    }
    throw new IllegalArgumentException("Spreading requires a statically known tuple or struct, not %s".formatted(spread.valueTy()));
  }
}
