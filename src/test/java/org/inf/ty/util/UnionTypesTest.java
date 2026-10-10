package org.inf.ty.util;

import org.inf.Inf;
import org.inf.execution.interpreter.InterpreterCodeExecutor;
import org.inf.ty.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class UnionTypesTest {

  static Stream<Arguments> memberCompatibility() {
    final var integer = Tys.fromString("int", new MachineTarget(64));
    final var destination = Tys.union(integer, Ty.BOOLEAN);
    final var array = new TyValueArray(integer, null);
    return Stream.of(
      Arguments.of(Ty.INTEGER, destination, true),
      Arguments.of(Tys.union(Ty.INTEGER, Ty.BOOLEAN), destination, true),
      Arguments.of(destination, Ty.INTEGER, false),
      Arguments.of(Ty.STRING, destination, false),
      Arguments.of(new TyValueArray(Ty.INTEGER, 2), Tys.union(array, Ty.BOOLEAN), true),
      Arguments.of(new TyPointer<>(new TyValueArray(Ty.INTEGER, 2)), Tys.union(new TyPointer<>(array), Ty.BOOLEAN), true),
      Arguments.of(new TyValueArray(Ty.INTEGER, 2), Tys.union(new TyValueArray(integer, 3), Ty.BOOLEAN), false)
    );
  }

  @ParameterizedTest
  @MethodSource("memberCompatibility")
  void given__member_types__when__compatibility_is_queried__then__only_injection_and_widening_are_allowed(
    final Ty actual, final Ty expected, final boolean compatible
  ) {
    assertEquals(compatible, UnionTypes.compatible(actual, expected));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val U = int | bool; val x: U = if (true) 7 else false; val use = (v: U): bool => true; use(x)",
    "val x: [;int;] | bool = [1,2]; val use = (v: [;int;] | bool): bool => true; use(x)",
    "val F = (x: int): int; val U = F | bool; val f = (y: int): int => y; val v: U = if (true) f else false; val use = (v: U): bool => true; use(v)"
  })
  void given__equivalent_or_unsized_union_member__when__both_backends_execute__then__the_union_can_be_passed(
    final String code
  ) {
    final var mir = Inf.codeToMir(code);
    assertAll(
      () -> assertEquals(true, new InterpreterCodeExecutor().execute(mir.script().entry())),
      () -> assertEquals(true, Inf.codeToResult(code).resultValue())
    );
  }
}
