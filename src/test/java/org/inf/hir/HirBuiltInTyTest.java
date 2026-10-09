package org.inf.hir;

import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HirBuiltInTyTest {

  @ParameterizedTest
  @CsvSource({
    "int, 64",
    "uint8, 64",
    "bool, 64",
    "string, 64",
    "*int16, 64",
    "**opaque, 64",
    "usize, 32",
    "usize, 64"
  })
  void given__recognized_name__when__created__then__type_and_spelling_match_the_compilation_target(
    final String name, final int pointerBitSize
  ) {
    final var target = new MachineTarget(pointerBitSize);
    final var expression = Hir.BuiltInTy.fromString(name, target);
    assertNotNull(expression);
    assertAll(
      () -> assertEquals(name, expression.name()),
      () -> assertEquals(name, expression.toString()),
      () -> assertEquals(Tys.fromString(name, target), expression.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Alias",
    "*Alias",
    "missing"
  })
  void given__unrecognized_name__when__resolved__then__no_builtin_node_is_created(final String name) {
    assertNull(Hir.BuiltInTy.fromString(name, new MachineTarget(64)));
  }

  @Test
  void given__builtin_leaf__when__visited_and_transformed__then__its_specialized_hooks_are_used() {
    final var expression = Hir.BuiltInTy.fromString("bool", new MachineTarget(64));
    final var replacement = Hir.BuiltInTy.fromString("int", new MachineTarget(64));
    final var visited = new ArrayList<Hir.Expression>();
    expression.visit(new HirVisitor() {
      @Override
      public void visitBuiltInTy(final Hir.BuiltInTy leaf) {
        visited.add(leaf);
      }
    });
    final var transformed = expression.transform(new HirTransformer() {
      @Override
      public Hir.Expression transformBuiltInTy(final Hir.BuiltInTy leaf) {
        assertSame(expression, leaf);
        return replacement;
      }
    });
    assertAll(
      () -> assertEquals(List.of(expression), visited),
      () -> assertSame(expression, expression.transform(new HirTransformer() {})),
      () -> assertSame(replacement, transformed)
    );
  }
}
