package org.inf.hir;

import org.inf.ty.Ty;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirDynamicTyDefaultTest {

  @ParameterizedTest
  @CsvSource({
    "int",
    "bool",
    "?"
  })
  void given__static_constraint__when__resolved_visited_and_transformed__then__type_is_retained_without_source_work(final String tyName) {
    final var annotation = Hir.BuiltInTy.fromString(tyName, MachineTarget.T64);
    final var visited = new ArrayList<Hir.Expression>();
    annotation.visit(new HirVisitor() {
      @Override
      public void visitChild(final Hir.Expression expression) {
        visited.add(expression);
      }
    });
    final var transformed = annotation.transform(new HirTransformer() {});
    assertAll(
      () -> assertSame(Tys.fromString(tyName, MachineTarget.T64), annotation.ty()),
      () -> assertSame(annotation, transformed),
      () -> assertTrue(visited.isEmpty())
    );
  }

  @Test
  void given__source_constraint__when__visited_and_transformed__then__only_source_expression_is_traversed() {
    final var source = new Hir.Identifier("Alias", null);
    final var replacement = new Hir.Identifier("Refined", null);
    final var annotation = new DynamicTyDefault(Ty.INTEGER, source);
    final var visited = new ArrayList<Hir.Expression>();
    annotation.visit(new HirVisitor() {
      @Override
      public void visitIdentifier(final Hir.Identifier expression) {
        visited.add(expression);
      }
    });
    final var transformed = annotation.transform(new HirTransformer() {
      @Override
      public Hir.Expression transformIdentifier(final Hir.Identifier expression) {
        return replacement;
      }
    });
    assertAll(
      () -> assertEquals(java.util.List.of(source), visited),
      () -> assertSame(Ty.INTEGER, transformed.ty()),
      () -> assertSame(replacement, transformed.expression()),
      () -> assertSame(source, annotation.expression())
    );
  }
}
