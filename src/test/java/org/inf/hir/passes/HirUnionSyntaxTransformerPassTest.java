package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.util.TypeComparison;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class HirUnionSyntaxTransformerPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "1 | 2",
    "val int = 1; val bool = 2; int | bool",
    "val value = 1; value | 2",
    "val use = (p: int) => p | 2; use(1)",
    "val use = (int: int, bool: int) => int | bool; use(1, 2)"
  })
  void given__integer_operands__when__union_syntax_is_resolved__then__bitwise_or_is_preserved(final String code) {
    final var root = Inf.codeToThir(code).root();
    final var operations = new ArrayList<Hir.BinaryOperation>();
    final var unions = new ArrayList<Hir.Union>();
    root.visit(new HirVisitor() {
      @Override
      public void visitBinaryOperation(final Hir.BinaryOperation expr) {
        if (expr.kind() == Hir.BinaryOperationKind.BIT_OR) {
          operations.add(expr);
        }
        HirVisitor.super.visitBinaryOperation(expr);
      }

      @Override
      public void visitUnion(final Hir.Union expr) {
        unions.add(expr);
      }
    });
    assertAll(
      () -> assertEquals(1, operations.size()),
      () -> assertTrue(unions.isEmpty()),
      () -> assertTrue(TypeComparison.sameValueType(Ty.INTEGER, root.ty()))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "[7;(1 | 2)]",
    "[7;int;(1 | 2)]",
    "val values: [;int;(1 | 2)] = [1i32, 2i32, 3i32]; values"
  })
  void given__array_length_or__when__raised__then__length_is_a_value_position(final String code) {
    final var lengths = new ArrayList<Hir.BinaryOperation>();
    Inf.codeToHir(code).visit(new HirVisitor() {
      @Override
      public void visitArrayLength(final Hir.Expression expr) {
        if (expr instanceof Hir.BinaryOperation binary) {
          lengths.add(binary);
        }
      }
    });
    assertEquals(1, lengths.size());
    assertEquals(Hir.BinaryOperationKind.BIT_OR, lengths.getFirst().kind());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val U = U | bool; val value: U = 7; value",
    "val U = U; val value: U | bool = 7; value"
  })
  void given__cyclic_type_alias__when__union_syntax_is_resolved__then__cycle_is_rejected(final String code) {
    final var error = assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
    assertNotNull(error.getMessage());
  }
}
