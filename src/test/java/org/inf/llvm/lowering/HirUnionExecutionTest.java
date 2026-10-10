package org.inf.llvm.lowering;

import org.inf.Inf;
import org.inf.execution.interpreter.InterpreterCodeExecutor;
import org.inf.mir.MirUnionValue;
import org.inf.mir.model.MirFunction;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyPointer;
import org.inf.ty.TyValueArray;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirUnionExecutionTest {

  private static final Ty INT = Tys.fromString("int", new MachineTarget(64));

  static Stream<Arguments> unionPrograms() {
    return Stream.of(
      Arguments.of("val v: int | bool = 7; v", INT, 7),
      Arguments.of("val v: int | bool = true; v", Ty.BOOLEAN, true),
      Arguments.of("val v: int | bool = false; v", Ty.BOOLEAN, false),
      Arguments.of("val values = [7, true;int | bool;2]; values[1]", Ty.BOOLEAN, true),
      Arguments.of("val pair: (int | bool, string) = (7, \"label\"); pair[0]", INT, 7),
      Arguments.of("val S = struct { val value: int | bool; }; val s = new heap S { value = 7; }; s.value", INT, 7),
      Arguments.of("val use = (p: int | bool): int | bool => p; use(7)", INT, 7),
      Arguments.of("val use = (p: int | bool): int | bool => p; use(true)", Ty.BOOLEAN, true),
      Arguments.of("""
        val use = (flag: bool): int | bool => {
          if (flag) { return 7; };
          return true;
        };
        use(true)
        """, INT, 7),
      Arguments.of("""
        val use = (flag: bool): int | bool => {
          if (flag) { return 7; };
          return true;
        };
        use(false)
        """, Ty.BOOLEAN, true),
      Arguments.of("val U = int | bool; val V = U; val v: V = 7; v", INT, 7),
      Arguments.of("val U = int | bool; val V = U; val v: V = true; v", Ty.BOOLEAN, true),
      Arguments.of("val U = int | bool; val x: U = if (true) 7 else false; x", INT, 7),
      Arguments.of("val U = int | bool; val V = U; val use = (): V => 7; use()", INT, 7),
      Arguments.of("""
        val outer = () => {
          val U = int | bool;
          val V = U;
          val use = (p: V): V => p;
          use(true)
        };
        outer()
        """, Ty.BOOLEAN, true),
      Arguments.of("""
        val outer = () => {
          val U = int | bool;
          val V = U;
          val use = (): V => { return 7; };
          use()
        };
        outer()
        """, INT, 7)
    );
  }

  @ParameterizedTest
  @MethodSource("unionPrograms")
  void given__source_union_annotation_or_alias__when__executed__then__the_selected_variant_and_payload_are_returned(
    final String code, final Ty variantType, final Object payload
  ) {
    final var result = assertInstanceOf(MirUnionValue.class, Inf.codeToResult(code).resultValue());
    assertAll(
      () -> assertEquals(Tys.union(INT, Ty.BOOLEAN), result.type()),
      () -> assertEquals(variantType, result.type().types()[result.variant()]),
      () -> assertEquals(payload, result.payload())
    );
  }

  @Test
  void given__sized_array_in_an_unsized_array_union__when__executed__then__array_variant_and_payload_are_preserved() {
    final var code = "val x: [;int;] | bool = [1, 2]; x";
    final var interpreted = assertInstanceOf(MirUnionValue.class,
      new InterpreterCodeExecutor().execute(Inf.codeToMir(code).initNode()));
    final var pointer = assertInstanceOf(TyPointer.class, interpreted.type().types()[interpreted.variant()]);
    final var array = assertInstanceOf(TyValueArray.class, pointer.inner());
    final var elements = assertInstanceOf(Object[].class, interpreted.payload());
    assertAll(
      () -> assertEquals(0, interpreted.variant()),
      () -> assertEquals(INT, array.elementType()),
      () -> assertNull(array.size()),
      () -> assertArrayEquals(new Object[]{1, 2}, elements),
      () -> assertEquals(7, Inf.codeToResult(code + "; 7").resultValue())
    );
  }

  @Test
  void given__function_union_member_with_different_parameter_names__when__executed__then__callable_variant_is_preserved() {
    final var code = """
      val F = (x: int): int;
      val U = F | bool;
      val f = (y: int): int => y;
      val v: U = if (true) f else false;
      v
      """;
    final var interpreted = assertInstanceOf(MirUnionValue.class,
      new InterpreterCodeExecutor().execute(Inf.codeToMir(code).initNode()));
    final var pointer = assertInstanceOf(TyPointer.class, interpreted.type().types()[interpreted.variant()]);
    final var signature = assertInstanceOf(TyFn.class, pointer.inner());
    final var function = assertInstanceOf(MirFunction.class, interpreted.payload());
    assertAll(
      () -> assertEquals(0, interpreted.variant()),
      () -> assertEquals(INT, signature.returnTy()),
      () -> assertEquals(1, signature.parameters().length),
      () -> assertEquals("x", signature.parameters()[0].name()),
      () -> assertEquals(INT, signature.parameters()[0].ty()),
      () -> assertEquals(INT, function.signature().returnType()),
      () -> assertEquals(1, function.signature().parameters().length),
      () -> assertEquals(7, Inf.codeToResult(code + "; f(7)").resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 | 2",
    "val a = 1; val b = 2; a | b",
    "val a = 1; val alias = a; alias | 2",
    "val v: int | int = 1 | 2; v"
  })
  void given__integer_or_duplicate_integer_union__when__executed__then__ordinary_bitwise_result_is_returned(
    final String code
  ) {
    assertEquals(3, Inf.codeToResult(code).resultValue());
  }
}
