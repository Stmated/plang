package org.inf.ty.util;

import org.inf.ty.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Stream;

class TypeComparisonTest {

  private static TyStruct tuple(Ty... types) {
    return new TyStruct(Arrays.stream(types).map(type -> new TyField(null, type)).toArray(TyField[]::new));
  }

  static Stream<Arguments> numericTypes() {
    return Stream.of(
      Arguments.of(Ty.INTEGER, Ty.INTEGER.toBuilder().build(), true),
      Arguments.of(Ty.INTEGER, Ty.INTEGER.toBuilder().width(new BitWidth(32, true)).build(), true),
      Arguments.of(Ty.INTEGER, Ty.LONG, false),
      Arguments.of(Ty.INTEGER, Ty.UINTEGER, false),
      Arguments.of(Ty.INTEGER, Ty.INTEGER_HEX, false),
      Arguments.of(Ty.INTEGER, Ty.INTEGER.toBuilder().flags(EnumSet.of(TyFlags.CONSTANT)).build(), false),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().build(), true),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().width(new BitWidth(32, true)).build(), true),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().width(new BitWidth(64, false)).build(), false),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().precision(8).build(), false),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().signed(false).build(), false),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().kind(RealKind.DOUBLE).build(), false),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().flags(EnumSet.of(TyFlags.IMMUTABLE)).build(), false),
      Arguments.of(Ty.DECIMAL, new TyValueNumberScaled(new BitWidth(128, false), 10, true, EnumSet.noneOf(TyFlags.class)), true),
      Arguments.of(Ty.DECIMAL, new TyValueNumberScaled(new BitWidth(128, true), 10, true, EnumSet.noneOf(TyFlags.class)), true),
      Arguments.of(Ty.DECIMAL, new TyValueNumberScaled(new BitWidth(64, false), 10, true, EnumSet.noneOf(TyFlags.class)), false),
      Arguments.of(Ty.DECIMAL, new TyValueNumberScaled(new BitWidth(128, false), 11, true, EnumSet.noneOf(TyFlags.class)), false),
      Arguments.of(Ty.DECIMAL, new TyValueNumberScaled(new BitWidth(128, false), 10, false, EnumSet.noneOf(TyFlags.class)), false),
      Arguments.of(Ty.DECIMAL, new TyValueNumberScaled(new BitWidth(128, false), 10, true, EnumSet.of(TyFlags.CONSTANT)), false),
      Arguments.of(Ty.INTEGER, Ty.FLOAT, false),
      Arguments.of(Ty.INTEGER, new TyValueNumberScaled(new BitWidth(32, false), 10, true, EnumSet.noneOf(TyFlags.class)), false),
      Arguments.of(Ty.FLOAT, new TyValueNumberScaled(new BitWidth(32, false), 7, true, EnumSet.noneOf(TyFlags.class)), false)
    );
  }

  @ParameterizedTest
  @MethodSource("numericTypes")
  void given__numeric_types__when__compared__then__only_width_explicitness_is_ignored(
    Ty actual, Ty expected, boolean equivalent
  ) {
    Assertions.assertAll(
      () -> Assertions.assertEquals(equivalent, TypeComparison.sameValueType(actual, expected)),
      () -> Assertions.assertEquals(equivalent, TypeComparison.sameValueType(expected, actual)),
      () -> Assertions.assertEquals(true, TypeComparison.sameValueType(actual, actual))
    );
  }

  static Stream<Arguments> aggregateTypes() {
    final var explicitInt = Ty.INTEGER.toBuilder().width(new BitWidth(32, true)).build();
    return Stream.of(
      Arguments.of(tuple(Ty.INTEGER), tuple(explicitInt), true),
      Arguments.of(new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)}, true),
        new TyStruct(new TyField[]{new TyField("x", explicitInt)}), true),
      Arguments.of(new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)}, true),
        new TyStruct(new TyField[]{new TyField("x", Ty.LONG)}), false),
      Arguments.of(tuple(tuple(Ty.INTEGER), Ty.BOOLEAN), tuple(tuple(explicitInt), Ty.BOOLEAN), true),
      Arguments.of(tuple(Ty.INTEGER), tuple(Ty.LONG), false),
      Arguments.of(tuple(Ty.INTEGER), tuple(Ty.UINTEGER), false),
      Arguments.of(tuple(Ty.INTEGER), tuple(Ty.INTEGER_HEX), false),
      Arguments.of(tuple(Ty.INTEGER), tuple(Ty.BOOLEAN), false),
      Arguments.of(tuple(Ty.INTEGER), tuple(Ty.INTEGER, Ty.INTEGER), false),
      Arguments.of(tuple(Ty.INTEGER), tuple(tuple(Ty.INTEGER)), false),
      Arguments.of(tuple(Ty.INTEGER), new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)}), false),
      Arguments.of(new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)}),
        new TyStruct(new TyField[]{new TyField("y", Ty.INTEGER)}), false),
      Arguments.of(new TyValueArray(tuple(Ty.INTEGER), 2), new TyValueArray(tuple(explicitInt), 2), true),
      Arguments.of(new TyValueArray(tuple(Ty.INTEGER), 2), new TyValueArray(tuple(explicitInt), 3), false),
      Arguments.of(new TyValueArray(Ty.INTEGER, null), new TyValueArray(explicitInt, null), true),
      Arguments.of(new TyValueArray(Ty.INTEGER, null), new TyValueArray(Ty.INTEGER, 2), false),
      Arguments.of(new TyPointer<>(tuple(Ty.INTEGER)), new TyPointer<>(tuple(Ty.LONG)), false),
      Arguments.of(new TyPointer<>(tuple(Ty.INTEGER)), new TyPointer<>(tuple(explicitInt)), true),
      Arguments.of(new TyPointer<>(Ty.INTEGER), new TyPointer<>(Ty.INTEGER, TyPointerAddressSpace.CUDA_GLOBAL), false),
      Arguments.of(new TyFn(new TyParam[]{new TyParam("a", Ty.INTEGER)}, false, Ty.BOOLEAN),
        new TyFn(new TyParam[]{new TyParam("b", explicitInt)}, false, Ty.BOOLEAN), true),
      Arguments.of(new TyFn(new TyParam[]{new TyParam("a", Ty.INTEGER)}, false, Ty.BOOLEAN),
        new TyFn(new TyParam[]{new TyParam("a", Ty.INTEGER)}, true, Ty.BOOLEAN), false),
      Arguments.of(new TyFn(new TyParam[0], false, Ty.INTEGER), new TyFn(new TyParam[0], false, Ty.BOOLEAN), false),
      Arguments.of(new TyUnion(new Ty[]{tuple(Ty.INTEGER), Ty.BOOLEAN}),
        new TyUnion(new Ty[]{tuple(explicitInt), Ty.BOOLEAN}), true),
      Arguments.of(new TyUnion(new Ty[]{Ty.INTEGER, Ty.BOOLEAN}), new TyUnion(new Ty[]{Ty.BOOLEAN, Ty.INTEGER}), false),
      Arguments.of(Ty.BOOLEAN, new TyValueBoolean(), true),
      Arguments.of(Ty.INTEGER, Ty.BOOLEAN, false),
      Arguments.of(null, Ty.INTEGER, false),
      Arguments.of(null, null, true)
    );
  }

  @ParameterizedTest
  @MethodSource("aggregateTypes")
  void given__value_types__when__compared__then__recursive_shape_and_properties_match(
    Ty actual, Ty expected, boolean equivalent
  ) {
    Assertions.assertAll(
      () -> Assertions.assertEquals(equivalent, TypeComparison.sameValueType(actual, expected)),
      () -> Assertions.assertEquals(equivalent, TypeComparison.sameValueType(expected, actual))
    );
  }

  static Stream<Arguments> nestedDecimalFlags() {
    final var plain = Ty.DECIMAL;
    final var constant = new TyValueNumberScaled(plain.width(), plain.scale(), plain.signed(), EnumSet.of(TyFlags.CONSTANT));
    return Stream.of(
      Arguments.of(tuple(plain), tuple(constant)),
      Arguments.of(new TyValueArray(plain, 2), new TyValueArray(constant, 2)),
      Arguments.of(new TyPointer<>(plain), new TyPointer<>(constant)),
      Arguments.of(new TyFn(new TyParam[0], false, plain), new TyFn(new TyParam[0], false, constant)),
      Arguments.of(new TyFn(new TyParam[]{new TyParam("a", plain)}, false, Ty.VOID),
        new TyFn(new TyParam[]{new TyParam("a", constant)}, false, Ty.VOID)),
      Arguments.of(new TyUnion(new Ty[]{plain, Ty.BOOLEAN}), new TyUnion(new Ty[]{constant, Ty.BOOLEAN}))
    );
  }

  @ParameterizedTest
  @MethodSource("nestedDecimalFlags")
  void given__nested_decimal_flag_difference__when__compared__then__record_equality_does_not_bypass_flags(Ty a, Ty b) {
    Assertions.assertAll(
      () -> Assertions.assertEquals(a, b),
      () -> Assertions.assertEquals(false, TypeComparison.sameValueType(a, b)),
      () -> Assertions.assertEquals(false, TypeComparison.sameValueType(b, a))
    );
  }

  static Stream<Arguments> numericProperties() {
    return Stream.of(
      Arguments.of(Ty.INTEGER, Ty.INTEGER, true, true, true, true),
      Arguments.of(Ty.INTEGER, Ty.INTEGER.toBuilder().width(new BitWidth(32, true)).build(), true, false, true, true),
      Arguments.of(Ty.INTEGER, Ty.LONG, false, true, true, true),
      Arguments.of(Ty.INTEGER, Ty.UINTEGER, true, true, false, true),
      Arguments.of(Ty.INTEGER, Ty.INTEGER_HEX, true, true, true, false),
      Arguments.of(Ty.INTEGER, Ty.FLOAT, true, true, true, true)
    );
  }

  @ParameterizedTest
  @MethodSource("numericProperties")
  void given__numeric_properties__when__compared_independently__then__each_relation_has_one_meaning(
    TyValueNumber a, TyValueNumber b, boolean width, boolean explicitness, boolean signedness, boolean radix
  ) {
    Assertions.assertAll(
      () -> Assertions.assertEquals(width, TypeComparison.sameBitWidth(a, b)),
      () -> Assertions.assertEquals(explicitness, TypeComparison.sameWidthExplicitness(a, b)),
      () -> Assertions.assertEquals(signedness, TypeComparison.sameSignedness(a, b)),
      () -> Assertions.assertEquals(radix, TypeComparison.sameRadix(a, b))
    );
  }
}
