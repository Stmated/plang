package com.github.stmated.plang;

import com.github.stmated.plang.ast.model.AstProgram;
import com.github.stmated.plang.hir.raising.AstToHirRaising;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.lexer.PlangLexer;
import com.github.stmated.plang.lexer.PlangLexerSteps;
import com.github.stmated.plang.llvm.lowering.MirToLLVMLowering;
import com.github.stmated.plang.mir.ThirToMirLowering;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.parser.PlangAstParser;
import com.github.stmated.plang.thir.raising.HirToThirRaising;
import com.github.stmated.plang.thir.raising.ThirRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Plang {

  public record Result(int returnCode, String output, String error) {}

  public static AstProgram codeToAst(String code) {

    final var pass2 = new PlangLexerSteps();
    try (final var tokens = new PlangLexer(stringToStream(code))) {
      final var transformed = pass2.transform(tokens);
      final var parser = new PlangAstParser(transformed);

      return parser.parse();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static HirProgram astToHir(AstProgram ast) {
    return new AstToHirRaising().lower_program(ast);
  }

  public static ThirRepository hirToThir(HirProgram hir) {
    return new HirToThirRaising().raise(hir);
  }

  public static MirNode hirToMir(HirProgram hir) {

    final var thir = new HirToThirRaising().raise(hir);
    return thirToMir(thir);
  }

  public static MirNode thirToMir(ThirRepository thir) {
    return new ThirToMirLowering(thir).lower();
  }

  public static ThirRepository codeToThir(String code) {

    final var ast = Plang.codeToAst(code);
    final var hir = Plang.astToHir(ast);
    return Plang.hirToThir(hir);
  }

  public static MirNode codeToMir(String code) {

    final var ast = Plang.codeToAst(code);
    final var hir = Plang.astToHir(ast);
    return Plang.hirToMir(hir);
  }

  public static Result hirToResult(HirProgram hir) {

    final var mir = Plang.hirToMir(hir);
    return Plang.mirToResult(mir);
  }

  public static Result mirToResult(MirNode mir) {

    final var llvmLowering = new MirToLLVMLowering();
    return llvmLowering.lower_script(mir, "script");
  }

  public static Result codeToResult(String code) {

    final var ast = Plang.codeToAst(code);
    final var hir = Plang.astToHir(ast);
    final var mir = Plang.hirToMir(hir);
    return Plang.mirToResult(mir);
  }

  private static InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }
}
