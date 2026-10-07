package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyField;
import org.inf.ty.TyParam;
import org.inf.ty.TyStruct;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.inf.ty.util.TypeComparison;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

class HirTyCommonVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "[17;double;1]",
    "[17.0f;double;1]"
  })
  void given__noninteger_array_destination__when__common_typing_runs__then__existing_numeric_literal_adaptation_is_preserved(
    final String code
  ) {
    final var root = prepare(code);
    HirTyCommonVisitorPass.pass(root);
    final var literals = new ArrayList<Hir.Literal>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal expression) {
        if (expression.content().startsWith("17")) {
          literals.add(expression);
        }
      }
    });
    Assertions.assertEquals(1, literals.size());
    Assertions.assertEquals(Ty.DOUBLE, literals.getFirst().ty());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v; }; s.fn",
    "val S = struct { val fn: Fn; }; val make = () => new heap S { fn = (v) => v; }; make().fn",
    "val S = struct { val fn: Fn; }; val make = () => { val s = new heap S { fn = (v) => v; }; s; }; make().fn",
    "val S = struct { val fn: Fn; }; val values = [new heap S { fn = (v) => v; }]; values[0].fn",
    "val f: Fn = (v) => v; val wrapper = (inner = (call = f,),); wrapper.inner.call"
  })
  void given__function_valued_receivers__when__available_types_are_resolved__then__contextual_passes_can_read_the_signature(
    final String expression
  ) {
    final var root = prepare("val Fn = (value: int): int; %s".formatted(expression));
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    final var integer = Tys.fromString("int", new MachineTarget(64));
    final var expected = new TyFn(new TyParam[]{new TyParam("value", integer)}, false, integer);
    Assertions.assertTrue(TypeComparison.sameValueType(root.ty(), expected));
  }

  @Test
  void given__inferred_function_return__when__types_are_prepared__then__return_annotation_is_left_for_final_resolution() {
    final var root = prepare("val Fn = (value: (uint8,)): int; val f: Fn = (v) => v[0] + 0; f((1,))");
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function expression) {
        functions.add(expression);
        HirVisitor.super.visitFunction(expression);
      }
    });
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    Assertions.assertTrue(Tys.isInferred(functions.getFirst().signature().returnType().ty()));
    HirFunctionContextualTypingVisitorPass.pass(root);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirTupleContextVisitorPass.pass(root);
    HirIntegerLiteralTypingVisitorPass.pass(root);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirTupleSlotConversionVisitorPass.pass(root);
    HirTyCommonVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertTrue(TypeComparison.sameValueType(
        Tys.fromString("int", new MachineTarget(64)), functions.getFirst().signature().returnType().ty())),
      () -> Assertions.assertEquals(Tys.fromString("int", new MachineTarget(64)), root.ty())
    );
  }

  @Test
  void given__explicit_function_type_alias__when__staged_resolution_runs__then__the_annotation_is_preserved() {
    final var root = prepare("val Fn = (value: int): int; val f: Fn = (v) => v; f");
    final var declarations = new ArrayList<Hir.Dec>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        declarations.add(declaration);
        HirVisitor.super.visitDec(declaration);
      }
    });
    final var declaration = declarations.stream().filter(value -> value.lexeme().name().equals("f")).findFirst().orElseThrow();
    final var annotation = declaration.valueType();
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    Assertions.assertSame(annotation, declaration.valueType());
    HirFunctionContextualTypingVisitorPass.pass(root);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirTupleContextVisitorPass.pass(root);
    HirIntegerLiteralTypingVisitorPass.pass(root);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirTupleSlotConversionVisitorPass.pass(root);
    HirTyCommonVisitorPass.pass(root);
    Assertions.assertSame(annotation, declaration.valueType());
  }

  @Test
  void given__expected_tuple_type__when__only_common_inference_runs__then__no_contextual_rewriting() {
    final var root = HirTyIdentifierToTyTransformerPass.pass(
      Inf.codeToHir("val t: (uint8,) = (1,); t"), new MachineTarget(64)
    );
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.pass(root);
    final var literals = new ArrayList<Hir.Literal>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(Hir.Literal literal) {
        literals.add(literal);
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, literals.size()),
      () -> Assertions.assertEquals(Ty.INTEGER, literals.getFirst().ty()),
      () -> Assertions.assertThrows(InvalidTypeConversionException.class, () -> HirTupleValidationVisitorPass.pass(root))
    );
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void given__inferred_declaration__when__independent_preparation_repeats__then__annotation_and_latest_value_type_are_preserved(
    final boolean partiallyInferred
  ) {
    final var root = prepare("val t = (1,); t");
    final var declarations = new ArrayList<Hir.Dec>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        declarations.add(declaration);
        HirVisitor.super.visitDec(declaration);
      }
    });
    final var declaration = declarations.getFirst();
    if (partiallyInferred) {
      declaration.valueType(new Hir.TyExpr(new TyStruct(new TyField[]{new TyField(null, Ty.INFER)}, true)));
    }
    final var annotation = declaration.valueType();
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    final var initial = declaration.valueTy();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal literal) {
        literal.ty(Ty.LONG);
      }
    });
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, declaration.valueType()),
      () -> Assertions.assertTrue(Tys.containsInferred(annotation.ty())),
      () -> Assertions.assertNotEquals(initial, declaration.valueTy()),
      () -> Assertions.assertEquals(root.ty(), declaration.valueTy())
    );
    HirTyCommonVisitorPass.pass(root);
    Assertions.assertEquals(declaration.valueTy(), declaration.valueType().ty());
  }

  private static Hir.Expression prepare(final String code) {
    final var root = HirTyIdentifierToTyTransformerPass.pass(Inf.codeToHir(code), new MachineTarget(64));
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    return root;
  }
}
