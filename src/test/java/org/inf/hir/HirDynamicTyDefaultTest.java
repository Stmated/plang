package org.inf.hir;

import org.inf.ty.Ty;
import org.inf.ty.util.MachineTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirDynamicTyDefaultTest {

  static Stream<Ty> staticTypes() {
    return Stream.of(
      Ty.INTEGER,
      Ty.BOOLEAN,
      Ty.INFER
    );
  }

  @ParameterizedTest
  @MethodSource("staticTypes")
  void given__static_constraint__when__resolved_visited_and_transformed__then__type_is_retained_without_source_work(
    final Ty ty
  ) {
    final var annotation = new DynamicTyDefault(ty);
    final var visited = new ArrayList<Hir.Expression>();
    annotation.visit(new HirVisitor() {
      @Override
      public void visitChild(final Hir.Expression expression) {
        visited.add(expression);
      }
    });
    final var resolved = annotation.resolve(expression -> {
      fail("Static constraints do not need source resolution");
      return null;
    });
    final var transformed = annotation.transform(new HirTransformer() {});
    assertAll(
      () -> assertSame(ty, annotation.ty()),
      () -> assertNull(annotation.expression()),
      () -> assertSame(annotation, resolved),
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

  @Test
  void given__null_constraint__when__constructed__then__invalid_record_is_rejected() {
    assertAll(
      () -> assertThrows(NullPointerException.class, () -> new DynamicTyDefault((Ty) null)),
      () -> assertThrows(NullPointerException.class, () -> new DynamicTyDefault(null, new Hir.Lexeme("Alias")))
    );
  }
}
