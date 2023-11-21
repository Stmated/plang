package com.github.stmated.plang;

import com.github.stmated.plang.ast.Ast.Program;
import com.github.stmated.plang.hir.Hir;
import com.github.stmated.plang.hir.AstToHirRaising;
import com.github.stmated.plang.lexer.PlangLexer;
import com.github.stmated.plang.lexer.PlangLexerSteps;
import com.github.stmated.plang.llvm.lowering.MirToLLVMLowering;
import com.github.stmated.plang.mir.MirLoweringResult;
import com.github.stmated.plang.mir.ThirToMirLowering;
import com.github.stmated.plang.ast.TokenToAstRaising;
import com.github.stmated.plang.thir.raising.HirToThirRaising;
import com.github.stmated.plang.thir.raising.ThirRaiseResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Plang {

  public record Result<T>(T resultValue, String output, String error) {

  }

  public static Program codeToAst(String code) {

    final var pass2 = new PlangLexerSteps();
    try (final var tokens = new PlangLexer(stringToStream(code))) {
      final var transformed = pass2.transform(tokens);
      final var parser = new TokenToAstRaising(transformed);

      return parser.parse();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Hir.Program astToHir(Program ast) {
    return new AstToHirRaising().lower_program(ast);
  }

  public static ThirRaiseResult hirToThir(Hir.Program hir) {
    return new HirToThirRaising().raise(hir);
  }

  public static MirLoweringResult hirToMir(Hir.Program hir) {

    final var thir = new HirToThirRaising().raise(hir);
    return thirToMir(thir);
  }

  public static MirLoweringResult thirToMir(ThirRaiseResult thir) {
    return ThirToMirLowering.lower(thir);
  }

  public static Hir.Program codeToHir(String code) {
    final var ast = Plang.codeToAst(code);
    return Plang.astToHir(ast);
  }

  public static ThirRaiseResult codeToThir(String code) {

    final var hir = Plang.codeToHir(code);
    return Plang.hirToThir(hir);
  }

  public static MirLoweringResult codeToMir(String code) {

    final var ast = Plang.codeToAst(code);
    final var hir = Plang.astToHir(ast);
    return Plang.hirToMir(hir);
  }

  public static <T> Result<T> hirToResult(Hir.Program hir, Object[] arguments) {

    final var mir = Plang.hirToMir(hir);
    return Plang.mirToResult(mir, arguments);
  }

  public static <T> Result<T> mirToResult(MirLoweringResult mir, Object[] arguments) {

    final var llvmLowering = new MirToLLVMLowering();
    return llvmLowering.lower_script(mir, "script", arguments);
  }

  public static <T> Result<T> codeToResult(String code) {
    return codeToResult(code, new Object[0]);
  }

  public static <T> Result<T> codeToResult(String code, Object[] arguments) {

    final var ast = Plang.codeToAst(code);
    final var hir = Plang.astToHir(ast);
    final var mir = Plang.hirToMir(hir);
    return Plang.mirToResult(mir, arguments);
  }

  private static InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }
}
