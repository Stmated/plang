package org.inf.ty.util;

import org.inf.ty.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Stream;

class TysTest {

  static Stream<Arguments> inferredMembers() {
    return Stream.of(
      Arguments.of(null, true),
      Arguments.of(Ty.INFER, true),
      Arguments.of(Ty.INTEGER, false),
      Arguments.of(tuple(Ty.INTEGER, Ty.INFER), true),
      Arguments.of(tuple(Ty.INTEGER, Ty.BOOLEAN), false),
      Arguments.of(new TyValueArray(Ty.INFER, 1), true),
      Arguments.of(new TyPointer<>(Ty.INFER), true),
      Arguments.of(new TyFn(new TyParam[]{new TyParam("v", Ty.INFER)}, false, Ty.INTEGER), true),
      Arguments.of(new TyFn(new TyParam[0], false, tuple(Ty.INFER)), true),
      Arguments.of(new TyFn(new TyParam[]{new TyParam("v", Ty.INTEGER)}, false, Ty.INTEGER), false),
      Arguments.of(new TyUnion(new Ty[]{Ty.INTEGER, Ty.INFER}), true),
      Arguments.of(new TyUnion(new Ty[]{Ty.INTEGER, Ty.BOOLEAN}), false)
    );
  }

  @ParameterizedTest
  @MethodSource("inferredMembers")
  void given__compound_type__when__checked_for_unresolved_members__then__nested_inference_is_detected(
    final Ty type, final boolean expected
  ) {
    Assertions.assertEquals(expected, Tys.containsInferred(type));
  }

  private static TyStruct tuple(Ty... types) {
    return new TyStruct(Arrays.stream(types).map(type -> new TyField(null, type)).toArray(TyField[]::new));
  }

  static Stream<Arguments> numericJoins() {
    final var explicitInt = Ty.INTEGER.toBuilder().width(new BitWidth(32, true)).build();
    final var wideFloat = Ty.FLOAT.toBuilder().width(new BitWidth(64, false)).build();
    final var preciseFloat = Ty.FLOAT.toBuilder().precision(10).build();
    final var widePreciseFloat = wideFloat.toBuilder().precision(10).build();
    final var explicitFloat = Ty.FLOAT.toBuilder().width(new BitWidth(32, true)).build();
    return Stream.of(
      Arguments.of(Ty.INTEGER, Ty.INTEGER.toBuilder().build(), Ty.INTEGER, new TyDiffKind[0]),
      Arguments.of(Ty.INTEGER, explicitInt, explicitInt, new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXPLICIT}),
      Arguments.of(Ty.SHORT, Ty.INTEGER, Ty.INTEGER, new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXT}),
      Arguments.of(Ty.SHORT, explicitInt, explicitInt, new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXT}),
      Arguments.of(Ty.INTEGER, Ty.UINTEGER, Ty.INTEGER, new TyDiffKind[]{TyDiffKind.DIFF_SIGNED}),
      Arguments.of(Ty.INTEGER, Ty.INTEGER_HEX, Ty.INTEGER, new TyDiffKind[]{TyDiffKind.DIFF_RADIX}),
      Arguments.of(Ty.INTEGER_HEX, Ty.ULONG, Ty.INTEGER, new TyDiffKind[]{TyDiffKind.DIFF_SIGNED}),
      Arguments.of(Ty.INTEGER, Ty.FLOAT, Ty.FLOAT, new TyDiffKind[]{TyDiffKind.DIFF_PRECISION_EXT}),
      Arguments.of(Ty.LONG, Ty.FLOAT, Ty.FLOAT, new TyDiffKind[]{TyDiffKind.DIFF_PRECISION_EXT}),
      Arguments.of(Ty.INTEGER, Ty.DECIMAL, Ty.DECIMAL, new TyDiffKind[]{TyDiffKind.DIFF_PRECISION_EXT}),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().build(), Ty.FLOAT, new TyDiffKind[0]),
      Arguments.of(Ty.FLOAT, wideFloat, wideFloat, new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXT}),
      Arguments.of(Ty.FLOAT, preciseFloat, preciseFloat, new TyDiffKind[]{TyDiffKind.DIFF_PRECISION_EXT}),
      Arguments.of(Ty.FLOAT, widePreciseFloat, widePreciseFloat,
        new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXT, TyDiffKind.DIFF_PRECISION_EXT}),
      Arguments.of(Ty.FLOAT, explicitFloat, explicitFloat, new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXPLICIT}),
      Arguments.of(Ty.FLOAT, explicitFloat.toBuilder().precision(10).build(),
        explicitFloat.toBuilder().precision(10).build(), new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXPLICIT}),
      Arguments.of(Ty.FLOAT, Ty.DECIMAL, null, new TyDiffKind[]{TyDiffKind.INCOMPATIBLE}),
      Arguments.of(Ty.DECIMAL, new TyValueNumberScaled(Ty.DECIMAL.width(), 10, true, EnumSet.noneOf(TyFlags.class)),
        null, new TyDiffKind[]{TyDiffKind.INCOMPATIBLE}),
      Arguments.of(Ty.INTEGER, Ty.BOOLEAN, null, new TyDiffKind[]{TyDiffKind.INCOMPATIBLE}),
      Arguments.of(Ty.INTEGER, Ty.UNKNOWN, Ty.UNKNOWN, new TyDiffKind[]{TyDiffKind.UNKNOWN})
    );
  }

  @ParameterizedTest
  @MethodSource("numericJoins")
  void given__numeric_types__when__common_type_is_selected__then__existing_result_and_diagnostics_are_preserved(
    Ty a, Ty b, Ty expected, TyDiffKind[] differences
  ) {
    final var forward = Tys.getCommonDenominator(a, b);
    final var reverse = Tys.getCommonDenominator(b, a);
    Assertions.assertAll(
      () -> Assertions.assertEquals(expected, forward.ty()),
      () -> Assertions.assertArrayEquals(differences, forward.diffs()),
      () -> Assertions.assertEquals(expected, reverse.ty()),
      () -> Assertions.assertArrayEquals(differences, reverse.diffs())
    );
  }

  static Stream<Ty> identicalTypes() {
    return Stream.of(
      Ty.INTEGER,
      Ty.DECIMAL,
      Ty.BOOLEAN,
      Ty.UNKNOWN,
      new TyOpaque()
    );
  }

  @ParameterizedTest
  @MethodSource("identicalTypes")
  void given__identical_type_reference__when__joined__then__original_type_is_retained(Ty type) {
    final var result = Tys.getCommonDenominator(type, type);
    Assertions.assertAll(
      () -> Assertions.assertSame(type, result.ty()),
      () -> Assertions.assertArrayEquals(new TyDiffKind[0], result.diffs())
    );
  }

  static Stream<Arguments> unchangedSelectionRules() {
    return Stream.of(
      Arguments.of(Ty.INTEGER, Ty.INTEGER.toBuilder().flags(EnumSet.of(TyFlags.CONSTANT)).build()),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().signed(false).build()),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().kind(RealKind.DOUBLE).build()),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().flags(EnumSet.of(TyFlags.IMMUTABLE)).build()),
      Arguments.of(new TyOpaque(), new TyOpaque())
    );
  }

  @ParameterizedTest
  @MethodSource("unchangedSelectionRules")
  void given__existing_left_operand_selection__when__joined__then__refactoring_does_not_change_policy(Ty a, Ty b) {
    final var forward = Tys.getCommonDenominator(a, b);
    final var reverse = Tys.getCommonDenominator(b, a);
    Assertions.assertAll(
      () -> Assertions.assertSame(a, forward.ty()),
      () -> Assertions.assertSame(b, reverse.ty()),
      () -> Assertions.assertArrayEquals(new TyDiffKind[0], forward.diffs()),
      () -> Assertions.assertArrayEquals(new TyDiffKind[0], reverse.diffs())
    );
  }

  @Test
  void given__width_change_with_flags__when__joined__then__existing_flag_merge_is_preserved() {
    final var narrow = Ty.SHORT.toBuilder().flags(EnumSet.of(TyFlags.CONSTANT)).build();
    final var wide = Ty.INTEGER.toBuilder().flags(EnumSet.of(TyFlags.MUTABLE)).build();
    final var expected = Ty.INTEGER.toBuilder().flags(EnumSet.of(TyFlags.MUTABLE)).build();
    final var result = Tys.getCommonDenominator(narrow, wide);
    Assertions.assertAll(
      () -> Assertions.assertEquals(expected, result.ty()),
      () -> Assertions.assertArrayEquals(new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXT}, result.diffs())
    );
  }

  @Test
  void given__equivalent_tuple_layouts__when__joined__then__no_spurious_union() {
    final var inferred = tuple(Ty.INTEGER, Ty.BOOLEAN);
    final var explicit = tuple(Ty.INTEGER.toBuilder().width(new BitWidth(32, true)).build(), Ty.BOOLEAN);
    Assertions.assertAll(
      () -> Assertions.assertEquals(inferred, Tys.union(inferred, explicit)),
      () -> Assertions.assertEquals(explicit, Tys.union(explicit, inferred)),
      () -> Assertions.assertInstanceOf(TyUnion.class, Tys.union(inferred, tuple(Ty.LONG, Ty.BOOLEAN)))
    );
  }

  @Test
  void given__tuple_decimal_flag_difference__when__common_type_is_selected__then__types_are_not_equivalent() {
    final var a = tuple(Ty.DECIMAL);
    final var b = tuple(new TyValueNumberScaled(Ty.DECIMAL.width(), 10, true, EnumSet.of(TyFlags.CONSTANT)));
    final var result = Tys.getCommonDenominator(a, b);
    Assertions.assertAll(
      () -> Assertions.assertNull(result.ty()),
      () -> Assertions.assertArrayEquals(new TyDiffKind[]{TyDiffKind.INCOMPATIBLE}, result.diffs())
    );
  }
}
