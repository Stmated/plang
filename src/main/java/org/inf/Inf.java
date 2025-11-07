package org.inf;

import lombok.extern.slf4j.Slf4j;
import org.inf.ast.Ast.Program;
import org.inf.ast.TokenToAstRaising;
import org.inf.hir.AstToHirRaising;
import org.inf.hir.Hir;
import org.inf.lexer.InfLexer;
import org.inf.lexer.InfLexerSteps;
import org.inf.llvm.lowering.MirToLLVMLowering;
import org.inf.mir.MirLoweringResult;
import org.inf.mir.ThirToMirLowering;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.thir.raising.ThirRaiseResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
public class Inf {

  public record Result<T>(T resultValue, String output, String error) {

  }

  public static Program codeToAst(String code) {

    final var pass2 = new InfLexerSteps();
    try (final var tokens = new InfLexer(stringToStream(code))) {
      final var transformed = pass2.transform(tokens);
      final var parser = new TokenToAstRaising(transformed);

      return parser.parse();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Hir.Expression codeToHir(String code) {
    final var ast = Inf.codeToAst(code);
    return AstToHirRaising.lower_program(ast, InfCompileOptions.builder().build().machineTarget());
  }

  public static ThirRaiseResult codeToThir(String code) {
    final var hir = Inf.codeToHir(code);
    final var options = InfCompileOptions.builder().build();
    return new HirToThirRaising(options.machineTarget()).raise(hir);
  }

  public static MirLoweringResult codeToMir(String code) {

    final var options = InfCompileOptions.builder().build();
    final var ast = Inf.codeToAst(code);
    final var hir = AstToHirRaising.lower_program(ast, options.machineTarget());
    final var thir = new HirToThirRaising(options.machineTarget()).raise(hir);
    return ThirToMirLowering.lower(thir, options.machineTarget());
  }

  public static <T> Result<T> codeToResult(String code) {
    return codeToResult(code, InfRunOptions.builder().build());
  }

  public static <T> Result<T> codeToResult(String code, InfRunOptions options) {

    final var ast = Inf.codeToAst(code);
    final var hir = AstToHirRaising.lower_program(ast, options.machineTarget());
    return hirToResult(hir, options);
  }

  public static <T> Result<T> hirToResult(Hir.Expression hir, InfRunOptions options) {

    final var thir = new HirToThirRaising(options.machineTarget()).raise(hir);
    final var mir = ThirToMirLowering.lower(thir, options.machineTarget());
    final var llvmLowering = new MirToLLVMLowering();
    return llvmLowering.lower_script(mir, "script", options);
  }

  private static InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }
}
