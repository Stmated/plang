package org.inf.ast.util;

import de.skuzzle.test.snapshots.Snapshot;
import de.skuzzle.test.snapshots.SnapshotDsl;
import lombok.experimental.UtilityClass;
import org.junit.jupiter.api.TestInfo;

import java.nio.file.Path;

@UtilityClass
public class SnapshotTestUtils {

  private static final String SNAPSHOT_DIR = ".snapshots";

  public static void assertMatches(final TestInfo testInfo, final Snapshot snapshot, final String testName, final String actual) {

    final SnapshotDsl.ChooseActual asserter;
    if (testName != null) {
      final var directory = Path.of(
        SNAPSHOT_DIR,
        testInfo.getTestClass().orElseThrow().getSimpleName(),
        testInfo.getTestMethod().orElseThrow().getName()
      );
      asserter = snapshot.in(directory).named(testName);
    } else {
      final var directory = Path.of(
        SNAPSHOT_DIR,
        testInfo.getTestClass().orElseThrow().getSimpleName()
      );
      asserter = snapshot.in(directory);
    }

    asserter.assertThat(actual).asText().matchesSnapshotText();
  }
}
