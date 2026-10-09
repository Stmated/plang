package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueArray;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirTypeAnnotationResolverTest {

  static Stream<Arguments> aliasConstraints() {
    final var struct = "val Alias = struct { val value: int; }; val value = new heap Alias { value = 7; }; ";
    final var function = "val Alias = (argument: int): int; val value: Alias = (argument) => argument; ";
    final var array = "val Alias = [;int;1]; val value = [7i32]; ";
    return Stream.of(
      Arguments.of(struct + "val v: Alias = value; v", "declaration", TyStruct.class),
      Arguments.of(struct + "val use = (p: Alias) => p; use(value)", "parameter", TyStruct.class),
      Arguments.of(struct + "val use = (): Alias => value; use()", "return", TyStruct.class),
      Arguments.of(struct + "[value;Alias;1]", "element", TyStruct.class),
      Arguments.of(function + "val v: Alias = value; v", "declaration", TyFn.class),
      Arguments.of(function + "val use = (p: Alias) => p; use(value)", "parameter", TyFn.class),
      Arguments.of(function + "val use = (): Alias => value; use()", "return", TyFn.class),
      Arguments.of(function + "[value;Alias;1]", "element", TyFn.class),
      Arguments.of(array + "val v: Alias = value; v", "declaration", TyValueArray.class),
      Arguments.of(array + "val use = (p: Alias) => p; use(value)", "parameter", TyValueArray.class),
      Arguments.of(array + "val use = (): Alias => value; use()", "return", TyValueArray.class),
      Arguments.of(array + "[value;Alias;1]", "element", TyValueArray.class)
    );
  }

  @ParameterizedTest
  @MethodSource("aliasConstraints")
  void given__alias_in_a_type_position__when__typing_repeats__then__constraint_is_cached_and_source_reference_is_preserved(
    final String code, final String role, final Class<? extends Ty> expected
  ) {
    var root = Inf.codeToHir(code);
    final var constraint = annotation(root, role);
    final var original = constraint.get();
    final var source = assertInstanceOf(Hir.Identifier.class, original.expression());
    assertSame(Ty.INFER, original.ty());
    final var raising = new HirToThirRaising(new MachineTarget(64));
    for (int i = 0; i < 2; i++) {
      root = raising.raise(root).root();
      final var resolved = constraint.get();
      assertAll(
        () -> assertInstanceOf(expected, resolved.ty()),
        () -> assertSame(source, resolved.expression()),
        () -> assertNotNull(source.target())
      );
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val v: 1 = 1; v",
    "val v: true = true; v",
    "val n = 1; val v: n = 1; v",
    "val make = () => 1; val v: (make()) = 1; v",
    "val v: { 1 } = 1; v",
    "val use = (p: 1) => p; use(1)",
    "val read = (sample: int, value: sample) => value; read(1, 2)",
    "val n = 1; val use = (p: n) => p; use(1)",
    "val make = () => 1; val use = (p: make()) => p; use(1)",
    "val use = (p: { 1 }) => p; use(1)",
    "val use = (): 1 => 1; use()",
    "val n = 1; val use = (): n => 1; use()",
    "val make = () => 1; val use = (): make() => 1; use()",
    "val use = (): { 1 } => 1; use()",
    "[1;1;1]",
    "val n = 1; [1;n;1]",
    "val make = () => 1; [1;make();1]",
    "[1;{ 1 };1]",
    "val Runtime = (p: int) => p; val v: Runtime = (p: int) => p; v",
    "val Runtime = [1]; val v: Runtime = [1]; v",
    "val S = struct { val value: int; }; val Runtime = new heap S { value = 1; }; val v: Runtime = Runtime; v"
  })
  void given__runtime_value_as_type_annotation__when__full_typing_runs__then__value_derived_constraint_is_rejected(
    final String code
  ) {
    final var error = assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
    assertEquals("Type annotations require types, not value expressions", error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val S = struct { val value: int; }; val s = new heap S { value = 1; }; val copy: s = new heap S { value = 2; }; copy",
    "val fn = (v: int): int => v; val f: fn = (v) => v; f",
    "val values = [1, 2]; val copy: values = [1, 2]; copy",
    "val copy: [1, 2] = [1, 2]; copy"
  })
  void given__value_with_a_matching_type_shape__when__used_as_an_annotation__then__runtime_shape_does_not_define_a_type(
    final String code
  ) {
    final var error = assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
    assertEquals("Type annotations require types, not value expressions", error.getMessage());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "unspecified", value = {
    "2 * 3 | 6",
    "count | unspecified"
  })
  void given__source_array_length__when__annotation_is_resolved__then__existing_static_length_rules_are_preserved(
    final String length, final Integer expectedSize
  ) {
    final var root = Inf.codeToThir("""
      val count = 6;
      val v: [;int;%s] = [1i32, 2i32, 3i32, 4i32, 5i32, 6i32];
      v
      """.formatted(length)).root();
    final var constraint = annotation(root, "declaration").get();
    final var source = assertInstanceOf(Hir.Array.class, constraint.expression());
    final var resolved = assertInstanceOf(TyValueArray.class, constraint.ty());
    assertAll(
      () -> assertEquals(expectedSize, resolved.size()),
      () -> assertSame(source.arrayTy(), resolved),
      () -> assertEquals(Tys.fromString("int", new MachineTarget(64)), resolved.elementType())
    );
  }

  @Test
  void given__builtin_and_omitted_annotations__when__typed__then__constraints_are_direct_and_non_null() {
    final var root = Inf.codeToThir("val v: int = 7; val use = (p: bool, q: int) => p; [7;int;1]; [7]").root();
    final var constraints = new ArrayList<Hir.DynamicTy>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        constraints.add(declaration.typeAnnotation());
        HirVisitor.super.visitDec(declaration);
      }

      @Override
      public void visitParameter(final Hir.Parameter parameter) {
        constraints.add(parameter.typeAnnotation());
        HirVisitor.super.visitParameter(parameter);
      }

      @Override
      public void visitFunctionSignatureReturnType(final Hir.DynamicTy annotation) {
        constraints.add(annotation);
        HirVisitor.super.visitFunctionSignatureReturnType(annotation);
      }

      @Override
      public void visitArrayElementType(final Hir.DynamicTy annotation) {
        constraints.add(annotation);
        HirVisitor.super.visitArrayElementType(annotation);
      }
    });
    assertEquals(7, constraints.size());
    assertAll(
      () -> assertEquals(3, constraints.stream()
        .filter(it -> it.ty().equals(Tys.fromString("int", new MachineTarget(64)))).count()),
      () -> assertEquals(1, constraints.stream().filter(it -> it.ty().equals(Ty.BOOLEAN)).count()),
      () -> assertEquals(3, constraints.stream().filter(it -> it.ty() == Ty.INFER).count()),
      () -> assertTrue(constraints.stream().allMatch(it -> it.expression() == null))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "struct { val value: int; }",
    "(argument: int): int",
    "[;int;1]"
  })
  void given__resolved_alias_constraint__when__source_definition_is_refined__then__cache_and_parameter_binding_refresh(
    final String definition
  ) {
    final var root = Inf.codeToThir("val Alias = %s; val use = (p: Alias) => p; use".formatted(definition)).root();
    final var constraint = annotation(root, "parameter");
    final var original = constraint.get();
    final var definitions = new ArrayList<Hir.Expression>();
    final var parameters = new ArrayList<Hir.Parameter>();
    root.visit(new HirVisitor() {
      @Override
      public void visitAssignment(final Hir.Assignment assignment) {
        if (assignment.lhs() instanceof Hir.Dec declaration && declaration.lexeme().name().equals("Alias")) {
          definitions.add(assignment.rhs());
        }
        HirVisitor.super.visitAssignment(assignment);
      }

      @Override
      public void visitParameter(final Hir.Parameter parameter) {
        if (parameter.lexeme().name().equals("p")) {
          parameters.add(parameter);
        }
        HirVisitor.super.visitParameter(parameter);
      }
    });
    assertAll(
      () -> assertEquals(1, definitions.size()),
      () -> assertEquals(1, parameters.size())
    );
    switch (definitions.getFirst()) {
      case Hir.Struct struct -> struct.declarations()[0].typeAnnotation(new Hir.DynamicTy(Ty.BOOLEAN));
      case Hir.FunctionSignature signature -> {
        signature.parameters()[0].typeAnnotation(new Hir.DynamicTy(Ty.BOOLEAN));
        signature.returnTypeAnnotation(new Hir.DynamicTy(Ty.BOOLEAN));
      }
      case Hir.Array array -> array.elementType(new Hir.DynamicTy(Ty.BOOLEAN));
      default -> fail("Expected an alias source definition");
    }
    for (int i = 0; i < 2; i++) {
      HirTyCommonVisitorPass.resolveAvailableTypes(root);
      HirTyCommonVisitorPass.pass(root);
      final var refreshed = constraint.get();
      final var parameter = parameters.getFirst();
      assertAll(
        () -> assertNotEquals(original.ty(), refreshed.ty()),
        () -> assertSame(original.expression(), refreshed.expression()),
        () -> assertEquals(refreshed.ty(), parameter.resolvedTy())
      );
      final Ty member = switch (refreshed.ty()) {
        case TyStruct struct -> struct.fields()[0].ty();
        case TyFn function -> function.returnTy();
        case TyValueArray array -> array.elementType();
        default -> throw new AssertionError("Expected a resolved alias type");
      };
      assertSame(Ty.BOOLEAN, member);
    }
  }

  private static Supplier<Hir.DynamicTy> annotation(final Hir.Expression root, final String role) {
    final var found = new ArrayList<Supplier<Hir.DynamicTy>>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        if (role.equals("declaration") && declaration.lexeme().name().equals("v")) {
          found.add(declaration::typeAnnotation);
        }
        HirVisitor.super.visitDec(declaration);
      }

      @Override
      public void visitParameter(final Hir.Parameter parameter) {
        if (role.equals("parameter") && parameter.lexeme().name().equals("p")) {
          found.add(parameter::typeAnnotation);
        }
        HirVisitor.super.visitParameter(parameter);
      }

      @Override
      public void visitFunction(final Hir.Function function) {
        if (role.equals("return")
          && function.signature().returnTypeAnnotation().expression() instanceof Hir.Identifier identifier
          && identifier.name().equals("Alias")) {
          found.add(function.signature()::returnTypeAnnotation);
        }
        HirVisitor.super.visitFunction(function);
      }

      @Override
      public void visitArray(final Hir.Array array) {
        if (role.equals("element") && array.elementType().expression() instanceof Hir.Identifier identifier
          && identifier.name().equals("Alias")) {
          found.add(array::elementType);
        }
        HirVisitor.super.visitArray(array);
      }
    });
    assertEquals(1, found.size());
    return found.getFirst();
  }
}
