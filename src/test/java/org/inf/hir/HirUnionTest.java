package org.inf.hir;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.passes.HirTyCommonVisitorPass;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyStruct;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirUnionTest {

  private static final Ty INT = Tys.fromString("int", new MachineTarget(64));

  @Test
  void given__equivalent_function_alias_members__when__typed__then__the_union_simplifies_without_losing_source_members() {
    final var code = """
      val A = (x: int): int;
      val B = (y: int): int;
      val U = A | B;
      val f: U = (z: int) => z;
      f(7)
      """;
    final var root = Inf.codeToThir(code).root();
    final var union = assertInstanceOf(Hir.Union.class, definition(root, "U").rhs());
    final var function = assertInstanceOf(TyFn.class, union.unionTy());
    assertAll(
      () -> assertEquals(2, union.elements().length),
      () -> assertEquals("x", function.parameters()[0].name()),
      () -> assertEquals(function, declaration(root, "f").typeAnnotation().ty()),
      () -> assertEquals(7, Inf.codeToResult(code).resultValue())
    );
  }

  @Test
  void given__explicit_union_annotation__when__raised_to_hir__then__source_members_and_completion_are_separate() {
    final var root = Inf.codeToHir("val v: int | bool = 7; v");
    final var declaration = declaration(root, "v");
    final var annotation = declaration.typeAnnotation();
    final var union = assertInstanceOf(Hir.Union.class, annotation.expression());
    final var integer = assertInstanceOf(Hir.Identifier.class, union.elements()[0]);
    final var bool = assertInstanceOf(Hir.Identifier.class, union.elements()[1]);
    final var assignment = nodes(root, Hir.Assignment.class).getFirst();
    assertAll(
      () -> assertEquals(2, union.elements().length),
      () -> assertEquals("int", integer.name()),
      () -> assertEquals("bool", bool.name()),
      () -> assertSame(Ty.INFER, annotation.ty()),
      () -> assertSame(Ty.INFER, union.unionTy()),
      () -> assertSame(Ty.VOID, union.ty()),
      () -> assertSame(declaration, assignment.lhs()),
      () -> assertInstanceOf(Hir.Literal.class, assignment.rhs())
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '#', value = {
    "int | bool | string # int bool string",
    "int | (bool | string) # int bool string",
    "(int | bool) | string # int bool string",
    "bool | (int | bool) # bool int bool",
    "int | int # int int"
  })
  void given__nested_or_duplicate_union_syntax__when__raised_to_hir__then__direct_members_are_flattened_in_source_order(
    final String annotation, final String expectedNames
  ) {
    final var root = Inf.codeToHir("val v: %s = 7; v".formatted(annotation));
    final var union = assertInstanceOf(Hir.Union.class, declaration(root, "v").typeAnnotation().expression());
    final var names = Arrays.stream(union.elements())
      .map(element -> assertInstanceOf(Hir.Identifier.class, element).name()).toList();
    assertAll(
      () -> assertEquals(List.of(expectedNames.split(" ")), names),
      () -> assertEquals(1, nodes(root, Hir.Union.class).size()),
      () -> assertSame(Ty.INFER, union.unionTy()),
      () -> assertSame(Ty.VOID, union.ty())
    );
  }

  @Test
  void given__source_union__when__visited_and_transformed__then__specialized_hooks_and_member_traversal_are_used() {
    final var union = sourceUnion("int | bool");
    final var replacement = sourceUnion("bool | int");
    final var visited = new ArrayList<Hir.Union>();
    final var members = new ArrayList<String>();
    union.visit(new HirVisitor() {
      @Override
      public void visitUnion(final Hir.Union expression) {
        visited.add(expression);
        HirVisitor.super.visitUnion(expression);
      }

      @Override
      public void visitIdentifier(final Hir.Identifier expression) {
        members.add(expression.name());
      }
    });
    final var transformed = union.transform(new HirTransformer() {
      @Override
      public Hir.Expression transformUnion(final Hir.Union expression) {
        assertSame(union, expression);
        return replacement;
      }
    });
    assertAll(
      () -> assertEquals(List.of(union), visited),
      () -> assertEquals(List.of("int", "bool"), members),
      () -> assertSame(union, union.transform(new HirTransformer() {})),
      () -> assertSame(replacement, transformed)
    );
  }

  static Stream<Arguments> unionTypes() {
    return Stream.of(
      Arguments.of("int | bool", "7", Tys.union(INT, Ty.BOOLEAN)),
      Arguments.of("int | bool", "true", Tys.union(INT, Ty.BOOLEAN)),
      Arguments.of("int | int", "7", INT),
      Arguments.of("bool | (int | bool)", "true", Tys.union(Ty.BOOLEAN, INT)),
      Arguments.of("int | (bool | string)", "7", Tys.union(INT, Ty.BOOLEAN, Ty.STRING)),
      Arguments.of("(int | bool) | string", "true", Tys.union(INT, Ty.BOOLEAN, Ty.STRING))
    );
  }

  @ParameterizedTest
  @MethodSource("unionTypes")
  void given__source_union__when__typed__then__builtin_members_and_simplified_binding_type_are_resolved(
    final String annotation, final String value, final Ty expected
  ) {
    final var root = Inf.codeToThir("val v: %s = %s; v".formatted(annotation, value)).root();
    final var declaration = declaration(root, "v");
    final var union = assertInstanceOf(Hir.Union.class, declaration.typeAnnotation().expression());
    assertAll(
      () -> assertEquals(expected, union.unionTy()),
      () -> assertEquals(expected, declaration.typeAnnotation().ty()),
      () -> assertEquals(expected, declaration.resolvedTy()),
      () -> assertEquals(expected, root.ty()),
      () -> assertSame(Ty.VOID, union.ty()),
      () -> assertTrue(Arrays.stream(union.elements()).allMatch(Hir.BuiltInTy.class::isInstance))
    );
  }

  @Test
  void given__union_alias_initializer__when__raised_and_typed__then__bitwise_syntax_is_promoted_only_after_name_resolution() {
    final var code = "val U = int | bool; val v: U = 7; v";
    final var hir = Inf.codeToHir(code);
    final var binary = nodes(hir, Hir.BinaryOperation.class).getFirst();
    final var thir = Inf.codeToThir(code).root();
    final var union = assertInstanceOf(Hir.Union.class, definition(thir, "U").rhs());
    final var expected = Tys.union(INT, Ty.BOOLEAN);
    assertAll(
      () -> assertEquals(Hir.BinaryOperationKind.BIT_OR, binary.kind()),
      () -> assertInstanceOf(Hir.Identifier.class, binary.lhs()),
      () -> assertInstanceOf(Hir.Identifier.class, binary.rhs()),
      () -> assertTrue(nodes(hir, Hir.Union.class).isEmpty()),
      () -> assertEquals(expected, union.unionTy()),
      () -> assertEquals(expected, declaration(thir, "v").resolvedTy()),
      () -> assertSame(Ty.VOID, union.ty())
    );
  }

  @Test
  void given__chained_alias_union_member__when__typed__then__alias_source_is_retained_and_resolved_type_is_flattened() {
    final var root = Inf.codeToThir("""
      val U = int | bool;
      val V = U;
      val v: V | string = 7;
      v
      """).root();
    final var union = assertInstanceOf(Hir.Union.class, declaration(root, "v").typeAnnotation().expression());
    final var member = assertInstanceOf(Hir.Identifier.class, union.elements()[0]);
    final var string = assertInstanceOf(Hir.BuiltInTy.class, union.elements()[1]);
    final var expected = Tys.union(INT, Ty.BOOLEAN, Ty.STRING);
    assertAll(
      () -> assertEquals(2, union.elements().length),
      () -> assertEquals("V", member.name()),
      () -> assertSame(declaration(root, "V"), member.target()),
      () -> assertEquals("string", string.name()),
      () -> assertInstanceOf(Hir.Identifier.class, definition(root, "V").rhs()),
      () -> assertEquals(expected, union.unionTy()),
      () -> assertEquals(expected, declaration(root, "v").resolvedTy()),
      () -> assertEquals(expected, root.ty()),
      () -> assertSame(Ty.VOID, union.ty())
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '#', value = {
    "val U = int | bool; val V = U; val use = (): V => 7; use() # 1",
    "val outer = () => { val U = int | bool; val V = U; val use = (): V => 7; use() }; outer() # 2"
  })
  void given__union_alias_used_inside_a_function__when__lambda_lifting_runs__then__aliases_are_not_runtime_captures(
    final String code, final int expectedFunctions
  ) {
    final var root = Inf.codeToThir(code).root();
    final var functions = nodes(root, Hir.Function.class);
    assertAll(
      () -> assertEquals(expectedFunctions, functions.size()),
      () -> assertTrue(functions.stream().allMatch(function -> function.signature().parameters().length == 0)),
      () -> assertEquals(Tys.union(INT, Ty.BOOLEAN), root.ty())
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '#', value = {
    "val v: int | bool = 7; v # declaration",
    "val use = (p: int | bool) => p; use(7) # parameter",
    "val use = (): int | bool => 7; use() # return",
    "val S = struct { val value: int | bool; }; S # field",
    "val v: [;int | bool;1] = [7]; v # element",
    "'val v: (int | bool, string) = (7, \"label\"); v' # tuple"
  })
  void given__union_in_a_supported_annotation_position__when__raised_and_typed__then__source_and_constraint_are_preserved(
    final String code, final String role
  ) {
    final var hirUnion = nodes(Inf.codeToHir(code), Hir.Union.class).getFirst();
    final var root = Inf.codeToThir(code).root();
    final var union = nodes(root, Hir.Union.class).getFirst();
    final Ty constraint = switch (role) {
      case "declaration" -> declaration(root, "v").typeAnnotation().ty();
      case "parameter" -> nodes(root, Hir.Parameter.class).getFirst().typeAnnotation().ty();
      case "return" -> nodes(root, Hir.Function.class).getFirst().signature().returnTypeAnnotation().ty();
      case "field" -> nodes(root, Hir.Struct.class).getFirst().declarations()[0].typeAnnotation().ty();
      case "element" -> nodes(root, Hir.Array.class).getFirst().elementType().ty();
      case "tuple" -> assertInstanceOf(TyStruct.class, declaration(root, "v").typeAnnotation().ty()).fields()[0].ty();
      default -> throw new AssertionError("Unknown annotation role: " + role);
    };
    final var expected = Tys.union(INT, Ty.BOOLEAN);
    assertAll(
      () -> assertSame(Ty.INFER, hirUnion.unionTy()),
      () -> assertTrue(Arrays.stream(hirUnion.elements()).allMatch(Hir.Identifier.class::isInstance)),
      () -> assertEquals(expected, union.unionTy()),
      () -> assertEquals(expected, constraint),
      () -> assertSame(Ty.VOID, union.ty())
    );
  }

  @Test
  void given__chained_alias_constraint__when__source_members_change_and_typing_repeats__then__union_and_binding_caches_refresh() {
    final var root = Inf.codeToThir("val U = int | bool; val V = U; val use = (p: V) => p; use").root();
    final var union = assertInstanceOf(Hir.Union.class, definition(root, "U").rhs());
    final var parameter = nodes(root, Hir.Parameter.class).getFirst();
    final var source = parameter.typeAnnotation().expression();
    final var original = parameter.resolvedTy();
    final var replacement = Inf.codeToThir("val U = int | string; val use = (p: U) => p; use").root();
    final var elements = assertInstanceOf(Hir.Union.class, definition(replacement, "U").rhs()).elements();
    union.elements(elements);
    union.unionTy(Ty.INFER);

    for (int i = 0; i < 2; i++) {
      HirTyCommonVisitorPass.resolveAvailableTypes(root);
      HirTyCommonVisitorPass.pass(root);
      final var expected = Tys.union(INT, Ty.STRING);
      assertAll(
        () -> assertSame(elements, union.elements()),
        () -> assertNotEquals(original, parameter.resolvedTy()),
        () -> assertEquals(expected, union.unionTy()),
        () -> assertEquals(expected, parameter.typeAnnotation().ty()),
        () -> assertEquals(expected, parameter.resolvedTy()),
        () -> assertSame(source, parameter.typeAnnotation().expression()),
        () -> assertSame(Ty.VOID, union.ty())
      );
    }
  }

  @Test
  void given__inferred_conditional_union__when__assigned_to_a_source_alias__then__annotated_member_width_is_preserved() {
    final var root = Inf.codeToThir("val U = int | bool; val x: U = if (true) 7 else false; x").root();
    final var declaration = declaration(root, "x");
    final var union = assertInstanceOf(Hir.Union.class, definition(root, "U").rhs());
    final var source = assertInstanceOf(Hir.Identifier.class, declaration.typeAnnotation().expression());
    final var expected = Tys.union(INT, Ty.BOOLEAN);
    assertAll(
      () -> assertEquals(expected, union.unionTy()),
      () -> assertEquals(expected, declaration.typeAnnotation().ty()),
      () -> assertEquals(expected, declaration.resolvedTy()),
      () -> assertEquals(expected, root.ty()),
      () -> assertEquals("U", source.name())
    );
  }

  @Test
  @Timeout(30)
  void given__shared_twenty_four_level_union_alias_graph__when__typed__then__duplicate_paths_resolve_to_one_union() {
    final var code = new StringBuilder("val U0 = int | bool;\n");
    for (int i = 1; i <= 24; i++) {
      code.append("val U%d = U%d | U%d;\n".formatted(i, i - 1, i - 1));
    }
    code.append("val x: U24 = 7; x");
    final var root = Inf.codeToThir(code.toString()).root();
    final var union = assertInstanceOf(Hir.Union.class, definition(root, "U24").rhs());
    final var declaration = declaration(root, "x");
    final var source = assertInstanceOf(Hir.Identifier.class, declaration.typeAnnotation().expression());
    final var expected = Tys.union(INT, Ty.BOOLEAN);
    assertAll(
      () -> assertEquals(2, union.elements().length),
      () -> assertEquals(List.of("U23", "U23"), Arrays.stream(union.elements())
        .map(element -> assertInstanceOf(Hir.Identifier.class, element).name()).toList()),
      () -> assertEquals(expected, union.unionTy()),
      () -> assertEquals(expected, declaration.typeAnnotation().ty()),
      () -> assertEquals(expected, declaration.resolvedTy()),
      () -> assertEquals(expected, root.ty()),
      () -> assertEquals("U24", source.name())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "int | bool",
    "val v: int | 1 = 7; v",
    "val v: true | bool = true; v",
    "val v: (1 | 2) = 3; v",
    "val n = 7; val v: int | n = 7; v",
    "val make = () => 7; val v: int | make() = 7; v",
    "val U = int | 1; 0",
    "val n = 7; val U = n | bool; 0",
    "val make = () => 7; val U = int | make(); 0",
    "val U = int | bool; U",
    "val U = int | bool; val V = U; V",
    "val U = int | bool; val use = (p: int) => p; use(U)"
  })
  void given__value_union_member_or_runtime_use_of_a_type_alias__when__typed__then__type_value_mixing_is_rejected(
    final String code
  ) {
    assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val v: int | bool = \"wrong\"; v",
    "val use = (p: int | bool) => p; use(\"wrong\")",
    "val use = (): int | bool => \"wrong\"; use()",
    "val use = (): int | bool => { return \"wrong\"; }; use()",
    "val S = struct { val value: int | bool; }; new heap S { value = \"wrong\"; }",
    "val v: [;int | bool;1] = [\"wrong\"]; v",
    "val v: (int | bool, string) = (\"wrong\", \"label\"); v"
  })
  void given__value_outside_the_annotated_union__when__typed__then__initialization_argument_or_return_is_rejected(
    final String code
  ) {
    assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 | 2",
    "val a = 1; val b = 2; a | b",
    "val a = 1; val alias = a; alias | 2"
  })
  void given__integer_operands_or_runtime_value_aliases__when__typed__then__bitwise_or_is_not_promoted_to_a_union(
    final String code
  ) {
    final var root = Inf.codeToThir(code).root();
    final var binary = nodes(root, Hir.BinaryOperation.class).getFirst();
    assertAll(
      () -> assertEquals(Hir.BinaryOperationKind.BIT_OR, binary.kind()),
      () -> assertEquals(Ty.INTEGER, binary.ty()),
      () -> assertEquals(Ty.INTEGER, root.ty()),
      () -> assertTrue(nodes(root, Hir.Union.class).isEmpty())
    );
  }

  private static Hir.Union sourceUnion(final String annotation) {
    final var root = Inf.codeToHir("val v: %s = 7; v".formatted(annotation));
    return assertInstanceOf(Hir.Union.class, declaration(root, "v").typeAnnotation().expression());
  }

  private static Hir.Dec declaration(final Hir.Expression root, final String name) {
    return nodes(root, Hir.Dec.class).stream()
      .filter(candidate -> candidate.lexeme().name().equals(name)).findFirst().orElseThrow();
  }

  private static Hir.Assignment definition(final Hir.Expression root, final String name) {
    return nodes(root, Hir.Assignment.class).stream()
      .filter(candidate -> candidate.lhs() instanceof Hir.Dec declaration && declaration.lexeme().name().equals(name))
      .findFirst().orElseThrow();
  }

  private static <T extends Hir.Expression> List<T> nodes(final Hir.Expression root, final Class<T> nodeType) {
    final var found = new ArrayList<T>();
    new HirVisitor() {
      @Override
      public void visitChild(final Hir.Expression expression) {
        if (nodeType.isInstance(expression)) {
          found.add(nodeType.cast(expression));
        }
        HirVisitor.super.visitChild(expression);
      }
    }.visitChild(root);
    return found;
  }
}
