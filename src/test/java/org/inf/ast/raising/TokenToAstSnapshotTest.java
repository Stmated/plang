package org.inf.ast.raising;

import de.skuzzle.test.snapshots.Snapshot;
import de.skuzzle.test.snapshots.junit5.EnableSnapshotTests;
import lombok.SneakyThrows;
import org.inf.ast.TokenToAstRaising;
import org.inf.ast.util.SnapshotTestUtils;
import org.inf.ast.util.ToStringTreeAstVisitor;
import org.inf.lexer.InfLexer;
import org.inf.lexer.InfLexerSteps;
import org.inf.parser.InfTestUtil;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnableSnapshotTests
@Execution(ExecutionMode.SAME_THREAD)
public class TokenToAstSnapshotTest {

  public static Stream<Arguments> allValidTestFiles() throws IOException {
    return InfTestUtil.testShouldSucceedSource();
  }

  /// Does not test real validity, just that it does not crash.
  /// So we run through all the code example files which we expect to be valid.
  @ParameterizedTest
  @MethodSource("allValidTestFiles")
  @SneakyThrows
  void testAllFiles(final Path path, final TestInfo testInfo, final Snapshot snapshot) {

    final var steps = new InfLexerSteps();

    try (final var tokens = new InfLexer(Files.newInputStream(path))) {
      final var transformed = steps.transform(tokens);
      final var parser = new TokenToAstRaising(transformed);
      final var program = parser.parse();
      assertNotNull(program);

      final var programToString = new ToStringTreeAstVisitor().visit(program);
      SnapshotTestUtils.assertMatches(testInfo, snapshot, path.getFileName().toString(), programToString);
    }
  }
}
