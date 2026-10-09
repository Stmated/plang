package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HirFieldResolutionTest {

  @Test
  void given__out_of_order_initializers__when__fields_are_queried__then__access_and_initializers_select_their_layout_fields() {
    final var root = Inf.codeToThir("""
      val S = struct { val flag: bool; val value: uint8; };
      val s = new heap S { value = 7; flag = false; };
      s.value
      """).root();
    final var construction = nodes(root, Hir.NewByBlock.class).getFirst();
    final var access = nodes(root, Hir.DotAccess.class).getFirst();
    final var owner = Tys.getConstructionTargetTy(construction.target());
    final var value = construction.fields()[0];
    final var flag = construction.fields()[1];
    final var memberField = Tys.getMemberField(access);
    final var valueField = Tys.getInitializerField(construction, value);
    final var flagField = Tys.getInitializerField(construction, flag);
    assertAll(
      () -> assertEquals("value", access.name()),
      () -> assertEquals(1, memberField.index()),
      () -> assertSame(owner, memberField.owner()),
      () -> assertSame(owner, valueField.owner()),
      () -> assertEquals(List.of(1, 0), List.of(valueField.index(), flagField.index())),
      () -> assertEquals(access.memberTy(), valueField.ty()),
      () -> assertEquals(valueField.ty(), value.rhs().ty()),
      () -> assertSame(Ty.VOID, value.ty()),
      () -> assertSame(Ty.BOOLEAN, flagField.ty()),
      () -> assertEquals(Tys.fromString("uint8", new MachineTarget(64)), root.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(booleans = {
    false,
    true
  })
  void given__field_uses__when__layout_order_and_type_change__then__queries_immediately_select_the_current_fields(
    final boolean availableOnly
  ) {
    final var root = Inf.codeToThir("""
      val S = struct { val flag: bool; val value: uint8; };
      val s = new heap S { value = 7; flag = false; };
      s.value
      """).root();
    final var construction = nodes(root, Hir.NewByBlock.class).getFirst();
    final var access = nodes(root, Hir.DotAccess.class).getFirst();
    final var refined = new TyStruct(new TyField[] {
      new TyField("value", Ty.LONG),
      new TyField("flag", Ty.BOOLEAN)
    });
    assertInstanceOf(Hir.Dec.class, assertInstanceOf(Hir.Identifier.class, construction.target()).target()).resolvedTy(refined);
    assertInstanceOf(Hir.Dec.class, assertInstanceOf(Hir.Identifier.class, access.target()).target()).resolvedTy(refined);

    assertAll(
      () -> assertSame(refined, Tys.getMemberField(access).owner()),
      () -> assertEquals(0, Tys.getMemberField(access).index()),
      () -> assertSame(Ty.LONG, access.memberTy()),
      () -> assertEquals(0, Tys.getInitializerField(construction, construction.fields()[0]).index()),
      () -> assertSame(Ty.LONG, Tys.getInitializerField(construction, construction.fields()[0]).ty()),
      () -> assertEquals(1, Tys.getInitializerField(construction, construction.fields()[1]).index())
    );

    HirFieldResolution.resolve(construction, availableOnly);
    HirFieldResolution.resolve(access, availableOnly);
    assertSame(Ty.LONG, access.ty());
  }

  @Test
  void given__previously_resolved_uses__when__the_receiver_becomes_unavailable__then__queries_do_not_retain_the_old_layout() {
    final var root = Inf.codeToThir("""
      val S = struct { val value: int; };
      val s = new heap S { value = 7; };
      s.value
      """).root();
    final var construction = nodes(root, Hir.NewByBlock.class).getFirst();
    final var access = nodes(root, Hir.DotAccess.class).getFirst();
    assertInstanceOf(Hir.Dec.class, assertInstanceOf(Hir.Identifier.class, construction.target()).target()).resolvedTy(null);
    assertInstanceOf(Hir.Dec.class, assertInstanceOf(Hir.Identifier.class, access.target()).target()).resolvedTy(null);

    assertAll(
      () -> assertNull(Tys.getMemberField(access)),
      () -> assertSame(Ty.INFER, access.memberTy()),
      () -> assertNull(Tys.getInitializerField(construction, construction.fields()[0]))
    );

    HirFieldResolution.resolve(construction, true);
    HirFieldResolution.resolve(access, true);
    assertSame(Ty.INFER, access.ty());
  }

  @Test
  void given__noncontinuing_initializer_and_member_receiver__when__typed__then__fields_are_available_without_hypothetical_completion() {
    final var root = Inf.codeToThir("""
      val S = struct { val value: int; };
      (new heap S { value = { return true; }; }).value
      """).root();
    final var construction = nodes(root, Hir.NewByBlock.class).getFirst();
    final var initializer = construction.fields()[0];
    final var access = nodes(root, Hir.DotAccess.class).getFirst();
    assertAll(
      () -> assertSame(Ty.DEADEND, initializer.ty()),
      () -> assertSame(Ty.DEADEND, construction.ty()),
      () -> assertSame(Ty.DEADEND, access.ty()),
      () -> assertEquals(Tys.getInitializerField(construction, initializer).ty(), access.memberTy()),
      () -> assertNotNull(Tys.getMemberField(access)),
      () -> assertSame(Ty.BOOLEAN, root.ty())
    );
  }

  @Test
  void given__nested_constructions_with_the_same_field_name__when__typed__then__each_initializer_uses_its_own_construction_context() {
    final var root = Inf.codeToThir("""
      val Inner = struct { val value: uint8; };
      val Outer = struct { val nested: Inner; val value: uint16; };
      new heap Outer { nested = new heap Inner { value = 7; }; value = 9; }
      """).root();
    final var constructions = nodes(root, Hir.NewByBlock.class);
    final var outer = constructions.getFirst();
    final var inner = constructions.getLast();
    final var uint8 = Tys.fromString("uint8", new MachineTarget(64));
    final var uint16 = Tys.fromString("uint16", new MachineTarget(64));
    assertAll(
      () -> assertEquals(uint8, inner.fields()[0].rhs().ty()),
      () -> assertEquals(uint16, outer.fields()[1].rhs().ty()),
      () -> assertEquals(uint8, Tys.getInitializerField(inner, inner.fields()[0]).ty()),
      () -> assertEquals(uint16, Tys.getInitializerField(outer, outer.fields()[1]).ty())
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "int | 7",
    "[;int;1] | [7]",
    "(int, bool) | (7, true)",
    "Fn | (v: int) => v"
  })
  void given__unknown_initializer_name__when__fields_are_resolved__then__the_error_does_not_depend_on_value_kind(
    final String annotation, final String value
  ) {
    final var error = assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir("""
      val Fn = (v: int): int;
      val S = struct { val expected: %s; };
      new heap S { missing = %s; }
      """.formatted(annotation, value)));
    assertEquals("Unknown struct field: missing", error.getMessage());
  }

  private static <T extends Hir.Expression> List<T> nodes(final Hir.Expression root, final Class<T> type) {
    final var found = new ArrayList<T>();
    root.visit(new HirVisitor() {
      @Override
      public void visitChild(final Hir.Expression expression) {
        if (type.isInstance(expression)) {
          found.add(type.cast(expression));
        }
        HirVisitor.super.visitChild(expression);
      }
    });
    return found;
  }
}
