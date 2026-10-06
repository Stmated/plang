package org.inf.ty.util;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.ty.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

@UtilityClass
public class Tys {

  public static final Comparator<Ty> TY_COMPARATOR = Comparator.comparing(it -> it.getClass().getSimpleName());

  private static final Map<Ty, Ty> tyInternMap = Collections.synchronizedMap(new HashMap<>());

  public static <T extends Ty> T intern(T ty) {
    return (T) tyInternMap.computeIfAbsent(ty, Function.identity());
  }

  public static TyResult<Ty> getCommonDenominator(Ty a, Ty b) {

    if (a == b) {
      return new TyResult<>(a);
    }
    if (a instanceof TyStruct && b instanceof TyStruct
      && (TupleTypes.containsTuple(a) || TupleTypes.containsTuple(b)) && TypeComparison.sameValueType(a, b)) {
      return new TyResult<>(a);
    }

    final var reordered = reorder(a, b);
    a = reordered.a();
    b = reordered.b();

    if (a == Ty.UNKNOWN || b == Ty.UNKNOWN) {
      return new TyResult<>(Ty.UNKNOWN, TyDiffKind.UNKNOWN);
    }

    if (a instanceof TyOpaque && b instanceof TyOpaque) {
      return new TyResult<>(a);
    }

    return switch (a) {
      case TyValueNumberInteger ani -> switch (b) {
        case TyValueNumberInteger bni -> {
          if (!TypeComparison.sameSignedness(ani, bni)) {
            yield new TyResult<>(Ty.INTEGER, TyDiffKind.DIFF_SIGNED);
          }
          if (!TypeComparison.sameRadix(ani, bni)) {
            yield new TyResult<>(Ty.INTEGER, TyDiffKind.DIFF_RADIX);
          }
          final var widthDiff = !TypeComparison.sameBitWidth(ani, bni);
          final var explicitDiff = !TypeComparison.sameWidthExplicitness(ani, bni);
          if (widthDiff || explicitDiff) {
            final var newWidthValue = Math.max(ani.width().value(), bni.width().value());
            final var newWidth = new BitWidth(newWidthValue, ani.width().explicit() || bni.width().explicit());
            final var newFlags = mixFlags(ani.flags(), bni.flags());
            yield new TyResult<>(
              Tys.intern(new TyValueNumberInteger((byte) 10, newWidth, ani.signed(), newFlags)),
              widthDiff ? TyDiffKind.DIFF_WIDTH_EXT : TyDiffKind.DIFF_WIDTH_EXPLICIT
            );
          }

          yield new TyResult<>(ani);
        }
        case TyValueNumberPrecisioned bnp -> new TyResult<>(bnp, TyDiffKind.DIFF_PRECISION_EXT);
        case TyValueNumberScaled bns -> new TyResult<>(bns, TyDiffKind.DIFF_PRECISION_EXT);
        default -> new TyResult<>(null, TyDiffKind.INCOMPATIBLE);
      };
      case TyValueNumberPrecisioned anp -> switch (b) {
        case TyValueNumberPrecisioned bnp -> {

          final var newWidthValue = Math.max(anp.width().value(), bnp.width().value());
          final var newPrecisionValue = Math.max(anp.precision(), bnp.precision());
          final var newSigned = anp.signed() || bnp.signed();
          final var newExplicit = anp.width().explicit() || bnp.width().explicit();
          final var newFlags = mixFlags(anp.flags(), bnp.flags());

          final var diffWidth = !TypeComparison.sameBitWidth(anp, bnp);
          final var diffExpl = !TypeComparison.sameWidthExplicitness(anp, bnp);
          final var diffPrecision = !TypeComparison.samePrecision(anp, bnp);

          if (diffWidth && diffPrecision) {
            final var newWidth = new BitWidth(newWidthValue, newExplicit);
            yield new TyResult<>(Tys.intern(new TyValueNumberPrecisioned(anp.kind(), newWidth, newPrecisionValue, newSigned, newFlags)), TyDiffKind.DIFF_WIDTH_EXT, TyDiffKind.DIFF_PRECISION_EXT);
          } else if (diffWidth) {
            final var newWidth = new BitWidth(newWidthValue, newExplicit);
            yield new TyResult<>(Tys.intern(new TyValueNumberPrecisioned(anp.kind(), newWidth, newPrecisionValue, newSigned, newFlags)), TyDiffKind.DIFF_WIDTH_EXT);
          } else if (diffExpl) {
            final var newWidth = new BitWidth(newWidthValue, newExplicit);
            yield new TyResult<>(Tys.intern(new TyValueNumberPrecisioned(anp.kind(), newWidth, newPrecisionValue, newSigned, newFlags)), TyDiffKind.DIFF_WIDTH_EXPLICIT);
          } else if (diffPrecision) {
            yield new TyResult<>(Tys.intern(new TyValueNumberPrecisioned(anp.kind(), anp.width(), newPrecisionValue, newSigned, newFlags)), TyDiffKind.DIFF_PRECISION_EXT);
          }

          yield new TyResult<>(anp);
        }
        case TyValueNumberInteger bni -> {
          if (!TypeComparison.sameBitWidth(anp, bni)) {
            final var newWidthValue = Math.max(anp.width().value(), bni.width().value());
            final var newWidth = new BitWidth(newWidthValue, anp.width().explicit() || bni.width().explicit());
            final var newFlags = mixFlags(anp.flags(), bni.flags());
            yield new TyResult<>(
              Tys.intern(new TyValueNumberPrecisioned(anp.kind(), newWidth, anp.precision(), anp.signed(), newFlags)),
              TyDiffKind.DIFF_WIDTH_EXT
            );
          }

          yield new TyResult<>(anp);
        }
        default -> new TyResult<>(null, TyDiffKind.INCOMPATIBLE);
      };
      default -> new TyResult<>(null, TyDiffKind.INCOMPATIBLE);
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
  public static EnumSet<TyFlags> mixFlags(final Set<TyFlags> a, final Set<TyFlags> b) {

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

    final var newUnionMembers = new ArrayList<Ty>();
    final var remaining = new ArrayList<>(Arrays.asList(types));
    while (!remaining.isEmpty()) {
      final var current = remaining.removeFirst();
      if (current == null || current == Ty.DEADEND) {
        continue;
      }

      if (current instanceof TyUnion union) {
        remaining.addAll(Arrays.asList(union.types()));
      } else {

        boolean found = false;
        Ty foundCommon = null;
        Ty toRemove = null;
        for (final var existing : newUnionMembers) {
          if (existing.equals(current)) {
            found = true;
            break;
          }

          final var common = getCommonDenominator(existing, current);
          if (isGenerallyCompatible(common.diffs())) {
            found = true;
            foundCommon = common.ty();
            toRemove = existing;
            break;
          }
        }

        if (!found) {
          newUnionMembers.add(current);
        } else if (foundCommon != null) {
          newUnionMembers.remove(toRemove);
          newUnionMembers.add(foundCommon);
        }
      }
    }

    if (newUnionMembers.size() == 1) {
      return newUnionMembers.getFirst();
    } else if (newUnionMembers.isEmpty()) {
      return Ty.DEADEND;
    }

    return new TyUnion(newUnionMembers.toArray(new Ty[0]));
  }

  public boolean isGenerallyCompatible(TyDiffKind[] diffs) {
      if (diffs == null || diffs.length == 0) {
        return true;
      } else {
        return diffs.length == 1 && diffs[0] == TyDiffKind.DIFF_WIDTH_EXPLICIT;
      }
  }

  public boolean isSizeCompatible(TyDiffKind[] diffs) {
    // TODO: Implement actual compatibility checks
    return diffs == null || diffs.length == 0;
  }

  public static boolean isUsable(Ty ty) {
    return ty != null && ty != Ty.INVALID;
  }

  public static boolean isInferred(Ty ty) {
    return ty == null || ty == Ty.INFER;
  }

  public static Ty getIfInferred(Ty original, Supplier<Ty> supplier) {

    if (original == null || original == Ty.INFER) {
      return supplier.get();
    } else {
      return original;
    }
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
  public static Ty fromString(String name, MachineTarget machineTarget) {

    var pointerDepth = 0;
    while (name.startsWith("*")) {
      pointerDepth++;
      name = name.substring(1);
    }

    var ty = fromStringInner(name, machineTarget);
    while (ty != null && pointerDepth > 0) {

      ty = new TyPointer<>(ty);
      pointerDepth--;
    }

    return ty;
  }

  @Nullable
  private static Ty fromStringInner(String name, MachineTarget machineTarget) {
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
      case "char" -> Ty.CHAR;
      case "usize" -> switch (machineTarget.pointerBitSize()) {
        case 64 -> Ty.ULONG;
        case 32 -> Ty.UINTEGER;
        default -> throw new IllegalArgumentException("Unhandled pointer bit size '" + machineTarget.pointerBitSize() + "'");
      };
      case "opaque" -> new TyOpaque();
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

  public static boolean isDeadEnd(Hir.Expression... children) {
    for (final var child : children) {
      if (child.ty() == Ty.DEADEND) {
        return true;
      }
    }
    return false;
  }
}
