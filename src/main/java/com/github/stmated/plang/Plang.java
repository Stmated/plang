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

  public static Hir.Program astToHir(Program ast, PlangCompileOptions options) {
    return AstToHirRaising.lower_program(ast, options.machineTarget());
  }

  public static ThirRaiseResult hirToThir(Hir.Program hir, PlangCompileOptions options) {
    return new HirToThirRaising(options.machineTarget()).raise(hir);
  }

  public static MirLoweringResult hirToMir(Hir.Program hir, PlangCompileOptions options) {

    final var thir = new HirToThirRaising(options.machineTarget()).raise(hir);
    return thirToMir(thir, options);
  }

  public static MirLoweringResult thirToMir(ThirRaiseResult thir, PlangCompileOptions options) {
    return ThirToMirLowering.lower(thir, options.machineTarget());
  }

  public static Hir.Program codeToHir(String code) {
    return codeToHir(code, PlangCompileOptions.builder().build());
  }

  public static Hir.Program codeToHir(String code, PlangCompileOptions options) {
    final var ast = Plang.codeToAst(code);
    return Plang.astToHir(ast, options);
  }

  public static ThirRaiseResult codeToThir(String code) {
    return codeToThir(code, PlangCompileOptions.builder().build());
  }

  public static ThirRaiseResult codeToThir(String code, PlangCompileOptions options) {

    final var hir = Plang.codeToHir(code);
    return Plang.hirToThir(hir, options);
  }

  public static MirLoweringResult codeToMir(String code) {
    return codeToMir(code, PlangCompileOptions.builder().build());
  }

  public static MirLoweringResult codeToMir(String code, PlangCompileOptions options) {

    final var ast = Plang.codeToAst(code);
    final var hir = Plang.astToHir(ast, options);
    return Plang.hirToMir(hir, options);
  }

  public static <T> Result<T> hirToResult(Hir.Program hir, PlangRunOptions options) {

    final var mir = Plang.hirToMir(hir, options);
    return Plang.mirToResult(mir, options);
  }

  public static <T> Result<T> mirToResult(MirLoweringResult mir, PlangRunOptions options) {

    final var llvmLowering = new MirToLLVMLowering();
    return llvmLowering.lower_script(mir, "script", options);
  }

  public static <T> Result<T> codeToResult(String code) {
    return codeToResult(code, PlangRunOptions.builder().build());
  }

  public static <T> Result<T> codeToResult(String code, PlangRunOptions options) {

    final var ast = Plang.codeToAst(code);
    final var hir = Plang.astToHir(ast, options);
    final var mir = Plang.hirToMir(hir, options);
    return Plang.mirToResult(mir, options);
  }

  private static InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }
}
