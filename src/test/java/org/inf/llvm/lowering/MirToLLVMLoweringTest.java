package org.inf.llvm.lowering;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.inf.Inf;
import org.inf.InfRunOptions;
import org.inf.exceptions.InvalidImplementationException;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.exceptions.UnreachableCodeException;
import org.inf.hir.Hir;
import org.inf.hir.Hir.*;
import org.inf.ty.Ty;
import org.inf.ty.TyValueNumberInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

@Slf4j
class MirToLLVMLoweringTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val Fn = (v: int): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v * 2; }; s.fn(5) | 10",
    "val Fn = (v: int): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = (x) => x * 2; }; s.fn(v = 5) | 10",
    "val pair = (a: int, b: int) => a * 10 + b; val service = (call = pair,); service.call b = 2, a = 1 | 12",
    "val pair = (a: int, b: int) => a * 10 + b; val service = (make = () => (call = pair,),); service.make().call(1, 2) | 12",
    "val read = (t: (int, int)) => t[0] * 10 + t[1]; val service = (call = read,); service.call((1, 2)) | 12",
    "val Fn = (v: int): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v * 2; }; s.fn({ s.fn = (v) => v * 3; 5; }) | 10"
  })
  void given__function_valued_field__when__executed_by_llvm__then__binding_and_inference_are_preserved(
    final String code, final int expected
  ) {
    Assertions.assertEquals(expected, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void given__ordinary_field_call_effects__when__optimized__then__target_and_arguments_keep_source_order(final int optLevel) {
    final var code = """
      val trace = [0];
      val target = () => {
        trace[0] = trace[0] * 10 + 1;
        (call = (a: int, b: int) => a * 10 + b,);
      };
      val result = target().call(
        b = { trace[0] = trace[0] * 10 + 2; 2; },
        a = { trace[0] = trace[0] * 10 + 3; 1; });
      trace[0] * 100 + result
      """;
    Assertions.assertEquals(12312,
      Inf.codeToResult(code, InfRunOptions.builder().optLevel(optLevel).build()).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "pair(...(1, 2))",
    "val args = (b = 2, a = 1); pair ...args",
    "val make = () => (1, 2); pair(...make())",
    "val service = (call = pair,); service.call ...(1, 2)",
    "val trace = [0]; val make = () => { trace[0] = trace[0] * 10 + 1; (1, 2); }; val result = pair(...make()); trace[0] + result - 1",
    "pair(...(a = 3, b = 2, ignored = true), a = 1)",
    "val S = struct { val a: int; }; val args = new heap S { a = 1; }; pair(...args, b = { args.a = 9; 2; })",
    "val captured = 1; val f = (a: int, b: int) => a * 10 + b + captured; f(...(1, 1))"
  })
  void given__spread_call__when__executed_by_llvm__then__binding_and_capture_are_preserved(final String expression) {
    Assertions.assertEquals(12, Inf.codeToResult(
      "val pair = (a: int, b: int) => a * 10 + b; %s".formatted(expression)).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void given__spread_effects_and_overrides__when__optimized__then__evaluation_order_and_field_capture_are_preserved(final int optLevel) {
    final var code = """
      val S = struct { val a: int; };
      val args = new heap S { a = 1; };
      val trace = [0];
      val make = () => { trace[0] = trace[0] * 10 + 1; args; };
      val pair = (a: int, b: int) => a * 10 + b;
      val result = pair(...make(), b = { trace[0] = trace[0] * 10 + 2; args.a = 9; 2; });
      val overridden = pair(...(a = { trace[0] = trace[0] * 10 + 3; 5; }, b = 0), a = 1);
      trace[0] * 100 + result + overridden
      """;
    Assertions.assertEquals(12322,
      Inf.codeToResult(code, InfRunOptions.builder().optLevel(optLevel).build()).resultValue());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "sum 20, 22 | 42",
    "sum 44, -2 | 42",
    "sum 10 + 10, 2 * 11 | 42",
    "twice sum 10, 11 | 42",
    "twice(sum 10, 11) | 42",
    "sum((twice 10), 22) | 42",
    "sum(twice(10), 22) | 42",
    "val result = sum 20, 22; result | 42",
    "return sum 20, 22 | 42",
    "{ sum 20, 22 } | 42",
    "if (true) then (sum 20, 22) else (sum 1, 2) | 42",
    "val fn = (x: int) => sum x, 22; fn 20 | 42",
    "sum 1, 2; sum 20, 22 | 42",
    "val alias = sum; alias 20, 22 | 42"
  }, delimiter = '|')
  void given__parenthesis_free_calls__when__executed__then__expected_result(final String expression, final int expected) {
    final var code = "val sum = (a: int, b: int) => a + b; val twice = (a: int) => a * 2; " + expression;
    Assertions.assertEquals(expected, Inf.codeToResult(code).resultValue());
  }

  @Test
  void given__alternative_grouping_would_match__when__lowered__then__no_regrouping() {
    final var code = """
      val inner = (x: int) => x;
      val outer = (x: int, y: int) => x + y;
      outer(inner 20, 22)
      """;
    final var error = Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToMir(code));
    Assertions.assertEquals("Too many arguments for function", error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "sum 20,\n22",
    "sum\n20, 22",
    "sum 20 // first argument\n, 22",
    "sum 20\n+ 1, 21",
    "sum /* first argument */ 20, /* second argument */ 22"
  })
  void given__multiline_call__when__executed__then__newlines_do_not_terminate_it(final String expression) {
    Assertions.assertEquals(42, Inf.codeToResult("val sum = (a: int, b: int) => a + b; " + expression).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "sum 1",
    "sum 1, 2, 3"
  })
  void given__wrong_argument_count__when__lowered__then__error(final String expression) {
    Assertions.assertThrows(IllegalArgumentException.class,
      () -> Inf.codeToMir("val sum = (a: int, b: int) => a + b; " + expression));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "if (positive 1) then (sum 20, 22) else { sum 0, 1 } | 42",
    "if (positive 0) then (sum 20, 22) else { sum 0, 1 } | 1",
    "if (true) 1 2; | 2",
    "if (true) sum; 42 | 42",
    "var total = 0; for (var i = 0; i < 3; i += 1) { total = sum total, i; } total | 3",
    "val value = ((x: int) => sum x, 22)(20); value | 42"
  }, delimiter = '|')
  void given__bounded_implicit_calls__when__executed__then__scope_is_preserved(final String expression, final int expected) {
    final var code = "val sum = (a: int, b: int) => a + b; val positive = (x: int) => x > 0; " + expression;
    Assertions.assertEquals(expected, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testBinaryOperationFromHir() {

    final var program = new Program(new Hir.Expressions(new Expression[]{
      new Return(
        new BinaryOperation(
          new Literal("1", Ty.INTEGER),
          BinaryOperationKind.ADD,
          new Literal("2", Ty.INTEGER),
          null
        )
      )
    }));

    Assertions.assertEquals(3, Inf.hirToResult(program, InfRunOptions.builder().build()).resultValue());
  }

  @Test
  void testBinaryOperationAdd() {
    Assertions.assertEquals(3, Inf.codeToResult("return 1 + 2").resultValue());
  }

  @Test
  void testBinaryOperationSubtract() {
    Assertions.assertEquals(1, Inf.codeToResult("return 3 - 2").resultValue());
  }

  @Test
  void testBinaryOperationMultiply() {
    Assertions.assertEquals(4, Inf.codeToResult("return 2 * 2").resultValue());
  }

  @Test
  void testBinaryOperationDivideInteger() {
    Assertions.assertEquals(1, Inf.codeToResult("return 2 / 2").resultValue());
  }

  @Test
  void testConditionalWithBlocks() {
    Assertions.assertEquals(1, Inf.codeToResult("if (1 == 1) { return 1; } else { return 2; }").resultValue());
  }

  @Test
  void testPassingConditionalWithBranchMerge() {
    Assertions.assertEquals(1, Inf.codeToResult("if (1 == 1) { return 1; } return 2;").resultValue());
  }

  @Test
  void testFailingConditionalWithBranchMerge() {
    Assertions.assertEquals(2, Inf.codeToResult("if (1 == 2) { return 1; } return 2;").resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "return if (1 == 1) then 1 else 2",
    "return if (1 == 1) 1 else 2"
  })
  void testPassingConditionalWithInlineExpression(String code) {
    Assertions.assertEquals(1, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "return if (1 == 2) then 1 else 2",
    "return if (1 == 2) 1 else 2"
  })
  void testFailingConditionalWithInlineExpression(String code) {
    Assertions.assertEquals(2, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "if (1 == 2) { return 1 } 2",
    "if (1 == 2) 1 2"
  })
  void testImplicitReturn(String code) {
    Assertions.assertEquals(2, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testCompactImplicitReturn() {
    Assertions.assertEquals(
      2, Inf.codeToResult("if (1 == 1) 1 2").resultValue(),
      "2, since 1 is discarded and 2 turned into implicit return"
    );
  }

  @Test
  void testCompactReturnWithUnreachableCodeAfter() {
    Assertions.assertThrows(UnreachableCodeException.class, () -> Inf.codeToResult("return if (1 == 1) 1 2"));
  }

  @Test
  void testReturnVariable() {
    Assertions.assertEquals(10, Inf.codeToResult("val a = 10; return a;").resultValue());
    Assertions.assertEquals(11, Inf.codeToResult("val a = 10; return a + 1;").resultValue());
  }

  @Test
  void testBinaryOperationDivideTwoIntegersWithLoss() {

    final var result = Inf.codeToResult("val v = 1 / 2; return v");
    Assertions.assertEquals(0, result.resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 / 2.0",
    "1.0 / 2",
  })
  void testBinaryOperationDivideFloats(String code) {

    final var result = Inf.<Double>codeToResult(code);
    Assertions.assertEquals(0.5d, result.resultValue(), 0.001d);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = 8; val b = 2; val c = a + b; return c;",
  })
  void testSimpleAssignment(String code) {

    // TODO: Something is wrong with this simple code -- fix this and other things might/should/could get better as well :)

    Assertions.assertEquals(10, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = 10; val b = a + 1; return b + 1;",
    "val a = 10; val b = a + 1; b + 1;",
    "val a = 10 val b = a + 1 b + 1"
  })
  void testChainedVariableAssignments(String code) {
    Assertions.assertEquals(12, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testLoop() {
    final var code = "var a = 0; for (var i = 0; i < 10; i += 1) { a += i; } return a;";
    Assertions.assertEquals(45, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testAssign() {
    final var code = "var a = 0; var b = 1; var c = a + b + 1; return c;";
    Assertions.assertEquals(2, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testMove() {
    final var code = "var a = 1; var b = 2; var temp = a; a = b; b = temp; return b;";
    Assertions.assertEquals(1, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "var a = 0; if (a == 0) then a = 10 else a = 5; return a;",
    "val a = 10; a",
  })
  void testScopesParameterized(String code) {
    Assertions.assertEquals(10, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "var a = 10; a += 2; a -= 1; a"
  })
  void testScopes2(String code) {
    Assertions.assertEquals(11, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testCreateArray() {
    final var code = "val a = [0, 1]; return 1;";
    Assertions.assertEquals(1, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testCreateAndAccessArray() {
    final var code = "val a = [10, 20]; return a[0];";
    Assertions.assertEquals(10, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testCreateAndAccessArrayWithInitializer() {
    final var code = "val a = [666; 500]; return a[499];";
    Assertions.assertEquals(666, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testCreateAndAccessArrayWithAlternatingInitializer() {
    final var code = "val a = [1, 2, 3, 4; 500]; return a[0] + a[3] + a[5];";
    Assertions.assertEquals(1 + 4 + 2, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testCreateAndAccessAndUseArray() {
    final var code = "val a = [10, 20]; return a[0] + a[1];";
    Assertions.assertEquals(30, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = [1, 2, 3, 4, 5;uint;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;",
    "val a = [1, 2, 3, 4, 5;uint32;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;",
    "val a = [1, 2, 3, 4, 5;uint64;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;",
    "val a = [1, 2, 3, 4, 5;uint8;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;"
  })
  void testCreateAndIterateArray(String code) {
    Assertions.assertNotEquals(0, Inf.codeToResult(code).resultValue());
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
    Assertions.assertEquals(30, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = [1i8, 2i8, 3i8, 4i8, 5i8;int8;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;"
  })
  void testCreateAndIterateExplicitUint8Array(String code) {
    Assertions.assertNotEquals(0, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val a = [1i32, 2i32, 3i32, 4i32, 5i32;uint8;10]; var t = 0; for (var i = 0; i < 100; i += 1) { t += a[i] } return t;"
  })
  void when_create_array_of_uint8_but_give_int_values_expect_exception(String code) {
    final var ex = Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToResult(code).resultValue());
    Assertions.assertInstanceOf(TyValueNumberInteger.class, ex.given());
    Assertions.assertEquals(Ty.CHAR, ex.expected());
  }

  @Test
  void arrayWithoutInitializerCanBeExplicitlyInitialized() {
    final var code = """
      val a = [;int;100];
      var t = 0;
      for (var i = 0; i < 100; i += 1) { a[i] = i; t += a[i]; }
      return t;
      """;
    Assertions.assertEquals(4950, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testParseAndRunInlineFn() {

    final var ast = Inf.codeToAst("((a: int, b: int) => a + b)(5, 5)");

    Assertions.assertNotNull(ast);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    // TODO: All these should be "const", or at least have the same tests again but with const and other order of instructions
    "val fn = () => 10; fn()",
    "val fn = (): int => 10; fn()",
    "val fn = (): uint => 10; fn()",
    "val fn = (): sint => 10; fn()",
    "val fn = (): int32 => 10; fn()",
    "val fn = () => 10; return fn()",
    "val fn = (a: int, b: int) => a + b; fn(5, 5)",
    "val fn = (a: int, b: int) => a + b; fn(10, 0)",
    "val fn = (a: int, b: int) => a + b; fn(0, 10)",
    "((a: int, b: int) => a + b)(5, 5)",
    "val fn = (a: int, b: int) => a + b; fn(2, 3) + fn(3, 2)",
    "val fn1 = (a: int, b: int) => a + b; val fn2 = (a: int, b: int) => a - b; val fn3 = fn2; return fn1(10, 10) + fn3(30, 40);"
  })
  void testFnCall(String code) {
    Assertions.assertEquals(10, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val fn = (): int64 => 10; fn()",
    "val fn = (): int64 => 10L; fn()"
  })
  void testFnCallPreserves64BitReturnType(String code) {
    Assertions.assertEquals(10L, Inf.codeToResult(code).resultValue());
  }

  @Test
  void testFnCallPreserves128BitReturnType() {
    Assertions.assertEquals(java.math.BigInteger.TEN, Inf.codeToResult("val fn = (): uint128 => 10; fn()").resultValue());
  }

  // TODO: Make TyArrayConst that is a const array with constant values. Should it just take the HirArray?
  //        Also need to match the situation below where the type is an array of a certain type -- how is that syntax? [int;] ? Also support [int; 0..5] ?

  @ParameterizedTest
  @ValueSource(strings = {
    "val fn = (a: int, ...x: [;int;]) => a + x[0] + x[1]; fn(1, 5, 4)",
  })
  void testFnImplVararg(String code) {
    Assertions.assertThrows(InvalidImplementationException.class, () -> Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val fn = (a: int, x: [;int;]) => a + x[0] + x[1]; fn(1, [5, 4])",
  })
  void testFnWithArray(String code) {
    Assertions.assertEquals(10, Inf.codeToResult(code).resultValue());
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

    final var options = InfRunOptions.builder().optLevel(optLevel).build();
    Assertions.assertEquals(10, Inf.codeToResult(code, options).resultValue());
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

    final var options = InfRunOptions.builder().optLevel(optLevel).build();
    Assertions.assertEquals(30, Inf.codeToResult(code, options).resultValue());
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

    final var options = InfRunOptions.builder().arguments(new Object[]{arg}).build();
    Assertions.assertEquals(expected, Inf.codeToResult(code, options).resultValue());
  }

  @Test
  @SneakyThrows
  @Disabled
  void testFPrintF() {

    final var randomFile = UUID.randomUUID() + ".txt";
    final var target = new File("target", randomFile).getAbsoluteFile();

    final var code = """
      val fopen = (filename: *char, mode: *char): *opaque;
      val fclose = (fp: *opaque): int;
      val fprintf = (fp: *opaque, c: *char, ...): int;
      
      val fp = fopen('%s', 'w+');
      fprintf(fp, 'Hello, world!!');
      fclose(fp);
      return 0;
      """.formatted(target.getAbsolutePath().replace('\\', '/'));

    final var options = InfRunOptions.builder()
      .includeCppLibs(false)
      .build();

    try {

      Assertions.assertEquals(0, Inf.codeToResult(code, options).resultValue());
      Assertions.assertTrue(target.exists());
      Assertions.assertEquals("Hello, world!!", Files.readString(target.toPath(), StandardCharsets.UTF_8));

    } finally {
      if (target.exists()) {
        target.delete();
      }
    }
  }

  @Test
  @SneakyThrows
  void testClosure() {

    final var code = """
      val a = 10;
      val fn = (b: int, c: int) => a + b + c;
      
      val d = fn(1, 2); // 13
      val e = fn(3, 4); // 17
      val f = a + d + e; // 40
      
      return f;
      """;

    final var options = InfRunOptions.builder()
      .build();

    Assertions.assertEquals(40, Inf.codeToResult(code, options).resultValue());
  }

  @Test
  @SneakyThrows
  void testClosure2() {

    final var code = """
      val a = 1;
      val fn = (b: int) => {
        val fn2 = (c: int) => a + b + c;
        val d = fn2(10);
        return a + b + d;
      }
      
      val e = fn(100);
      val f = a + e;
      
      return f;
      """;

    final var options = InfRunOptions.builder()
      .build();

    Assertions.assertEquals(213, Inf.codeToResult(code, options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Hello, world!",
    "a",
    "Some longer string"
  })
  @SneakyThrows
  @Disabled
  void testFPrintF_with_lambda(String message) {

    final var randomFile = UUID.randomUUID() + ".txt";
    final var target = new File("target", randomFile).getAbsoluteFile();

    final var code = """
      val fopen = (filename: string, mode: *char): *opaque;
      val fclose = (fp: *opaque): int;
      val fprintf = (fp: *opaque, c: string, ...): int;
      
      return (file: string, message: string) => {
      
        val fp = fopen(file, 'w+');
        fprintf(fp, message);
        fclose(fp);
      
        return 0;
      }
      """;

    final var options = InfRunOptions.builder()
      .arguments(new Object[]{target.getAbsolutePath(), message})
      .includeCppLibs(true)
      .build();

    try {

      Assertions.assertEquals(0, Inf.codeToResult(code, options).resultValue());
      Assertions.assertTrue(target.exists());
      Assertions.assertEquals(message, Files.readString(target.toPath(), StandardCharsets.UTF_8));

    } finally {
      if (target.exists()) {
        target.delete();
      }
    }
  }

  @Test
  @SneakyThrows
  @Disabled
  void testScopes() {

    final var randomFile = UUID.randomUUID() + ".txt";
    final var target = new File("target", randomFile).getAbsoluteFile();

    // TODO: Errors remaining
    //        * "Changed function scope" is never created because it only creates the "Function scope"
    //        * Something is seriously wrong with a lot of stuff -- go back to simpler test cases that tests specific things.

    final var code = """
      val fopen = (filePath: string, mode: *char): *opaque;
      val fclose = (fp: *opaque): int;
      val fprintf = (fp: *opaque, c: string, ...): int;
      
      var globalVar = "Global";
      
      val fp = fopen('%s', 'w+');
      
      val testFunction = () => {
        var functionVar = "Function scope";
        fprintf(fp, globalVar); // Global
        fprintf(fp, functionVar); // Function scope
      
        if (true) {
          val ifVar = "If scope";
          fprintf(fp, ifVar); //  If scope
        }
      
        var loopVar = "";
        for (var i = 0; i <= 1; i += 1) {
          loopVar = "Loop";
          fprintf(fp, loopVar); // should output: Loop and Loop
        }
        fprintf(fp, loopVar); // should output: Loop due to function scope rule of var
      
        val lambdaFunction = () => {
          var lambdaVar = "Lambda scope";
          fprintf(fp, globalVar); // Global
          fprintf(fp, functionVar); // Function scope
          fprintf(fp, loopVar); // Loop1
          fprintf(fp, lambdaVar); // Lambda scope
        }
        lambdaFunction();
      
        globalVar = "Changed global";
        functionVar = "Changed function scope";
        var ifVar = "Changed if scope";
        loopVar = "Changed loop scope";
      
        fprintf(fp, globalVar); // Changed global
        fprintf(fp, functionVar); // Changed function scope
        fprintf(fp, ifVar); // Changed if scope
        fprintf(fp, loopVar); // Changed loop scope
        lambdaFunction(); // Global, Function scope, Loop, Lambda scope
      }
      
      testFunction();
      fprintf(fp, globalVar); // Changed global
      fclose(fp);
      return 0;
      """.formatted(target.getAbsolutePath());

    try {

      Assertions.assertEquals(0, Inf.codeToResult(code).resultValue());
      Assertions.assertTrue(target.exists());

      final var expected = "";
      final var actual = Files.readString(target.toPath(), StandardCharsets.UTF_8);
      Assertions.assertEquals(expected, actual);

    } finally {
      if (target.exists()) {
        target.delete();
      }
    }
  }
}
