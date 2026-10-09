package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.Ty;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueArray;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.TypeComparison;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirFunctionReturnTypingTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "[10, 20, 30]",
    "{ return [10, 20, 30]; }"
  })
  void given__partial_array_return_constraint__when__body_length_differs__then__signature_and_actual_result_remain_distinct(
    final String body
  ) {
    final var root = Inf.codeToHir("val make = (): [;2] => %s; make".formatted(body));
    final var function = functions(root).getFirst();
    final var annotation = function.signature().returnTypeAnnotation();
    assertThrows(InvalidTypeConversionException.class, () -> new HirToThirRaising(new MachineTarget(64)).raise(root));
    assertAll(
      () -> assertSame(annotation.expression(), function.signature().returnTypeAnnotation().expression()),
      () -> assertTrue(TypeComparison.sameValueType(new TyValueArray(Ty.INTEGER, 2), function.signature().ty().returnTy())),
      () -> assertTrue(TypeComparison.sameValueType(new TyValueArray(Ty.INTEGER, 3), HirBodyResultTyping.resolve(function.body()))),
      () -> assertTrue(Tys.containsInferred(annotation.ty()))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val make = (): (uint8, [;2]) => (10, [true, false]); make",
    "val make = (): (uint8, [;2]) => { return (10, [true, false]); }; make"
  })
  void given__nested_array_return_inference__when__typed__then__explicit_tuple_member_and_array_length_are_preserved(
    final String code
  ) {
    final var root = Inf.codeToThir(code).root();
    final var function = functions(root).getFirst();
    final var result = assertInstanceOf(TyStruct.class, function.signature().ty().returnTy());
    assertAll(
      () -> assertTrue(TypeComparison.sameValueType(Ty.CHAR, result.fields()[0].ty())),
      () -> assertEquals(new TyValueArray(Ty.BOOLEAN, 2), result.fields()[1].ty()),
      () -> assertTrue(Tys.containsInferred(function.signature().returnTypeAnnotation().ty()))
    );
  }

  @Test
  void given__nested_array_return_constraint__when__executed__then__inferred_member_and_explicit_layout_agree() {
    assertEquals(true, Inf.codeToResult("""
      val make = (): (uint8, [;2]) => (10, [true, false]);
      (make())[1][0]
      """).resultValue());
  }

  @ParameterizedTest
  @MethodSource("bodyResults")
  void given__function_body__when__typed__then__reachable_returns_merge_with_normal_completion(
    final String body, final Ty expected
  ) {
    final var root = Inf.codeToThir("val use = (flag: bool) => %s; use(false)".formatted(body)).root();
    final var function = functions(root).getFirst();
    assertAll(
      () -> assertEquals(expected, function.signature().ty().returnTy()),
      () -> assertEquals(expected, root.ty()),
      () -> assertEquals(Ty.INFER, function.signature().returnTypeAnnotation().ty())
    );
  }

  private static Stream<Arguments> bodyResults() {
    return Stream.of(
      Arguments.of("7", Ty.INTEGER),
      Arguments.of("{ val n = 7; }", Ty.VOID),
      Arguments.of("{ var n: int; }", Ty.VOID),
      Arguments.of("{ var n = 7; n = 8; }", Ty.VOID),
      Arguments.of("{ var n = 7; n += 1; }", Ty.VOID),
      Arguments.of("{ if (flag) { return 7; }; true }", Tys.union(Ty.INTEGER, Ty.BOOLEAN)),
      Arguments.of("{ if (flag) { return 7; }; val n = true; }", Tys.union(Ty.INTEGER, Ty.VOID)),
      Arguments.of("if (flag) then 7", Tys.union(Ty.INTEGER, Ty.VOID)),
      Arguments.of("if (flag) { return 7; }", Tys.union(Ty.INTEGER, Ty.VOID)),
      Arguments.of("if (flag) { return 7; } else { return true; }", Tys.union(Ty.INTEGER, Ty.BOOLEAN)),
      Arguments.of("{ return 7; true }", Ty.INTEGER),
      Arguments.of("{ return 7; return true; }", Ty.INTEGER),
      Arguments.of("{ var n = 0; n = { return 7; }; true }", Ty.INTEGER),
      Arguments.of("{ if ({ return 7; }) then true else false; true }", Ty.INTEGER),
      Arguments.of("flag && { return 7; }", Tys.union(Ty.INTEGER, Ty.BOOLEAN)),
      Arguments.of("flag || { return 7; }", Tys.union(Ty.INTEGER, Ty.BOOLEAN)),
      Arguments.of("({ return 7; }) && { return true; }", Ty.INTEGER),
      Arguments.of("{ flag && { return 7; }; true }", Tys.union(Ty.INTEGER, Ty.BOOLEAN))
    );
  }

  @ParameterizedTest
  @MethodSource("generatedBodies")
  void given__generated_body__when__typed__then__only_reachable_results_contribute(
    final Hir.Expression body, final Ty expected
  ) {
    final var function = new Hir.Function(
      new Hir.FunctionSignature(new Hir.Parameter[0], false, new Hir.DynamicTy(Ty.INFER)), body
    );
    HirTyCommonVisitorPass.pass(function);
    assertEquals(expected, function.signature().ty().returnTy());
  }

  private static Stream<Arguments> generatedBodies() {
    return Stream.of(
      Arguments.of(new Hir.Expressions(new Hir.Expression[0]), Ty.VOID),
      Arguments.of(new Hir.Expressions(new Hir.Expression[]{
        new Hir.Loop(new Hir.LoopContinue()), new Hir.Literal("true", Ty.BOOLEAN)
      }), Ty.DEADEND),
      Arguments.of(new Hir.Expressions(new Hir.Expression[]{
        new Hir.Loop(new Hir.Return(new Hir.Literal("7", Ty.INTEGER))), new Hir.Literal("true", Ty.BOOLEAN)
      }), Ty.INTEGER),
      Arguments.of(new Hir.Expressions(new Hir.Expression[]{
        new Hir.Loop(new Hir.Expressions(new Hir.Expression[]{
          new Hir.LoopBreak(null), new Hir.Return(new Hir.Literal("true", Ty.BOOLEAN))
        })), new Hir.Literal("7", Ty.INTEGER)
      }), Ty.INTEGER)
    );
  }

  @Test
  void given__nested_function__when__outer_result_is_inferred__then__nested_returns_stay_in_their_own_scope() {
    final var root = Inf.codeToThir("""
      val use = (flag: bool) => {
        val inner = () => { return true; };
        if (flag) { return 7; };
        8
      };
      use(false)
      """).root();
    final var functions = functions(root);
    assertEquals(2, functions.size());
    assertAll(
      () -> assertEquals(Ty.INTEGER, functions.get(0).signature().ty().returnTy()),
      () -> assertEquals(Ty.BOOLEAN, functions.get(1).signature().ty().returnTy()),
      () -> assertEquals(Ty.INTEGER, root.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "int",
    "uint8"
  })
  void given__explicit_return_constraint__when__inference_runs__then__source_annotation_and_resolved_return_are_retained(
    final String annotation
  ) {
    final var root = Inf.codeToThir("""
      val use = (flag: bool): %s => { if (flag) { return 7; }; 8 };
      use(false)
      """.formatted(annotation)).root();
    final var function = functions(root).getFirst();
    final var source = function.signature().returnTypeAnnotation();
    final var signature = function.signature().ty();
    final var returnType = signature.returnTy();

    HirFunctionReturnTyping.resolve(function);

    assertAll(
      () -> assertSame(source, function.signature().returnTypeAnnotation()),
      () -> assertSame(signature, function.signature().ty()),
      () -> assertEquals(source.ty(), returnType),
      () -> assertSame(returnType, function.signature().ty().returnTy())
    );
  }

  @Test
  void given__alias_return_constraint__when__inference_runs__then__the_source_alias_is_retained() {
    final var root = Inf.codeToThir("""
      val Result = struct { val value: int; };
      val use = (): Result => new heap Result { value = 7; };
      use()
      """).root();
    final var function = functions(root).getFirst();
    final var annotation = function.signature().returnTypeAnnotation();
    final var signature = function.signature().ty();

    HirFunctionReturnTyping.resolve(function);

    assertAll(
      () -> assertInstanceOf(Hir.Identifier.class, annotation.expression()),
      () -> assertSame(annotation.expression(), function.signature().returnTypeAnnotation().expression()),
      () -> assertSame(signature, function.signature().ty()),
      () -> assertEquals(annotation.ty(), signature.returnTy())
    );
  }

  @Test
  void given__inferred_result__when__body_completion_is_refined__then__resolved_return_refreshes_without_changing_annotation() {
    final var root = Inf.codeToThir("val use = () => 7; use()").root();
    final var function = functions(root).getFirst();
    final var annotation = function.signature().returnTypeAnnotation();
    function.body(new Hir.Literal("true", Ty.BOOLEAN));

    HirFunctionReturnTyping.resolve(function);
    HirFunctionReturnTyping.resolve(function);

    assertAll(
      () -> assertSame(annotation, function.signature().returnTypeAnnotation()),
      () -> assertEquals(Ty.INFER, annotation.ty()),
      () -> assertEquals(Ty.BOOLEAN, function.signature().ty().returnTy())
    );
  }

  private static ArrayList<Hir.Function> functions(final Hir.Expression root) {
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function function) {
        functions.add(function);
        HirVisitor.super.visitFunction(function);
      }
    });
    return functions;
  }
}
