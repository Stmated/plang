---
name: java-test-writer
description: Use when writing Java test cases.
---

# Test writer

Name test methods in `given-when-then` format, e.g. `given__X__when__Y__then__Z`.
If there are words in `X`, `Y` or `Z`, separate them with underscores, e.g. `given__a_b_c__when__d_e_f__then__g_h_i`.

Prefer parameterized tests when testing multiple inputs and outputs for the same method.

Strongly prefer creating test classes that have the same name as the class being tested, with `Test` appended to the end, e.g. `MyClassTest` for `MyClass`.
If there is a need to create a test class that is not limited to the testing of one unit/class, that is likely indicative of a design problem.

Prefer the use of `assertAll` if there are several assertions one after another that do not have dependencies on each other.

## Snapshot testing
Prefer using snapshot testing for complex outputs, to assert toString-able structures.

```java
import org.inf.ast.util.SnapshotTestUtils;

@EnableSnapshotTests
@Execution(ExecutionMode.SAME_THREAD)
public class SnapshotTest {

  @Test
  void given__nothing__when__nothing__then__assert_snapshot(final TestInfo testInfo, final Snapshot snapshot) {

    final String snapshotSubName = null; // name, based on some input if using @ParameterizedTest, can be null.
    final var structureToString = "..."; // some large structure

    SnapshotTestUtils.assertMatches(testInfo, snapshot, snapshotSubName, structureToString);
  }
}

```
