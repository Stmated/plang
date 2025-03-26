package org.inf.llvm.util;

import org.inf.mir.Mir.Instr;
import org.inf.ty.Ty;
import org.inf.ty.TyPointer;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueArray;
import org.inf.ty.TyValueNumberInteger;
import org.inf.ty.TyValueString;
import org.inf.ty.util.Tys;
import lombok.experimental.UtilityClass;

/**
 * Utility class that will handle the centralized logic of types between our managed code and LLVM.
 *
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

  /**
   * NOTE: This might require a {@link Instr} to be given. To know context of usage.
   */
  public static Ty normalize(Ty ty) {

    if (ty instanceof TyPointer<?> tp) {
      return tp.inner();
    }

    return ty;
  }

  public static boolean isPointer(Ty ty) {

    if (ty instanceof TyPointer<?> || ty instanceof TyStruct || ty instanceof TyValueArray) {
      return true;
    }

    return false;
  }
}
