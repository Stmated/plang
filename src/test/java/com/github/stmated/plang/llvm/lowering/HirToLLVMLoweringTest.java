package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.Plang;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirBinaryOperationKind;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class HirToLLVMLoweringTest {

  @Test
  @SneakyThrows
  void testBinaryOperationFromHir() {

    final var program = new HirProgram(new HirExpression[] {
      new HirReturn(
        new HirBinaryOperation(new HirLiteral(1), HirBinaryOperationKind.ADD, new HirLiteral(2))
      )
    });

    Assertions.assertEquals(3, Plang.hirToResult(program).returnCode());
  }

  @Test
  @SneakyThrows
  void testBinaryOperation() {
    Assertions.assertEquals(3, Plang.codeToResult("return 1 + 2").returnCode());
    Assertions.assertEquals(1, Plang.codeToResult("return 3 - 2").returnCode());
    Assertions.assertEquals(4, Plang.codeToResult("return 2 * 2").returnCode());
  }

  @RepeatedTest(100)
  @SneakyThrows
  void testPrint() {
    final var result = Plang.codeToResult("printf('%d', 1337); return 1;");
    Assertions.assertEquals(1, result.returnCode());
    Assertions.assertEquals("1337", result.output());
  }
}
