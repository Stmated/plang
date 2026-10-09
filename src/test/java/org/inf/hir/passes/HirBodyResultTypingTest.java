package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirBodyResultTypingTest {

  @ParameterizedTest
  @MethodSource("scriptResults")
  void given__script_body__when__typed__then__external_results_and_body_completion_are_distinct(
    final String code, final Ty result
  ) {
    final var program = assertInstanceOf(Hir.Program.class,
      Inf.codeToThir("val flag = false; %s".formatted(code)).root());
    assertAll(
      () -> assertEquals(Ty.DEADEND, program.expressions().ty()),
      () -> assertEquals(result, program.ty()),
      () -> assertEquals(result, HirBodyResultTyping.resolve(program.expressions()))
    );
  }

  private static Stream<Arguments> scriptResults() {
    return Stream.of(
      Arguments.of("7", Ty.INTEGER),
      Arguments.of("return 7;", Ty.INTEGER),
      Arguments.of("val n = 7;", Ty.VOID),
      Arguments.of("var n: int;", Ty.VOID),
      Arguments.of("var n = 7; n = 8;", Ty.VOID),
      Arguments.of("var n = 7; n += 1;", Ty.VOID),
      Arguments.of("val n = 7; n", Ty.INTEGER),
      Arguments.of("if (flag) { return 7; }; true", Tys.union(Ty.INTEGER, Ty.BOOLEAN)),
      Arguments.of("if (flag) { return 7; }; val n = true;", Tys.union(Ty.INTEGER, Ty.VOID)),
      Arguments.of("if (flag) then 7", Tys.union(Ty.INTEGER, Ty.VOID)),
      Arguments.of("if (flag) { return 7; }", Tys.union(Ty.INTEGER, Ty.VOID)),
      Arguments.of("if (flag) { return 7; } else { return true; }", Tys.union(Ty.INTEGER, Ty.BOOLEAN)),
      Arguments.of("return 7; true", Ty.INTEGER),
      Arguments.of("return 7; return true;", Ty.INTEGER),
      Arguments.of("var n = 0; n = { return 7; }; true", Ty.INTEGER),
      Arguments.of("if ({ return 7; }) then true else false", Ty.INTEGER),
      Arguments.of("flag && { return 7; }", Tys.union(Ty.INTEGER, Ty.BOOLEAN)),
      Arguments.of("flag || { return 7; }", Tys.union(Ty.INTEGER, Ty.BOOLEAN)),
      Arguments.of("({ return 7; }) && { return true; }", Ty.INTEGER),
      Arguments.of("val inner = () => { return 7; }; true", Ty.BOOLEAN),
      Arguments.of("val inner = () => { return 7; };", Ty.VOID)
    );
  }

  @ParameterizedTest
  @MethodSource("generatedBodies")
  void given__generated_body__when__typed__then__returns_and_normal_completion_determine_external_results(
    final Hir.Expression body, final Ty completion, final Ty result
  ) {
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(body));
    assertAll(
      () -> assertEquals(completion, body.ty()),
      () -> assertEquals(result, program.ty())
    );
  }

  private static Stream<Arguments> generatedBodies() {
    return Stream.of(
      Arguments.of(new Hir.Literal("7", Ty.INTEGER), Ty.INTEGER, Ty.INTEGER),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.MUTABLE, new Hir.DynamicTy(Ty.INTEGER)),
        Ty.VOID, Ty.VOID),
      Arguments.of(new Hir.Assignment(
        new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.MUTABLE, new Hir.DynamicTy(Ty.INFER)),
        new Hir.Literal("7", Ty.INTEGER)
      ), Ty.VOID, Ty.VOID),
      Arguments.of(new Hir.Conditional(
        new Hir.Literal("true", Ty.BOOLEAN), new Hir.Return(new Hir.Literal("7", Ty.INTEGER)),
        new Hir.Literal("true", Ty.BOOLEAN)
      ), Ty.BOOLEAN, Tys.union(Ty.INTEGER, Ty.BOOLEAN)),
      Arguments.of(new Hir.Conditional(
        new Hir.Literal("true", Ty.BOOLEAN), new Hir.Return(new Hir.Literal("7", Ty.INTEGER)), null
      ), Ty.VOID, Tys.union(Ty.INTEGER, Ty.VOID)),
      Arguments.of(new Hir.Expressions(new Hir.Expression[0]), Ty.VOID, Ty.VOID),
      Arguments.of(new Hir.Loop(new Hir.LoopContinue()), Ty.DEADEND, Ty.DEADEND),
      Arguments.of(new Hir.Loop(new Hir.Return(new Hir.Literal("7", Ty.INTEGER))), Ty.DEADEND, Ty.INTEGER)
    );
  }

  @Test
  void given__resolved_program__when__body_completion_changes__then__external_result_is_refreshed() {
    final var program = assertInstanceOf(Hir.Program.class, Inf.codeToThir("7").root());
    program.expressions(new Hir.Literal("true", Ty.BOOLEAN));
    HirTyCommonVisitorPass.pass(program);
    assertAll(
      () -> assertEquals(Ty.BOOLEAN, program.ty()),
      () -> assertEquals(Ty.BOOLEAN, program.expressions().ty())
    );
  }
}
