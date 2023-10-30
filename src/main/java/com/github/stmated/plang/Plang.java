package com.github.stmated.plang;

import com.github.stmated.plang.ast.model.AstProgram;
import com.github.stmated.plang.hir.raising.AstToHirRaising;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.lexer.PlangLexer;
import com.github.stmated.plang.lexer.PlangLexerSteps;
import com.github.stmated.plang.llvm.lowering.HirToLLVMLowering;
import com.github.stmated.plang.mir.HirToMirLowering;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.parser.PlangAstParser;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
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

  public static MirNode hirToMir(HirProgram hir) {
    return new HirToMirLowering().lower_program(hir);
  }

  public static Path hirToPath(HirProgram hir) {

    final Path randomPath;

    try {

      randomPath = Files
        .createTempDirectory(STR."plang-\{UUID.randomUUID()}")
        .resolve(UUID.randomUUID().toString());

    } catch (IOException ex) {

      throw new RuntimeException("Could not create temp directory", ex);
    }

    try {
      return new HirToLLVMLowering().lower_program(hir, randomPath);
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static Result pathToResult(Path path, boolean deleteDirectory) {

    try {

      final var p = Runtime.getRuntime().exec(new String[]{path.toAbsolutePath().toString()});

      final var sbError = new StringBuilder();
      final var sbOutput = new StringBuilder();

      try (var error = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
        String line;
        while ((line = error.readLine()) != null) {
          sbError.append(line);
        }
      }

      try (var error = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        String line;
        while ((line = error.readLine()) != null) {
          sbOutput.append(line);
        }
      }

      final var returnCode = p.waitFor();
      return new Result(returnCode, sbOutput.toString(), sbError.toString());

    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    } finally {

      if (deleteDirectory) {

        try {

          log.debug("Deleting {}", path.getParent());
          Files.walk(path.getParent())
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
              try {
                Files.delete(p);
              } catch (IOException ex) {
                log.error(STR."Could not delete '\{p}'", ex);
              }
            });
        } catch (IOException ex) {
          log.error(STR."Could not delete the temporary files inside '\{path.getParent()}'", ex);
        }
      }
    }
  }

  public static Path codeToPath(String code) {

    final var ast = Plang.codeToAst(code);
    final var hir = Plang.astToHir(ast);
    return Plang.hirToPath(hir);
  }

  public static MirNode codeToMir(String code) {

    final var ast = Plang.codeToAst(code);
    final var hir = Plang.astToHir(ast);
    return Plang.hirToMir(hir);
  }

  public static Result hirToResult(HirProgram hir) {

    final var path = Plang.hirToPath(hir);
    return Plang.pathToResult(path, true);
  }

  public static Result codeToResult(String code) {

    final var path = Plang.codeToPath(code);
    return Plang.pathToResult(path, true);
  }

  private static InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }
}
