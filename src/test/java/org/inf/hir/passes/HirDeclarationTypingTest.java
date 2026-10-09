package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueArray;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

class HirDeclarationTypingTest {

  static Stream<Arguments> memberConstraints() {
    return Stream.of(
      Arguments.of("val v: uint8 = 10; v", Ty.CHAR, Ty.CHAR),
      Arguments.of("val num: uint8 = 10; val v = (num, true); v", Ty.INFER, tuple(Ty.CHAR, Ty.BOOLEAN)),
      Arguments.of("val v: (uint8, bool) = (10, true); v",
        tuple(Ty.CHAR, Ty.BOOLEAN), tuple(Ty.CHAR, Ty.BOOLEAN)),
      Arguments.of("val v: [;2] = [10, 20]; v",
        new TyValueArray(Ty.INFER, 2), new TyValueArray(Ty.INTEGER, 2)),
      Arguments.of("val v: (uint8, [;2]) = (10, [true, false]); v",
        tuple(Ty.CHAR, new TyValueArray(Ty.INFER, 2)), tuple(Ty.CHAR, new TyValueArray(Ty.BOOLEAN, 2))),
      Arguments.of("val v: [;(uint8, [;2]);1] = [(10, [true, false])]; v",
        new TyValueArray(tuple(Ty.CHAR, new TyValueArray(Ty.INFER, 2)), 1),
        new TyValueArray(tuple(Ty.CHAR, new TyValueArray(Ty.BOOLEAN, 2)), 1)),
      Arguments.of("val v: ((uint8, [;2]), bool) = ((10, [true, false]), true); v",
        tuple(tuple(Ty.CHAR, new TyValueArray(Ty.INFER, 2)), Ty.BOOLEAN),
        tuple(tuple(Ty.CHAR, new TyValueArray(Ty.BOOLEAN, 2)), Ty.BOOLEAN)),
      Arguments.of("val v: [;[;2];1] = [[10, 20]]; v",
        new TyValueArray(new TyValueArray(Ty.INFER, 2), 1),
        new TyValueArray(new TyValueArray(Ty.INTEGER, 2), 1)),
      Arguments.of("val v: (left: uint8, right: [;2]) = (left = 10, right = [true, false]); v",
        namedTuple("left", Ty.CHAR, "right", new TyValueArray(Ty.INFER, 2)),
        namedTuple("left", Ty.CHAR, "right", new TyValueArray(Ty.BOOLEAN, 2))),
      Arguments.of("val v: (right: [;2], left: uint8) = (left = 10, right = [true, false]); v",
        namedTuple("right", new TyValueArray(Ty.INFER, 2), "left", Ty.CHAR),
        namedTuple("right", new TyValueArray(Ty.BOOLEAN, 2), "left", Ty.CHAR)),
      Arguments.of("val v: (uint16, [;2]) = (255u8, [true, false]); v",
        tuple(Ty.USHORT, new TyValueArray(Ty.INFER, 2)), tuple(Ty.USHORT, new TyValueArray(Ty.BOOLEAN, 2))),
      Arguments.of("val v: (uint8, [;2]) = { val unused = false; (10, [true, false]) }; v",
        tuple(Ty.CHAR, new TyValueArray(Ty.INFER, 2)), tuple(Ty.CHAR, new TyValueArray(Ty.BOOLEAN, 2))),
      Arguments.of("val v: (uint8, [;2]) = if (true) { (10, [true, false]) } else { (20, [false, true]) }; v",
        tuple(Ty.CHAR, new TyValueArray(Ty.INFER, 2)), tuple(Ty.CHAR, new TyValueArray(Ty.BOOLEAN, 2))),
      Arguments.of("val v: [;2] = if (true) { [10, 20] } else { [30, 40] }; v",
        new TyValueArray(Ty.INFER, 2), new TyValueArray(Ty.INTEGER, 2)),
      Arguments.of("val v: (uint8, bool) = if (true) { (10, true) } else { (20, false) }; v",
        tuple(Ty.CHAR, Ty.BOOLEAN), tuple(Ty.CHAR, Ty.BOOLEAN))
    );
  }

  @ParameterizedTest
  @MethodSource("memberConstraints")
  void given__source_declaration_constraint__when__full_typing_repeats__then__explicit_members_and_annotation_are_preserved(
    final String code, final Ty constraint, final Ty expected
  ) {
    var root = Inf.codeToThir(code).root();
    final var declaration = declaration(root);
    final var annotation = declaration.typeAnnotation();
    final var raising = new HirToThirRaising(new MachineTarget(64));
    for (var i = 0; i < 3; i++) {
      final var result = root;
      Assertions.assertAll(
        () -> Assertions.assertSame(annotation.expression(), declaration.typeAnnotation().expression()),
        () -> Assertions.assertEquals(constraint, declaration.typeAnnotation().ty()),
        () -> Assertions.assertEquals(Tys.isInferred(constraint) ? null : constraint, Tys.getAssignmentContextTy(declaration)),
        () -> Assertions.assertEquals(expected, declaration.resolvedTy()),
        () -> Assertions.assertEquals(expected, result.ty()),
        () -> Assertions.assertFalse(Tys.containsInferred(declaration.resolvedTy()))
      );
      if (i < 2) {
        root = raising.raise(root).root();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val v: (uint8, bool) = (true, true); v",
    "val v: (uint8, [;2]) = (true, [10, 20]); v",
    "val v: (uint8, [;2]) = (256, [true, false]); v",
    "val v: [;2] = [10]; v",
    "val v: (uint8, [;2]) = (10, [true]); v",
    "val v: [;(uint8, [;2]);1] = [(10, [true, false]), (20, [false, true])]; v",
    "val v: (uint8, bool) = (10,); v",
    "val v: (uint8, bool) = (10, true, false); v",
    "val v: (left: uint8, right: [;2]) = (left = 10, missing = [true, false]); v"
  })
  void given__incompatible_source_initializer__when__declaration_is_typed__then__constraint_is_not_discarded(
    final String code
  ) {
    Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir(code));
  }

  static Stream<Arguments> refreshableMembers() {
    return Stream.of(
      Arguments.of("val v: [;1] = [7]; v", new TyValueArray(Ty.INFER, 1),
        new TyValueArray(Ty.INTEGER, 1), new TyValueArray(Ty.SHORT, 1), new TyValueArray(Ty.LONG, 1)),
      Arguments.of("val v: (uint8, [;1]) = (10, [7]); v", tuple(Ty.CHAR, new TyValueArray(Ty.INFER, 1)),
        tuple(Ty.CHAR, new TyValueArray(Ty.INTEGER, 1)),
        tuple(Ty.CHAR, new TyValueArray(Ty.SHORT, 1)),
        tuple(Ty.CHAR, new TyValueArray(Ty.LONG, 1))),
      Arguments.of("val v: [;(uint8, [;1]);1] = [(10, [7])]; v",
        new TyValueArray(tuple(Ty.CHAR, new TyValueArray(Ty.INFER, 1)), 1),
        new TyValueArray(tuple(Ty.CHAR, new TyValueArray(Ty.INTEGER, 1)), 1),
        new TyValueArray(tuple(Ty.CHAR, new TyValueArray(Ty.SHORT, 1)), 1),
        new TyValueArray(tuple(Ty.CHAR, new TyValueArray(Ty.LONG, 1)), 1)),
      Arguments.of("val v: (values: [;1], code: uint8) = (code = 10, values = [7]); v",
        namedTuple("values", new TyValueArray(Ty.INFER, 1), "code", Ty.CHAR),
        namedTuple("values", new TyValueArray(Ty.INTEGER, 1), "code", Ty.CHAR),
        namedTuple("values", new TyValueArray(Ty.SHORT, 1), "code", Ty.CHAR),
        namedTuple("values", new TyValueArray(Ty.LONG, 1), "code", Ty.CHAR))
    );
  }

  @ParameterizedTest
  @MethodSource("refreshableMembers")
  void given__resolved_source_array_constraint__when__initializer_element_changes__then__only_inferred_members_refresh(
    final String code, final Ty constraint, final Ty initial, final Ty refined, final Ty latest
  ) {
    var root = Inf.codeToThir(code).root();
    final var declaration = declaration(root);
    final var annotation = declaration.typeAnnotation();
    final var literals = new ArrayList<Hir.Literal>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal literal) {
        if (literal.content().equals("7")) {
          literals.add(literal);
        }
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertEquals(initial, declaration.resolvedTy()),
      () -> Assertions.assertEquals(constraint, annotation.ty()),
      () -> Assertions.assertEquals(1, literals.size())
    );
    final var literal = literals.getFirst();
    literal.ty(Ty.SHORT);
    final var raising = new HirToThirRaising(new MachineTarget(64));
    for (var i = 0; i < 2; i++) {
      root = raising.raise(root).root();
      final var result = root;
      Assertions.assertAll(
        () -> Assertions.assertEquals(refined, declaration.resolvedTy()),
        () -> Assertions.assertEquals(refined, result.ty()),
        () -> Assertions.assertSame(annotation.expression(), declaration.typeAnnotation().expression()),
        () -> Assertions.assertEquals(constraint, declaration.typeAnnotation().ty()),
        () -> Assertions.assertEquals(constraint, Tys.getAssignmentContextTy(declaration))
      );
    }
    literal.ty(Ty.LONG);
    final var result = raising.raise(root).root();
    Assertions.assertAll(
      () -> Assertions.assertEquals(latest, declaration.resolvedTy()),
      () -> Assertions.assertEquals(latest, result.ty()),
      () -> Assertions.assertSame(annotation.expression(), declaration.typeAnnotation().expression()),
      () -> Assertions.assertEquals(constraint, declaration.typeAnnotation().ty()),
      () -> Assertions.assertEquals(constraint, Tys.getAssignmentContextTy(declaration))
    );
  }

  @Test
  void given__source_tuple_with_an_omitted_array_element_type__when__typing_repeats__then__annotation_tree_remains_inferred() {
    final var root = Inf.codeToThir("val v: (uint8, [;2]) = (10, [true, false]); v").root();
    final var declaration = declaration(root);
    final var annotation = Assertions.assertInstanceOf(Hir.Tuple.class, declaration.typeAnnotation().expression());
    final var array = Assertions.assertInstanceOf(Hir.Array.class, annotation.children()[1].value());
    final var elementAnnotation = array.elementType();

    new HirToThirRaising(new MachineTarget(64)).raise(root);

    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, declaration.typeAnnotation().expression()),
      () -> Assertions.assertSame(array, annotation.children()[1].value()),
      () -> Assertions.assertSame(elementAnnotation, array.elementType()),
      () -> Assertions.assertSame(Ty.INFER, elementAnnotation.ty()),
      () -> Assertions.assertEquals(new TyValueArray(Ty.INFER, 2), array.ty()),
      () -> Assertions.assertEquals(tuple(Ty.CHAR, new TyValueArray(Ty.INFER, 2)), annotation.ty()),
      () -> Assertions.assertEquals(tuple(Ty.CHAR, new TyValueArray(Ty.BOOLEAN, 2)), declaration.resolvedTy())
    );
  }

  static Stream<Arguments> lowerableDeclarations() {
    return Stream.of(
      Arguments.of("val v: (uint16, [;2]) = (255u8, [true, false]); v[0]", 255),
      Arguments.of("val v: (uint8, [;2]) = (10, [true, false]); v[1][0]", true),
      Arguments.of("val rows: [;(uint8, [;2]);1] = [(10, [true, false])]; rows[0][1][1]", false),
      Arguments.of("val v: (right: [;2], left: uint8) = (left = 10, right = [true, false]); v.left", 10),
      Arguments.of("val v: (right: [;2], left: uint8) = (left = 10, right = [true, false]); v.right[1]", false)
    );
  }

  @ParameterizedTest
  @MethodSource("lowerableDeclarations")
  void given__source_member_constraints__when__lowered_and_executed__then__slot_types_and_layout_agree(
    final String code, final Object expected
  ) {
    Assertions.assertEquals(expected, Inf.codeToResult(code).resultValue());
  }

  private static Hir.Dec declaration(final Hir.Expression root) {
    final var declarations = new ArrayList<Hir.Dec>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        if (declaration.lexeme().name().equals("v")) {
          declarations.add(declaration);
        }
        HirVisitor.super.visitDec(declaration);
      }
    });
    Assertions.assertEquals(1, declarations.size());
    return declarations.getFirst();
  }

  private static TyStruct tuple(final Ty... types) {
    return new TyStruct(Arrays.stream(types).map(type -> new TyField(null, type)).toArray(TyField[]::new), true);
  }

  private static TyStruct namedTuple(final String firstName, final Ty first, final String secondName, final Ty second) {
    return new TyStruct(new TyField[]{new TyField(firstName, first), new TyField(secondName, second)}, true);
  }
}
