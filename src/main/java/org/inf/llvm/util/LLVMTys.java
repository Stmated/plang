package org.inf.llvm.util;

import lombok.experimental.UtilityClass;
import org.inf.ty.*;
import org.inf.ty.util.Tys;

/**
 * Utility class that will handle the centralized logic of types between our managed code and LLVM.
 * <p>
 * Any discrepancies will be handled here, for example that a string is a pointer to a u8.
 */
@UtilityClass
public class LLVMTys {

  private static final TyPointer<TyValueNumberInteger> STR_CHAR_POINTER = Tys.intern(new TyPointer<>(Ty.CHAR));

  /**
   * TODO: Create a new kind object called LowTy that has things like: "isGlobal" "original" and "low"
   *        Then use it everywhere in this lowering -- so we can be sure we're working with a lowered kind (but access the original)
   */
  public static Ty getLowTy(Ty ty) {

    if (ty instanceof TyValueString) {
      return STR_CHAR_POINTER;
    }

    return ty;
  }

  public static Ty normalize(Ty ty) {
    return getLowTy(ty);
  }

  public static boolean isPointer(Ty ty) {
    return ty instanceof TyPointer<?>;
  }
}
