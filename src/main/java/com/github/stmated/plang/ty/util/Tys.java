package com.github.stmated.plang.ty.util;

import com.github.stmated.plang.hir.Hir.MutabilityKind;
import com.github.stmated.plang.ty.BitWidth;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyDiffKind;
import com.github.stmated.plang.ty.TyFlags;
import com.github.stmated.plang.ty.TyIdentifier;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.TyResult;
import com.github.stmated.plang.ty.TyUnion;
import com.github.stmated.plang.ty.TyValue;
import com.github.stmated.plang.ty.TyValueKind;
import com.github.stmated.plang.ty.TyValueNumber;
import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.ty.TyValueNumberPrecisioned;
import com.github.stmated.plang.ty.TyValueNumberScaled;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Tys {

  public static final Comparator<Ty> TY_COMPARATOR = Comparator.comparing(it -> it.getClass().getSimpleName());

  private static final Map<Ty, Ty> tyInternMap = Collections.synchronizedMap(new HashMap<>());

  public static <T extends Ty> T intern(T ty) {
    return (T) tyInternMap.computeIfAbsent(ty, it -> it);
  }

  public static TyResult<Ty> getCommonDenominator(Ty a, Ty b) {

    if (a == b) {
      return new TyResult<>(a);
    }

    final var reordered = reorder(a, b);
    a = reordered.a();
    b = reordered.b();

    if (a == Ty.UNKNOWN || b == Ty.UNKNOWN) {
      return new TyResult<>(Ty.UNKNOWN, TyDiffKind.UNKNOWN);
    }

    return switch (a) {
      case TyValueNumberInteger ani -> switch (b) {
        case TyValueNumberInteger bni -> {
          if (ani.signed() != bni.signed()) {
            yield new TyResult<>(Ty.INTEGER, TyDiffKind.DIFF_SIGNED);
          }
          if (ani.radix() != bni.radix()) {
            yield new TyResult<>(Ty.INTEGER, TyDiffKind.DIFF_RADIX);
          }
          if (ani.width().value() != bni.width().value()) {
            final var newWidthValue = Math.max(ani.width().value(), bni.width().value());
            final var newWidth = new BitWidth(newWidthValue, ani.width().explicit() || bni.width().explicit());
            final var newFlags = mixFlags(ani.flags(), bni.flags());
            yield new TyResult<>(
              new TyValueNumberInteger((byte) 10, newWidth, ani.signed(), newFlags),
              TyDiffKind.DIFF_WIDTH_EXT
            );
          }

          yield new TyResult<>(ani);
        }
//          when ani.width() == bni.width() && ani.signed() == bni.signed() && ani.radix() == bni.radix() -> new TyResult<>(ani);
//        case TyValueNumberInteger bni
//          when ani.width() == bni.width() && ani.signed() == bni.signed() ->
//        case TyValueNumberInteger bni
//          when ani.signed() == bni.signed() -> new TyResult<>(
//          new TyValueNumberInteger((byte) 10, Math.max(ani.width(), bni.width()), ani.signed(), mixFlags(ani.flags(), bni.flags())),
//          TyDiffKind.DIFF_WIDTH
//        );
        case TyValueNumberPrecisioned bnp -> new TyResult<>(bnp, TyDiffKind.DIFF_PRECISION_EXT);
        case TyValueNumberScaled bns -> new TyResult<>(bns, TyDiffKind.DIFF_PRECISION_EXT);
        default -> new TyResult<>(null, TyDiffKind.INCOMPATIBLE);
      };
      case TyValueNumber an -> switch (b) {
        default -> new TyResult<>(null, TyDiffKind.INCOMPATIBLE);
      };
      default -> new TyResult<>(null, TyDiffKind.INCOMPATIBLE);
    };
  }

  public static TyDiffKind[] getDifferences(Ty a, Ty b) {

    final var common = getCommonDenominator(a, b);
    return common.diffs();
  }

  public static Ty toNonConstIfRequired(Ty ty, MutabilityKind mutabilityKind) {

    return switch (ty) {
      case TyValueNumberInteger ni -> {
        yield ni;
      }

      // TODO: This needs to be extensively expanded upon.
      default -> ty;
    };
  }

  public static TyValueKind getValueKind(Ty ty) {

    return switch (ty) {
      case TyValue v -> v.getValueKind();
      default -> throw new IllegalArgumentException(STR."Unknown value kind '\{ty}'");
    };
  }

  private Pair<Ty, Ty> reorder(Ty a, Ty b) {
    return reorderBasedOnType(a, b, Function.identity());
  }

  public static <T> Pair<T, T> reorderBasedOnType(T a, T b, Function<T, Ty> mapper) {

    if (TY_COMPARATOR.compare(mapper.apply(a), mapper.apply(b)) <= 0) {
      return new Pair<>(a, b);
    } else {
      return new Pair<>(b, a);
    }
  }

  /**
   * TODO: This is all bad and wrong and needs a way better system
   */
  public static EnumSet<TyFlags> mixFlags(EnumSet<TyFlags> a, EnumSet<TyFlags> b) {

    final var mix = EnumSet.noneOf(TyFlags.class);
    mix.addAll(a);
    mix.addAll(b);

    if (a.contains(TyFlags.MUTABLE) || b.contains(TyFlags.MUTABLE)) {
      mix.remove(TyFlags.CONSTANT);
      mix.remove(TyFlags.IMMUTABLE);
    }

    if (a.contains(TyFlags.CONSTANT) != b.contains(TyFlags.CONSTANT)) {

      // One of them is not constant, so it will stop being one.
      mix.remove(TyFlags.CONSTANT);
    }

    return mix;
  }

  /**
   * TODO: Should not only be union. If they are representable as common kind, then we give the common denominator
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

  public boolean isSizeCompatible(TyDiffKind[] diffs) {
    // TODO: Implement actual compatibility checks
    return diffs == null || diffs.length == 0;
  }

  public static boolean isUsable(Ty ty) {
    return ty != null && ty != Ty.INVALID;
  }

  public static Ty simplify(Ty ty) {

    // TODO: Simplify the kind as much as possible
    //        Used to be able to find a single kind that LLVM can use

    return ty;
  }

  public static Ty dereference(Ty ty) {
    if (ty instanceof TyPointer<?> tp) {
      return tp.inner();
    } else {
      return ty;
    }
  }

  public static int getReferenceDepth(Ty ty) {
    if (ty instanceof TyPointer<?> tp) {
      return getReferenceDepth(tp.inner()) + 1;
    } else {
      return 0;
    }
  }

  public static Ty dereferenceRecursively(Ty ty) {
    if (ty instanceof TyPointer<?> tp) {
      return dereferenceRecursively(tp.inner());
    } else {
      return ty;
    }
  }


  /**
   * TODO: Likely very slow, will need to be faster and smarter :)
   */
  public static Ty fromString(String name) {

    if (name.startsWith("int") || name.startsWith("sint")) {

      final var width = getWidthFromName(name, Ty.INTEGER.width().value());
      return Ty.INTEGER.toBuilder().width(new BitWidth(width, true)).signed(true).build().intern();

    } else if (name.startsWith("uint")) {

      final var width = getWidthFromName(name, Ty.INTEGER.width().value());
      return Ty.INTEGER.toBuilder().width(new BitWidth(width, false)).signed(false).build().intern();

    } else if (name.startsWith("float")) {

      final var width = getWidthFromName(name, Ty.FLOAT.width().value());
      return Ty.FLOAT.toBuilder().width(new BitWidth(width, true)).build().intern();
    }

    return switch (name) {
      case "string" -> Ty.STRING;
      case "long" -> Ty.LONG;
      case "short" -> Ty.SHORT;
      case "ushort" -> Ty.USHORT;
      case "double" -> Ty.DOUBLE;
      case "decimal" -> Ty.DECIMAL;
      case "bool" -> Ty.BOOLEAN;
      default -> null;
    };
  }

  private int getWidthFromName(String name, int defaultWidth) {

    final var lastIndex = (name.length() - 1);
    var numberIndex = lastIndex;
    while (Character.isDigit(name.charAt(numberIndex))) {
      numberIndex--;
    }

    return (numberIndex != lastIndex) ? Integer.parseInt(name.substring(numberIndex + 1, lastIndex + 1)) : defaultWidth;
  }
}
