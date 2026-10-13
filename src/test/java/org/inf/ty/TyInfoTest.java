package org.inf.ty;

import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TyInfoTest {

  static Stream<Arguments> projections() {
    return Stream.of(
      Arguments.of(Ty.INTEGER, Ty.INTEGER, null, Ty.INTEGER),
      Arguments.of(Ty.VOID, Ty.BOOLEAN, Ty.INFER, Ty.BOOLEAN),
      Arguments.of(Ty.VOID, Ty.DEADEND, Ty.INFER, null),
      Arguments.of(Ty.DEADEND, Ty.INTEGER, null, Ty.INTEGER),
      Arguments.of(Ty.DEADEND, Ty.DEADEND, Ty.INFER, null),
      Arguments.of(null, Ty.INTEGER, Ty.BOOLEAN, Ty.INTEGER),
      Arguments.of(Ty.VOID, null, Ty.BOOLEAN, null),
      Arguments.of(null, null, null, null)
    );
  }

  @ParameterizedTest
  @MethodSource("projections")
  void given__independent_type_answers__when__projected__then__completion_structure_and_constraints_remain_distinct(
    final Ty completion, final Ty inputStructural, final Ty constraint, final Ty structural
  ) {
    final var info = new TyInfo(completion, inputStructural, constraint);
    assertAll(
      () -> assertSame(completion, info.completionTy()),
      () -> assertSame(constraint, info.declaredConstraintTy()),
      () -> assertSame(structural, info.structural()),
      () -> assertSame(structural, info.structuralTy()),
      () -> assertEquals(completion == Ty.DEADEND, info.isDeadend())
    );
  }

  @Test
  void given__nested_type_info__when__constructed__then__each_argument_supplies_only_its_selected_answer() {
    final var completion = new TyInfo(Ty.INTEGER, Ty.LONG, Ty.BOOLEAN);
    final var structural = new TyInfo(Ty.VOID, Ty.BOOLEAN, Ty.INTEGER);
    final var constraint = new TyInfo(Ty.INFER, Ty.INTEGER, Ty.BOOLEAN);
    final var info = new TyInfo(completion, structural, constraint);
    assertAll(
      () -> assertSame(Ty.INTEGER, info.completion()),
      () -> assertSame(Ty.BOOLEAN, info.structural()),
      () -> assertSame(Ty.INFER, info.declaredConstraint()),
      () -> assertSame(info.structural(), info.structuralTy()),
      () -> assertFalse(info.isDeadend())
    );
  }

  @Test
  void given__equal_structures_with_different_source_requirements__when__compared__then__container_equality_keeps_requirements() {
    final var omitted = new TyInfo(Ty.INTEGER, Ty.INTEGER, Ty.INFER);
    final var explicit = new TyInfo(Ty.INTEGER, Ty.INTEGER, Ty.INTEGER);
    assertAll(
      () -> assertNotEquals(omitted, explicit),
      () -> assertEquals(omitted, new TyInfo(Ty.INTEGER, Ty.INTEGER, Ty.INFER)),
      () -> assertSame(Tys.intern(omitted), Tys.intern(new TyInfo(Ty.INTEGER, Ty.INTEGER, Ty.INFER))),
      () -> assertNotSame(Tys.intern(omitted), Tys.intern(explicit))
    );
  }

  @Test
  void given__equal_but_independently_owned_projections__when__interned__then__the_selected_objects_are_preserved() {
    final var first = new TyValueArray(Ty.BOOLEAN, 2);
    final var second = new TyValueArray(Ty.BOOLEAN, 2);
    final var original = Tys.typeInfo(Ty.VOID, first, first);
    final var structural = Tys.withStructural(original, second);
    final var constraint = Tys.withDeclaredConstraint(original, second);
    assertAll(
      () -> assertEquals(original, structural),
      () -> assertEquals(original, constraint),
      () -> assertSame(first, original.structuralTy()),
      () -> assertSame(first, original.declaredConstraintTy()),
      () -> assertSame(second, structural.structuralTy()),
      () -> assertSame(first, structural.declaredConstraintTy()),
      () -> assertSame(first, constraint.structuralTy()),
      () -> assertSame(second, constraint.declaredConstraintTy()),
      () -> assertSame(structural, Tys.withStructural(original, second)),
      () -> assertSame(constraint, Tys.withDeclaredConstraint(original, second))
    );
  }

  @Test
  void given__type_info__when__formatted__then__structural_information_and_source_requirements_are_visible() {
    assertAll(
      () -> assertEquals("(completion=DEADEND, structural=int(32?))", new TyInfo(Ty.DEADEND, Ty.INTEGER, null).toString()),
      () -> assertEquals("(completion=int(32?), structural=int(32?), declaredConstraint=INFER)",
        new TyInfo(Ty.INTEGER, Ty.INTEGER, Ty.INFER).toString()),
      () -> assertEquals("(completion=VOID, structural=null)",
        new TyInfo(Ty.VOID, Ty.DEADEND, null).toString())
    );
  }
}
