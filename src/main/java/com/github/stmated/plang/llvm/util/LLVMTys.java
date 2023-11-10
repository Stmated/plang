package com.github.stmated.plang.llvm.util;

import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.ty.TyValueString;
import com.github.stmated.plang.ty.util.Tys;
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
   * TODO: Create a new type object called LowTy that has things like: "isGlobal" "original" and "low"
   *        Then use it everywhere in this lowering -- so we can be sure we're working with a lowered type (but access the original)
   */
  public static Ty getLowTy(Ty ty) {

    if (ty instanceof TyValueString) {
      return STR_CHAR_POINTER;
    }

    return ty;
  }

  /**
   * NOTE: This might require a {@link com.github.stmated.plang.mir.model.MirInstr} to be given. To know context of usage.
   */
  public static Ty normalize(Ty ty) {

    if (ty instanceof TyPointer<?> tp) {
      return tp.inner();
    }

    return ty;
  }
}
