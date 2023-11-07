package com.github.stmated.plang.ty.util;

import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyDiffKind;
import com.github.stmated.plang.ty.TyFlags;
import com.github.stmated.plang.ty.TyIdentifier;
import com.github.stmated.plang.ty.TyResult;
import com.github.stmated.plang.ty.TyUnion;
import com.github.stmated.plang.ty.TyValueNumber;
import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.ty.TyValueNumberPrecisioned;
import com.github.stmated.plang.ty.TyValueNumberScaled;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TyUtil {

  public static TyResult<Ty> getCommonDenominator(Ty a, Ty b) {

    if (a == Ty.UNKNOWN || b == Ty.UNKNOWN) {
      return new TyResult<>(Ty.UNKNOWN, TyDiffKind.UNKNOWN);
    }

    return switch (a) {
      case TyValueNumberInteger ani -> switch (b) {
        case TyValueNumberInteger bni
          when ani.width() == bni.width() && ani.signed() == bni.signed() && ani.radix() == bni.radix() -> new TyResult<>(ani);
        case TyValueNumberInteger bni
          when ani.width() == bni.width() && ani.signed() == bni.signed() -> new TyResult<>(Ty.INTEGER, TyDiffKind.DIFF_RADIX);
        case TyValueNumberInteger bni
          when ani.signed() == bni.signed() -> new TyResult<>(
          new TyValueNumberInteger(10, Math.max(ani.width(), bni.width()), ani.signed(), mixFlags(ani.flags(), bni.flags())),
          TyDiffKind.DIFF_WIDTH
        );
        case TyValueNumberPrecisioned bnp -> new TyResult<>(bnp, TyDiffKind.DIFF_PRECISION_EXT);
        case TyValueNumberScaled bns -> new TyResult<>(bns, TyDiffKind.DIFF_PRECISION_EXT);
        default -> new TyResult<>(null, TyDiffKind.INCOMPATIBLE);
      };
      case TyValueNumber an -> switch (b) {
//        case TyValueNumber bn -> {};
        default -> new TyResult<>(null, TyDiffKind.INCOMPATIBLE);
      };
      default -> new TyResult<>(null, TyDiffKind.INCOMPATIBLE);
    };
  }

  /**
   * TODO: This is all bad and wrong and needs a way better system
   */
  public EnumSet<TyFlags> mixFlags(EnumSet<TyFlags> a, EnumSet<TyFlags> b) {

    final var mix = EnumSet.noneOf(TyFlags.class);
    mix.addAll(a);
    mix.addAll(b);

    if (a.contains(TyFlags.MUTABLE) || b.contains(TyFlags.MUTABLE)) {
      mix.remove(TyFlags.CONSTANT);
      mix.remove(TyFlags.IMMUTABLE);
    }

    return mix;
  }

  /**
   * TODO: Should not only be union. If they are representable as common type, then we give the common denominator
   */
  public static Ty merge(Ty... types) {
    return union(types);
  }

  public static Ty union(Ty... types) {

    final var uniqueSet = new LinkedHashSet<>(Arrays.asList(types));
    final var uniqueArray = uniqueSet.toArray(new Ty[0]);
    if (uniqueArray.length == 1) {
      return uniqueArray[0];
    } else if (uniqueArray.length == 0) {
      return Ty.INVALID;
    }

    return new TyUnion(uniqueArray);
  }

  public boolean isSpecific(Ty t) {

    if (t instanceof TyIdentifier || t == Ty.INFER) {
      return false;
    }

    return true;
  }

  public boolean isGenerallyCompatible(TyDiffKind[] diffs) {
    return diffs == null || diffs.length == 0;
  }

  public static boolean isUsable(Ty ty) {
    return ty != null && ty != Ty.INVALID;
  }
}
