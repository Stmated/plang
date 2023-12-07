package com.github.stmated.plang;

import com.github.stmated.plang.ast.Ast.Program;
import com.github.stmated.plang.ast.TokenToAstRaising;
import com.github.stmated.plang.hir.AstToHirRaising;
import com.github.stmated.plang.hir.Hir;
import com.github.stmated.plang.lexer.PlangLexer;
import com.github.stmated.plang.lexer.PlangLexerSteps;
import com.github.stmated.plang.llvm.lowering.MirToLLVMLowering;
import com.github.stmated.plang.mir.MirLoweringResult;
import com.github.stmated.plang.mir.ThirToMirLowering;
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

  public static Hir.Expression codeToHir(String code) {
    final var ast = Plang.codeToAst(code);
    return AstToHirRaising.lower_program(ast, PlangCompileOptions.builder().build().machineTarget());
  }

  public static ThirRaiseResult codeToThir(String code) {
    final var hir = Plang.codeToHir(code);
    final var options = PlangCompileOptions.builder().build();
    return new HirToThirRaising(options.machineTarget()).raise(hir);
  }

  public static MirLoweringResult codeToMir(String code) {

    final var options = PlangCompileOptions.builder().build();
    final var ast = Plang.codeToAst(code);
    final var hir = AstToHirRaising.lower_program(ast, options.machineTarget());
    final var thir = new HirToThirRaising(options.machineTarget()).raise(hir);
    return ThirToMirLowering.lower(thir, options.machineTarget());
  }

  public static <T> Result<T> codeToResult(String code) {
    return codeToResult(code, PlangRunOptions.builder().build());
  }

  public static <T> Result<T> codeToResult(String code, PlangRunOptions options) {

    final var ast = Plang.codeToAst(code);
    final var hir = AstToHirRaising.lower_program(ast, options.machineTarget());
    return hirToResult(hir, options);
  }

  public static <T> Result<T> hirToResult(Hir.Expression hir, PlangRunOptions options) {

    final var thir = new HirToThirRaising(options.machineTarget()).raise(hir);
    final var mir = ThirToMirLowering.lower(thir, options.machineTarget());
    final var llvmLowering = new MirToLLVMLowering();
    return llvmLowering.lower_script(mir, "script", options);
  }

  private static InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }
}
