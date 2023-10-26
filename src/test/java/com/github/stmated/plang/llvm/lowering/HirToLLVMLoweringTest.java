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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
  void testBinaryOperationAdd() {
    Assertions.assertEquals(3, Plang.codeToResult("return 1 + 2").returnCode());
  }

  @Test
  void testBinaryOperationSubtract() {
    Assertions.assertEquals(1, Plang.codeToResult("return 3 - 2").returnCode());
  }

  @Test
  void testBinaryOperationMultiply() {
    Assertions.assertEquals(4, Plang.codeToResult("return 2 * 2").returnCode());
  }

  @Test
  void testBinaryOperationDivideInteger() {
    Assertions.assertEquals(1, Plang.codeToResult("return 2 / 2").returnCode());
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

  @ParameterizedTest
  @ValueSource(strings = {
    "return if (1 == 1) then 1 else 2",
    "return if (1 == 1) 1 else 2"
  })
  void testPassingConditionalWithInlineExpression(String code) {
    Assertions.assertEquals(1, Plang.codeToResult(code).returnCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "return if (1 == 2) then 1 else 2",
    "return if (1 == 2) 1 else 2"
  })
  void testFailingConditionalWithInlineExpression(String code) {
    Assertions.assertEquals(2, Plang.codeToResult(code).returnCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "if (1 == 2) { return 1 } 2",
    "if (1 == 2) 1 2"
  })
  void testImplicitReturn(String code) {
    Assertions.assertEquals(2, Plang.codeToResult(code).returnCode());
  }

  @Test
  void testCompactImplicitReturn() {
    Assertions.assertEquals(
      2, Plang.codeToResult("if (1 == 1) 1 2").returnCode(),
      "2, since 1 is discarded and 2 turned into implicit return"
    );
  }

  @Test
  void testCompactReturnWithUnreachableCodeAfter() {
    Assertions.assertThrows(UnreachableCodeLLVMException.class, () -> Plang.codeToResult("return if (1 == 1) 1 2"));
  }

  @Test
  void testReturnVariable() {
    Assertions.assertEquals(10, Plang.codeToResult("val a = 10; return a;").returnCode());
    Assertions.assertEquals(11, Plang.codeToResult("val a = 10; return a + 1;").returnCode());
  }

  @Test
  void testBinaryOperationDivideTwoIntegersWithLoss() {

    final var result = Plang.codeToResult("val v = 1 / 2; printf('%.2f', v); return 0");
    Assertions.assertEquals(0, result.returnCode());
    Assertions.assertEquals("0.00", result.output());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val v = 1 / 2.0; printf('%.2f', v); return 0",
    "val v = 1.0 / 2; printf('%.2f', v); 0",
  })
  void testBinaryOperationDivideFloats(String code) {

    final var result = Plang.codeToResult(code);
    Assertions.assertEquals(0, result.returnCode());
    Assertions.assertEquals("0.50", result.output());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = 10; val b = a + 1; return b + 1;",
    "val a = 10; val b = a + 1; b + 1;",
    "val a = 10 val b = a + 1 b + 1"
  })
  void testChainedVariableAssignments(String code) {
    Assertions.assertEquals(12, Plang.codeToResult(code).returnCode());
  }

  @Test
  void testLoop() {
    final var code = "var a = 0; for (var i = 0; i < 10; i += 1) { a += i; } return a;";
    Assertions.assertEquals(45, Plang.codeToResult(code).returnCode());
  }

  @Test
  void testMove() {
    final var code = "var a = 1; var b = 2; var temp = a; a = b; b = temp; return b;";
    Assertions.assertEquals(1, Plang.codeToResult(code).returnCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = 0; return if (a == 0) then a = 10 else a = 5",
    "val a = 10",
    "val a = 10; a += 1; a -= 1"
  })
  void testScopes(String code) {
    Assertions.assertEquals(10, Plang.codeToResult(code).returnCode());
  }
}
