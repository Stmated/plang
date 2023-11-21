package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.Plang;
import com.github.stmated.plang.PlangRunOptions;
import com.github.stmated.plang.exceptions.InvalidImplementationException;
import com.github.stmated.plang.exceptions.InvalidTypeConversionException;
import com.github.stmated.plang.exceptions.UnreachableCodeLLVMException;
import com.github.stmated.plang.hir.Hir.BinaryOperation;
import com.github.stmated.plang.hir.Hir.BinaryOperationKind;
import com.github.stmated.plang.hir.Hir.Expression;
import com.github.stmated.plang.hir.Hir.Expressions;
import com.github.stmated.plang.hir.Hir.Literal;
import com.github.stmated.plang.hir.Hir.Program;
import com.github.stmated.plang.hir.Hir.Return;
import com.github.stmated.plang.ty.Ty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MirToLLVMLoweringTest {

  @Test
  void testBinaryOperationFromHir() {

    final var program = new Program(new Expressions(new Expression[]{
      new Return(
        new BinaryOperation(
          new Literal("1", Ty.INTEGER),
          BinaryOperationKind.ADD,
          new Literal("2", Ty.INTEGER)
        )
      )
    }));

    Assertions.assertEquals(3, Plang.hirToResult(program, PlangRunOptions.builder().build()).resultValue());
  }

  @Test
  void testBinaryOperationAdd() {
    Assertions.assertEquals(3, Plang.codeToResult("return 1 + 2").resultValue());
  }

  @Test
  void testBinaryOperationSubtract() {
    Assertions.assertEquals(1, Plang.codeToResult("return 3 - 2").resultValue());
  }

  @Test
  void testBinaryOperationMultiply() {
    Assertions.assertEquals(4, Plang.codeToResult("return 2 * 2").resultValue());
  }

  @Test
  void testBinaryOperationDivideInteger() {
    Assertions.assertEquals(1, Plang.codeToResult("return 2 / 2").resultValue());
  }

  @Test
  @Disabled
  void testPrint() {
    final var result = Plang.codeToResult("freopen('/tmp/out', 'w', stdout); printf('%d', 1337); return 1;");
    Assertions.assertEquals(1, result.resultValue());
    Assertions.assertEquals("1337", result.output());
  }

  @Test
  void testConditionalWithBlocks() {
    Assertions.assertEquals(1, Plang.codeToResult("if (1 == 1) { return 1; } else { return 2; }").resultValue());
  }

  @Test
  void testPassingConditionalWithBranchMerge() {
    Assertions.assertEquals(1, Plang.codeToResult("if (1 == 1) { return 1; } return 2;").resultValue());
  }

  @Test
  void testFailingConditionalWithBranchMerge() {
    Assertions.assertEquals(2, Plang.codeToResult("if (1 == 2) { return 1; } return 2;").resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "return if (1 == 1) then 1 else 2",
    "return if (1 == 1) 1 else 2"
  })
  void testPassingConditionalWithInlineExpression(String code) {
    Assertions.assertEquals(1, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "return if (1 == 2) then 1 else 2",
    "return if (1 == 2) 1 else 2"
  })
  void testFailingConditionalWithInlineExpression(String code) {
    Assertions.assertEquals(2, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "if (1 == 2) { return 1 } 2",
    "if (1 == 2) 1 2"
  })
  void testImplicitReturn(String code) {
    Assertions.assertEquals(2, Plang.codeToResult(code).resultValue());
  }

  @Test
  void testCompactImplicitReturn() {
    Assertions.assertEquals(
      2, Plang.codeToResult("if (1 == 1) 1 2").resultValue(),
      "2, since 1 is discarded and 2 turned into implicit return"
    );
  }

  @Test
  void testCompactReturnWithUnreachableCodeAfter() {
    Assertions.assertThrows(UnreachableCodeLLVMException.class, () -> Plang.codeToResult("return if (1 == 1) 1 2"));
  }

  @Test
  void testReturnVariable() {
    Assertions.assertEquals(10, Plang.codeToResult("val a = 10; return a;").resultValue());
    Assertions.assertEquals(11, Plang.codeToResult("val a = 10; return a + 1;").resultValue());
  }

  @Test
  void testBinaryOperationDivideTwoIntegersWithLoss() {

    final var result = Plang.codeToResult("val v = 1 / 2; return v");
    Assertions.assertEquals(0, result.resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 / 2.0",
    "1.0 / 2",
  })
  void testBinaryOperationDivideFloats(String code) {

    final var result = Plang.<Double>codeToResult(code);
    Assertions.assertEquals(0.5d, result.resultValue(), 0.001d);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = 8; val b = 2; val c = a + b; return c;",
  })
  void testSimpleAssignment(String code) {

    // TODO: Something is wrong with this simple code -- fix this and other things might/should/could get better as well :)

    Assertions.assertEquals(10, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = 10; val b = a + 1; return b + 1;",
    "val a = 10; val b = a + 1; b + 1;",
    "val a = 10 val b = a + 1 b + 1"
  })
  void testChainedVariableAssignments(String code) {
    Assertions.assertEquals(12, Plang.codeToResult(code).resultValue());
  }

  @Test
  void testLoop() {
    final var code = "var a = 0; for (var i = 0; i < 10; i += 1) { a += i; } return a;";
    Assertions.assertEquals(45, Plang.codeToResult(code).resultValue());
  }

  @Test
  void testAssign() {
    final var code = "var a = 0; var b = 1; var c = a + b + 1; return c;";
    Assertions.assertEquals(2, Plang.codeToResult(code).resultValue());
  }

  @Test
  void testMove() {
    final var code = "var a = 1; var b = 2; var temp = a; a = b; b = temp; return b;";
    Assertions.assertEquals(1, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "var a = 0; if (a == 0) then a = 10 else a = 5; return a;",
    "val a = 10; a",
  })
  void testScopes(String code) {
    Assertions.assertEquals(10, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "var a = 10; a += 2; a -= 1; a"
  })
  void testScopes2(String code) {
    Assertions.assertEquals(11, Plang.codeToResult(code).resultValue());
  }

  @Test
  void testCreateArray() {
    final var code = "val a = [0, 1]; return 1;";
    Assertions.assertEquals(1, Plang.codeToResult(code).resultValue());
  }

  @Test
  void testCreateAndAccessArray() {
    final var code = "val a = [10, 20]; return a[0];";
    Assertions.assertEquals(10, Plang.codeToResult(code).resultValue());
  }

  @Test
  void testCreateAndAccessArrayWithInitializer() {
    final var code = "val a = [666; 500]; return a[499];";
    Assertions.assertEquals(666, Plang.codeToResult(code).resultValue());
  }

  @Test
  void testCreateAndAccessArrayWithAlternatingInitializer() {
    final var code = "val a = [1, 2, 3, 4; 500]; return a[0] + a[3] + a[5];";
    Assertions.assertEquals(1 + 4 + 2, Plang.codeToResult(code).resultValue());
  }

  @Test
  void testCreateAndAccessAndUseArray() {
    final var code = "val a = [10, 20]; return a[0] + a[1];";
    Assertions.assertEquals(30, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = [1, 2, 3, 4, 5;uint;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;",
    "val a = [1, 2, 3, 4, 5;uint32;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;",
    "val a = [1, 2, 3, 4, 5;uint64;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;",
    "val a = [1, 2, 3, 4, 5;uint8;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;"
  })
  void testCreateAndIterateArray(String code) {
    Assertions.assertNotEquals(0, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = 10i32; val b = 20u8; a + b",
    "val a = 10u32; val b = 20i8; a + b",
    "val a = 10i32; val b = 20i8; a + b",
    "val a = 10u32; val b = 20u8; a + b",
    "val a = 10i8; val b = 20u32; a + b",
    "val a = 10u8; val b = 20i32; a + b",
    "val a = 10i8; val b = 20i32; a + b",
    "val a = 10u8; val b = 20u32; a + b",
  })
  void testAddIntegersOfDifferentWidths(String code) {
    Assertions.assertEquals(30, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = [1i8, 2i8, 3i8, 4i8, 5i8;int8;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;"
  })
  void testCreateAndIterateExplicitUint8Array(String code) {
    Assertions.assertNotEquals(0, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = [1i32, 2i32, 3i32, 4i32, 5i32;uint8;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;"
  })
  void when_create_array_of_uint8_but_give_int_values_expect_exception(String code) {
    final var ex = Assertions.assertThrows(InvalidTypeConversionException.class, () -> Plang.codeToResult(code).resultValue());
    Assertions.assertEquals(Ty.INTEGER, ex.given());
    Assertions.assertEquals(Ty.CHAR, ex.expected());
  }

  @RepeatedTest(10)
  void when_creating_array_without_initialization_expect_garbage() {

    // Slight chance it actually might be zero, but very, very, very low
    final var code = "val a = [;int;100]; var t = 0; for (var i = 0; i < 100; i += 1) t += a[i]; return t;";
    Assertions.assertNotEquals(0, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    // TODO: All these should be "const", or at least have the same tests again but with const and other order of instructions
    "val fn = () => 10; fn()",
    "val fn = (): int => 10; fn()",
    "val fn = (): uint => 10; fn()",
    "val fn = (): sint => 10; fn()",
    "val fn = (): int32 => 10; fn()",
    "val fn = (): int64 => 10; fn()",
    "val fn = (): int64 => 10L; fn()",
    "val fn = (): uint128 => 10; fn()",
    "val fn = () => 10; return fn()",
    "val fn = (a: int, b: int) => a + b; fn(5, 5)",
    "val fn = (a: int, b: int) => a + b; fn(10, 0)",
    "val fn = (a: int, b: int) => a + b; fn(0, 10)",
    "((a: int, b: int) => a + b)(5, 5)",
    "val fn = (a: int, b: int) => a + b; fn(2, 3) + fn(3, 2)",
    "val fn1 = (a: int, b: int) => a + b; val fn2 = (a: int, b: int) => a - b; val fn3 = fn2; return fn1(10, 10) + fn3(30, 40);"
  })
  void testFnCall(String code) {
    Assertions.assertEquals(10, Plang.codeToResult(code).resultValue());
  }

  // TODO: Make TyArrayConst that is a const array with constant values. Should it just take the HirArray?
  //        Also need to match the situation below where the type is an array of a certain type -- how is that syntax? [int;] ? Also support [int; 0..5] ?

  @ParameterizedTest
  @ValueSource(strings = {
    "val fn = (a: int, ...x: [int]) => a + x[0] + x[1]; fn(1, 5, 4)",
  })
  void testFnImplVararg(String code) {
    Assertions.assertThrows(InvalidImplementationException.class, () -> Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val fn = (a: int, x: [;int;]) => a + x[0] + x[1]; fn(1, [5, 4])",
  })
  void testFnWithArray(String code) {
    Assertions.assertEquals(10, Plang.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void testStruct(int optLevel) {

    final var code = """
      val S = struct {
        val a: int;
        val b: int;
      };
            
      val v = new heap S { a = 4, b = 6 };
      return v.a + v.b;
      """;

    final var options = PlangRunOptions.builder().optLevel(optLevel).build();
    Assertions.assertEquals(10, Plang.codeToResult(code, options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void testSetToStructField(int optLevel) {

    final var code = """
      val S = struct {
        val a: int;
        val b: int;
      };
      
      val v = new heap S { a = 4, b = 6 };
      v.a = 20;
      v.b = 10;
      return v.a + v.b;
      """;

    final var options = PlangRunOptions.builder().optLevel(optLevel).build();
    Assertions.assertEquals(30, Plang.codeToResult(code, options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "0=0",
    "1=10",
    "2=20",
    "3=30"
  })
  void testSetToStructField(String argAndExpected) {

    final var parts = argAndExpected.split("=");
    final var arg = Integer.parseInt(parts[0]);
    final var expected = Integer.parseInt(parts[1]);

    final var code = """
      (multiplier: int) => {
        val S = struct {
          val a: int;
          val b: int;
        };
              
        val v = new heap S { a = 4, b = 6 };
        return (v.a + v.b) * multiplier;
      };
      """;

    final var options = PlangRunOptions.builder().arguments(new Object[] {arg}).build();
    Assertions.assertEquals(expected, Plang.codeToResult(code, options).resultValue());
  }
}
