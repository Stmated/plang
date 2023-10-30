package com.github.stmated.plang.ty.util;

import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyDiffKind;
import com.github.stmated.plang.ty.TyResult;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TyUtil {

  public static <T extends Ty> TyResult<T> getCommonDenominator(T a, T b) {

    if (a == b) {
      return new TyResult<>(a, null);
    }

    return new TyResult<>(null, new TyDiffKind[]{TyDiffKind.INCOMPATIBLE});
  }
}
