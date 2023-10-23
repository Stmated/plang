package com.github.stmated.plang;

import com.github.stmated.plang.ast.model.AstProgram;
import com.github.stmated.plang.hir.lowering.AstToHirLowering;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.lexer.PlangLexer;
import com.github.stmated.plang.lexer.PlangLexerSteps;
import com.github.stmated.plang.llvm.lowering.HirToLLVMLowering;
import com.github.stmated.plang.parser.PlangAstParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Plang {

  public static record Result(int returnCode, String output, String error) {}

  public static AstProgram codeToAst(String code) throws Exception {

    final var pass2 = new PlangLexerSteps();
    try (final var tokens = new PlangLexer(stringToStream(code))) {
      final var transformed = pass2.transform(tokens);
      final var parser = new PlangAstParser(transformed);

      return parser.parse();
    }
  }

  public static HirProgram astToHir(AstProgram ast) {
    return AstToHirLowering.lower_program(ast);
  }

  public static Path hirToPath(HirProgram hir) throws IOException, InterruptedException {

    final var randomPath = Files
      .createTempDirectory(STR."plang-\{UUID.randomUUID()}")
      .resolve(UUID.randomUUID().toString());

    return HirToLLVMLowering.lower_program(hir, randomPath);
  }

  public static Result pathToResult(Path path, boolean deleteDirectory) throws IOException, InterruptedException {

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

    } finally {

      if (deleteDirectory) {

        Files.walk(path.getParent())
          .sorted(Comparator.reverseOrder())
          .forEach(p -> {
            try {
              log.debug("Deleting {}", p);
              Files.delete(p);
            } catch (IOException ex) {
              log.error(STR."Could not delete '\{p}'", ex);
            }
          });
      }
    }
  }

  public static Path codeToPath(String code) throws Exception {

    final var ast = Plang.codeToAst(code);
    final var hir = Plang.astToHir(ast);
    return Plang.hirToPath(hir);
  }

  public static Result hirToResult(HirProgram hir) throws IOException, InterruptedException {

    final var path = Plang.hirToPath(hir);
    return Plang.pathToResult(path, true);
  }

  public static Result codeToResult(String code) throws Exception {

    final var path = Plang.codeToPath(code);
    return Plang.pathToResult(path, true);
  }

  private static InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }
}
