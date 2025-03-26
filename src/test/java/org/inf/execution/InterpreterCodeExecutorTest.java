package org.inf.execution;

import org.inf.Inf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class InterpreterCodeExecutorTest {

  @Test
  void testArithmetic() {
    final var executor = new InterpreterCodeExecutor();

    Assertions.assertEquals(3, executor.execute(Inf.codeToMir("1 + 2").initNode()));
    Assertions.assertEquals(3L, executor.execute(Inf.codeToMir("1L + 2L").initNode()));
    Assertions.assertEquals(3.5d, executor.execute(Inf.codeToMir("1d + 2.5d").initNode()));
    Assertions.assertEquals(3.5f, executor.execute(Inf.codeToMir("1f + 2.5f").initNode()));

    Assertions.assertEquals(2L, executor.execute(Inf.codeToMir("3L - 1L").initNode()));
    Assertions.assertEquals(6, executor.execute(Inf.codeToMir("3 * 2").initNode()));
    Assertions.assertEquals(5, executor.execute(Inf.codeToMir("10 / 2").initNode()));
    Assertions.assertEquals(40.0, executor.execute(Inf.codeToMir("20 / 0.5").initNode()));
  }

  @Test
  void testLossyArithmetic() {
    final var executor = new InterpreterCodeExecutor();

    Assertions.assertEquals(0, executor.execute(Inf.codeToMir("3 / 6").initNode()));
    Assertions.assertEquals(2, executor.execute(Inf.codeToMir("20 / 11").initNode()));
    Assertions.assertEquals(2, executor.execute(Inf.codeToMir("20 / 12").initNode()));
  }

  @Test
  void testArithmeticWithDifferentTypes() {
    final var executor = new InterpreterCodeExecutor();

    Assertions.assertEquals(3.5d, executor.execute(Inf.codeToMir("1d + 2.5f").initNode()));
  }

  @Test
  void testFunctionCall() {
    final var executor = new InterpreterCodeExecutor();

    Assertions.assertEquals(3, executor.execute(Inf.codeToMir("val add = (a: int, b: int) => a + b; add(1, 2)").initNode()));
  }
}
