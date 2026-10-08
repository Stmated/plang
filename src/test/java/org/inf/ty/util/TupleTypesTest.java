package org.inf.ty.util;

import org.inf.ty.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

class TupleTypesTest {

  private static TyStruct tuple(Ty... types) {
    return new TyStruct(Arrays.stream(types).map(type -> new TyField(null, type)).toArray(TyField[]::new));
  }

  static Stream<Arguments> tupleContainment() {
    return Stream.of(
      Arguments.of(Ty.INTEGER, false),
      Arguments.of(tuple(Ty.INTEGER), true),
      Arguments.of(new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)}), false),
      Arguments.of(new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)}, true), true),
      Arguments.of(new TyStruct(new TyField[]{new TyField("x", tuple(Ty.INTEGER))}), true),
      Arguments.of(new TyValueArray(tuple(Ty.INTEGER), 2), true),
      Arguments.of(new TyPointer<>(tuple(Ty.INTEGER)), true),
      Arguments.of(new TyFn(new TyParam[0], false, tuple(Ty.INTEGER)), true),
      Arguments.of(new TyUnion(new Ty[]{Ty.INTEGER, tuple(Ty.INTEGER)}), true),
      Arguments.of(null, false)
    );
  }

  static Stream<Ty> partialConstraints() {
    return Stream.of(
      Ty.INFER,
      tuple(Ty.INTEGER, new TyValueArray(Ty.INFER, 2)),
      new TyValueArray(Ty.INFER, 1)
    );
  }

  @ParameterizedTest
  @MethodSource("partialConstraints")
  void given__partial_member_constraint__when__validated__then__placeholders_are_allowed_only_in_annotations(final Ty type) {
    Assertions.assertAll(
      () -> Assertions.assertDoesNotThrow(() -> TupleTypes.requireElementType(type, true)),
      () -> Assertions.assertThrows(IllegalArgumentException.class, () -> TupleTypes.requireElementType(type))
    );
  }

  @Test
  void given__fully_named_tuple__when__formatted__then__singleton_comma_is_preserved() {
    final var type = new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)}, true);
    Assertions.assertAll(
      () -> Assertions.assertFalse(type.hasUnnamedFields()),
      () -> Assertions.assertEquals("(x: %s,)".formatted(Ty.INTEGER), type.toString())
    );
  }

  @ParameterizedTest
  @MethodSource("tupleContainment")
  void given__type__when__inspected__then__nested_tuples_are_detected(Ty type, boolean containsTuple) {
    Assertions.assertEquals(containsTuple, TupleTypes.containsTuple(type));
  }

  @Test
  void given__unnamed_slots__when__formatted__then__labels_are_omitted() {
    Assertions.assertAll(
      () -> Assertions.assertEquals("(" + Ty.INTEGER + ",)", tuple(Ty.INTEGER).toString()),
      () -> Assertions.assertEquals("struct {[x: " + Ty.INTEGER + "]}", new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)}).toString())
    );
  }

}
