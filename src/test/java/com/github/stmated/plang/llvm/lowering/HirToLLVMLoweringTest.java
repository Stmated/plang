package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.Plang;
import com.github.stmated.plang.exceptions.UnreachableCodeLLVMException;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirBinaryOperationKind;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HirToLLVMLoweringTest {

  @Test
  void testBinaryOperationFromHir() {

    final var program = new HirProgram(new HirExpression[]{
      new HirReturn(
        new HirBinaryOperation(new HirLiteral(1), HirBinaryOperationKind.ADD, new HirLiteral(2))
      )
    });

    Assertions.assertEquals(3, Plang.hirToResult(program).returnCode());
  }

  @Test
  void testBinaryOperation() {
    Assertions.assertEquals(3, Plang.codeToResult("return 1 + 2").returnCode());
    Assertions.assertEquals(1, Plang.codeToResult("return 3 - 2").returnCode());
    Assertions.assertEquals(4, Plang.codeToResult("return 2 * 2").returnCode());
  }

  @Test
  void testPrint() {
    final var result = Plang.codeToResult("printf('%d', 1337); return 1;");
    Assertions.assertEquals(1, result.returnCode());
    Assertions.assertEquals("1337", result.output());
  }

  @Test
  void testConditionalWithBlocks() {
    Assertions.assertEquals(1, Plang.codeToResult("if (1 == 1) { return 1; } else { return 2; }").returnCode());
  }

  @Test
  void testPassingConditionalWithBranchMerge() {
    Assertions.assertEquals(1, Plang.codeToResult("if (1 == 1) { return 1; } return 2;").returnCode());
  }

  @Test
  void testFailingConditionalWithBranchMerge() {
    Assertions.assertEquals(2, Plang.codeToResult("if (1 == 2) { return 1; } return 2;").returnCode());
  }

  @Test
  void testPassingConditionalWithInlineExpression() {
    Assertions.assertEquals(1, Plang.codeToResult("return if (1 == 1) then 1 else 2").returnCode());
    Assertions.assertEquals(1, Plang.codeToResult("return if (1 == 1) 1 else 2").returnCode());
  }

  @Test
  void testFailingConditionalWithInlineExpression() {
    Assertions.assertEquals(2, Plang.codeToResult("return if (1 == 2) then 1 else 2").returnCode());
    Assertions.assertEquals(2, Plang.codeToResult("return if (1 == 2) 1 else 2").returnCode());
  }

  @Test
  void testImplictReturn() {
    Assertions.assertEquals(2, Plang.codeToResult("if (1 == 2) { return 1 } 2").returnCode());
    Assertions.assertEquals(2, Plang.codeToResult("if (1 == 2) 1 2").returnCode());
  }

  @Test
  void testCompactImplicitReturn() {
    Assertions.assertEquals(
      2, Plang.codeToResult("if (1 == 1) 1 2").returnCode(),
      "2, since 1 is discarded and 2 turned into implicit return"
    );
  }

  @Test
  void testCompactImplicitReturnConditionalResult() {
    Assertions.assertThrows(UnreachableCodeLLVMException.class, () -> Plang.codeToResult("return if (1 == 1) 1 2"));
  }

//  TODO: If function last expression is not terminal, then add an implicit return -- do this in AST -> HIR layer
}
