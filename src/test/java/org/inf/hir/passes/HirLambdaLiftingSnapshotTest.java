package org.inf.hir.passes;

import de.skuzzle.test.snapshots.Snapshot;
import de.skuzzle.test.snapshots.junit5.EnableSnapshotTests;
import org.inf.Inf;
import org.inf.InfCompileOptions;
import org.inf.ast.util.SnapshotTestUtils;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Random;

@EnableSnapshotTests
@Execution(ExecutionMode.SAME_THREAD)
public class HirLambdaLiftingSnapshotTest {

  @RepeatedTest(5)
  void callableParameterIsCapturedByIdentity(final TestInfo testInfo, final Snapshot snapshot) {

    final var opt = InfCompileOptions.builder()
      .random(new Random(0))
      .build();

    final var root = Inf.codeToThir("""
      (fn: (): int) => {
        return (() => {
          return fn();
        })();
      }
      """, opt).root();

    final var content = new ToStringTreeHirVisitor().render(root);

    SnapshotTestUtils.assertMatches(testInfo, snapshot, null, content);
  }
}
