package org.inf.hir;

import org.inf.ty.Ty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirDynamicTyTest {

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
    final var annotation = new Hir.DynamicTy(ty);
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
  void given__source_constraint__when__resolution_repeats_and_refines__then__cache_refreshes_and_source_identity_is_preserved() {
    final var source = new Hir.Identifier("Alias", null);
    final var annotation = new Hir.DynamicTy(source);
    final var resolved = annotation.resolve(expression -> expression == source ? Ty.INTEGER : null);
    final var repeated = resolved.resolve(expression -> Ty.INTEGER);
    final var refined = repeated.resolve(expression -> Ty.BOOLEAN);
    final var unavailable = refined.resolve(expression -> null);
    assertAll(
      () -> assertSame(Ty.INFER, annotation.ty()),
      () -> assertSame(Ty.INTEGER, resolved.ty()),
      () -> assertSame(Ty.INTEGER, repeated.ty()),
      () -> assertSame(Ty.BOOLEAN, refined.ty()),
      () -> assertSame(Ty.INFER, unavailable.ty()),
      () -> assertSame(source, resolved.expression()),
      () -> assertSame(source, refined.expression()),
      () -> assertSame(source, unavailable.expression())
    );
  }

  @Test
  void given__source_constraint__when__visited_and_transformed__then__only_source_expression_is_traversed() {
    final var source = new Hir.Identifier("Alias", null);
    final var replacement = new Hir.Identifier("Refined", null);
    final var annotation = new Hir.DynamicTy(Ty.INTEGER, source);
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
      () -> assertThrows(NullPointerException.class, () -> new Hir.DynamicTy((Ty) null)),
      () -> assertThrows(NullPointerException.class, () -> new Hir.DynamicTy(null, new Hir.Lexeme("Alias"))),
      () -> assertThrows(NullPointerException.class, () -> new Hir.DynamicTy((Hir.Expression) null))
    );
  }
}
