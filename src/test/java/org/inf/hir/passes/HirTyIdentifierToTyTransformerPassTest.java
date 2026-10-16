package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.DynamicTy;
import org.inf.hir.DynamicTyDefault;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.hir.util.Hirs;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyFn;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueArray;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class HirTyIdentifierToTyTransformerPassTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val v: int = 7; v | int",
    "val use = (p: bool) => p; use | bool",
    "val use = (): bool => true; use | bool",
    "[7;int;1] | int"
  })
  void given__source_builtin_annotation__when__resolved__then__the_constraint_is_static_and_source_identifier_is_unchanged(
    final String code, final String name
  ) {
    final var root = Inf.codeToThir(code).root();
    final var annotation = sourceAnnotation(root);
    assertEquals(Tys.fromString(name, MachineTarget.T64), annotation.ty(), "Should be %s".formatted(name));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val v: (int, bool) = (7, true); v",
    "val use = (p: (int, bool)) => p; use",
    "val use = (): (int, bool) => (7, true); use",
    "[(7, true);(int, bool);1]"
  })
  void given__source_composite_annotation__when__typed__then__builtin_leaves_are_resolved_without_mutating_source_identifiers(
    final String code
  ) {
    final var root = Inf.codeToHir(code);
    final var annotation = sourceAnnotation(root);
    final var tuple = assertInstanceOf(Hir.Tuple.class, annotation);
    final var integer = assertInstanceOf(Hir.Identifier.class, tuple.children()[0].value());
    final var booleanType = assertInstanceOf(Hir.Identifier.class, tuple.children()[1].value());
    final var target = MachineTarget.T64;

    HirTyIdentifierToTyTransformerPass.pass(root, target);

    final var resolvedInteger = assertInstanceOf(Hir.BuiltInTy.class, tuple.children()[0].value());
    final var resolvedBoolean = assertInstanceOf(Hir.BuiltInTy.class, tuple.children()[1].value());
    assertAll(
      () -> assertSame(tuple, annotation),
      () -> assertEquals(Tys.fromString("int", target), resolvedInteger.ty()),
      () -> assertSame(Ty.BOOLEAN, resolvedBoolean.ty()),
      () -> assertNull(integer.target()),
      () -> assertNull(booleanType.target())
    );

    final var typed = new HirToThirRaising(target).raise(root).root().ty();
    final var result = switch (typed) {
      case TyFn function -> function.returnTy();
      case TyValueArray array -> array.elementType();
      default -> typed;
    };
    assertEquals(new TyStruct(new TyField[]{
      new TyField(null, Tys.fromString("int", target)),
      new TyField(null, Ty.BOOLEAN)
    }, true), result);
  }

  @ParameterizedTest
  @ValueSource(ints = {32, 64})
  void given__source_target_dependent_annotation__when__typed__then__the_thir_compilation_target_determines_its_type(
    final int pointerBitSize
  ) {
    final var root = Inf.codeToHir("val size: usize = 7; size");
    final var annotation = sourceAnnotation(root);
    final var expected = pointerBitSize == 32 ? Ty.UINTEGER : Ty.ULONG;
    final var identifier = assertInstanceOf(Hir.Identifier.class, annotation);

    assertEquals("usize", identifier.name());
    assertEquals(null, annotation.ty());

    final var typed = new HirToThirRaising(new MachineTarget(pointerBitSize)).raise(root).root();

    assertAll(
      () -> assertSame(null, annotation.ty()),
      () -> assertFalse(Hirs.has(typed, identifier), "Identifier should have been replaced by builtin type expr"),
      () -> assertSame(expected, typed.ty())
    );
  }

  @Test
  void given__a_value_identifier_named_like_a_builtin__when__type_syntax_is_resolved__then__the_value_binding_is_preserved() {
    final var root = Inf.codeToHir("val int = 7; val pair: (int, bool) = (7, true); int");
    final var references = new ArrayList<Hir.Identifier>();
    root.visit(new HirVisitor() {
      @Override
      public void visitIdentifier(final Hir.Identifier identifier) {
        if (identifier.name().equals("int")) {
          references.add(identifier);
        }
      }
    });
    assertEquals(2, references.size());
    final var typeReference = references.getFirst();
    final var valueReference = references.getLast();

    final var typed = new HirToThirRaising(MachineTarget.T64).raise(root).root();

    final var declaration = assertInstanceOf(Hir.Dec.class, valueReference.target());
    assertAll(
      () -> assertEquals("int", declaration.lexeme().name()),
      () -> assertSame(Ty.INTEGER, declaration.ty().structuralTy()),
      () -> assertSame(Ty.INTEGER, valueReference.ty()),
      () -> assertSame(Ty.INTEGER, typed.ty()),
      () -> assertNull(typeReference.target()),
      () -> assertEquals("int", valueReference.name())
    );
  }

  /// TODO: Remove this ugly method. What's even the point of it? The tests cases are all weird
  ///         Especially the fact that it deals with suppliers for some odd reason...
  private static Hir.Expression sourceAnnotation(final Hir.Expression root) {
    final var found = new ArrayList<Hir.Expression>();
    root.visit(new HirVisitor() {
      private void add(final DynamicTy<?> annotation) {
        if (annotation.expression() != null) {
          found.add(annotation.expression());
        }
      }
      private void addExpr(final Hir.Expression expr) {
        if (expr != null) {
          found.add(expr);
        }
      }

      @Override
      public void visitDecType(DynamicTy annotation) {
        add(annotation);
        HirVisitor.super.visitDecType(annotation);
      }

      @Override
      public void visitParameterType(Hir.Expression expr) {
        addExpr(expr);
        HirVisitor.super.visitParameterType(expr);
      }

      @Override
      public void visitFunctionSignatureReturnType(DynamicTy annotation) {
        add(annotation);
        HirVisitor.super.visitFunctionSignatureReturnType(annotation);
      }

      @Override
      public void visitArrayElementType(DynamicTy annotation) {
        add(annotation);
        HirVisitor.super.visitArrayElementType(annotation);
      }
    });
    assertEquals(1, found.size());
    return found.getFirst();
  }
}
