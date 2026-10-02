package org.inf.ast.raising;

import de.skuzzle.test.snapshots.Snapshot;
import de.skuzzle.test.snapshots.junit5.EnableSnapshotTests;
import lombok.SneakyThrows;
import org.inf.Inf;
import org.inf.ast.util.SnapshotTestUtils;
import org.inf.ast.util.ToStringTreeAstVisitor;
import org.inf.parser.InfTestUtil;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnableSnapshotTests
@Execution(ExecutionMode.SAME_THREAD)
public class TokenToAstSnapshotTest {

  @ParameterizedTest
  @CsvSource(value = {
    "assignment | val r = f x + y, z;",
    "nested_calls | outer(inner x, y)",
    "parenthesis_boundary | outer((inner x), y)",
    "conditional_boundary | if (true) f x;",
    "bounded_conditional | if (p x) then (f y) else { g z }",
    "while_boundary | while (p) f x;",
    "for_boundary | for (var i = 0; i < 3; i += 1) f x;",
    "comments | f /* comment */ x /* comment */ + y, z;"
  }, delimiter = '|')
  void given__source__when__parsed__then__raw_structure_preserves_separators(
    final String name, final String code, final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var ast = Inf.codeToRawAst(code);
    SnapshotTestUtils.assertMatches(testInfo, snapshot, name, new ToStringTreeAstVisitor().visit(ast));
  }

  @Test
  void given__arithmetic_argument__when__parsed__then__operators_belong_to_argument(final TestInfo testInfo, final Snapshot snapshot) {
    final var ast = Inf.codeToAst("f x + y, z");
    SnapshotTestUtils.assertMatches(testInfo, snapshot, null, new ToStringTreeAstVisitor().visit(ast));
  }

  public static Stream<Arguments> allValidTestFiles() throws IOException {
    return InfTestUtil.testShouldSucceedSource();
  }

  /// Does not test real validity, just that it does not crash.
  /// So we run through all the code example files which we expect to be valid.
  @ParameterizedTest
  @MethodSource("allValidTestFiles")
  @SneakyThrows
  void testAllFiles(final Path path, final TestInfo testInfo, final Snapshot snapshot) {

    final var program = Inf.codeToRawAst(Files.readString(path));
    assertNotNull(program);

    final var programToString = new ToStringTreeAstVisitor().visit(program);
    SnapshotTestUtils.assertMatches(testInfo, snapshot, path.getFileName().toString(), programToString);
  }
}
