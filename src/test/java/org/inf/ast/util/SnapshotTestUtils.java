package org.inf.ast.util;

import de.skuzzle.test.snapshots.Snapshot;
import lombok.experimental.UtilityClass;
import org.junit.jupiter.api.TestInfo;

import java.nio.file.Path;

@UtilityClass
public class SnapshotTestUtils {

  public static void assertMatches(final TestInfo testInfo, final Snapshot snapshot, final String testName, final String actual) {
    final var directory = Path.of(
      ".snapshots",
      testInfo.getTestClass().orElseThrow().getSimpleName(),
      testInfo.getTestMethod().orElseThrow().getName()
    );

    snapshot.in(directory).named(testName).assertThat(actual).asText().matchesSnapshotText();
  }
}
