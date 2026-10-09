package org.inf.ty.util;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.hir.passes.HirTyCommonVisitorPass;
import org.inf.ty.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.stream.Stream;

class TysTest {

  static Stream<Arguments> callableTargets() {
    final var integer = Tys.fromString("int", new MachineTarget(64));
    final var function = new TyFn(new TyParam[]{new TyParam("value", integer)}, false, integer);
    final var prefix = """
      val fn = (value: int): int => value;
      val factory = (flag: bool) => fn;
      """;
    return Stream.of(
      Arguments.of(prefix + "fn", function),
      Arguments.of(prefix + "(value: int): int => value", function),
      Arguments.of(prefix + "{ fn }", function),
      Arguments.of(prefix + "{ return true; fn; }", function),
      Arguments.of(prefix + "var stored = fn; stored = fn", function),
      Arguments.of(prefix + "(callback = fn,).callback", function),
      Arguments.of(prefix + "[fn][0]", function),
      Arguments.of(prefix + "if ({ return true; }) then fn else fn", function),
      Arguments.of(prefix + "factory({ return true; })", function),
      Arguments.of(prefix + "if (true) then fn else true", Tys.union(function, Ty.BOOLEAN)),
      Arguments.of(prefix + "{ return fn; }", null),
      Arguments.of(prefix + "{ val n = 1; }", null),
      Arguments.of(prefix + "(callback = fn,)", null),
      Arguments.of(prefix + "[fn]", null),
      Arguments.of(prefix + "7", null)
    );
  }

  @ParameterizedTest
  @MethodSource("callableTargets")
  void given__resolved_result_wrappers__when__callable_information_is_queried__then__only_function_values_are_exposed(
    final String code, final Ty expected
  ) {
    final var returns = new ArrayList<Hir.Return>();
    Inf.codeToThir(code).root().visit(new HirVisitor() {
      @Override
      public void visitReturn(final Hir.Return expression) {
        returns.add(expression);
      }
    });
    final var target = returns.getLast().expression();
    final var completion = target.ty();
    final var signature = expected instanceof TyFn function ? function : null;
    Assertions.assertAll(
      () -> Assertions.assertEquals(expected, Tys.getCallableValueTy(target)),
      () -> Assertions.assertEquals(signature, Tys.getCallableSignature(target)),
      () -> Assertions.assertSame(completion, target.ty())
    );
  }

  @Test
  void given__callable_parameter_reference__when__queried__then__the_resolved_parameter_signature_is_used() {
    final var parameters = new ArrayList<Hir.Parameter>();
    final var references = new ArrayList<Hir.Identifier>();
    Inf.codeToThir("""
      val Fn = (value: int): int;
      val use = (callback: Fn) => callback;
      use
      """).root().visit(new HirVisitor() {
      @Override
      public void visitParameter(final Hir.Parameter parameter) {
        if (parameter.lexeme().name().equals("callback")) {
          parameters.add(parameter);
        }
      }

      @Override
      public void visitIdentifier(final Hir.Identifier identifier) {
        if (identifier.name().equals("callback")) {
          references.add(identifier);
        }
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, parameters.size()),
      () -> Assertions.assertEquals(1, references.size())
    );
    final var parameter = parameters.getFirst();
    final var reference = references.getFirst();
    Assertions.assertAll(
      () -> Assertions.assertSame(parameter, reference.target()),
      () -> Assertions.assertSame(Ty.VOID, parameter.ty()),
      () -> Assertions.assertInstanceOf(TyFn.class, parameter.resolvedTy()),
      () -> Assertions.assertSame(parameter.resolvedTy(), Tys.getCallableSignature(reference)),
      () -> Assertions.assertSame(parameter.resolvedTy(), Tys.getCallableValueTy(parameter))
    );
  }

  @Test
  void given__higher_order_call__when__resolved_signature_changes__then__callable_query_uses_the_latest_return_signature() {
    final var factories = new ArrayList<Hir.Function>();
    final var replacements = new ArrayList<Hir.Function>();
    final var calls = new ArrayList<Hir.Call>();
    final var root = Inf.codeToThir("""
      val integer = () => 7;
      val boolean = () => true;
      val factory = (flag: bool) => integer;
      val replacement = (flag: bool) => boolean;
      factory({ return true; })
      """).root();
    root.visit(new HirVisitor() {
      @Override
      public void visitAssignment(final Hir.Assignment assignment) {
        if (assignment.lhs() instanceof Hir.Dec declaration && assignment.rhs() instanceof Hir.Function function) {
          if (declaration.lexeme().name().equals("factory")) {
            factories.add(function);
          } else if (declaration.lexeme().name().equals("replacement")) {
            replacements.add(function);
          }
        }
        HirVisitor.super.visitAssignment(assignment);
      }

      @Override
      public void visitCall(final Hir.Call call) {
        calls.add(call);
        HirVisitor.super.visitCall(call);
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, factories.size()),
      () -> Assertions.assertEquals(1, replacements.size()),
      () -> Assertions.assertEquals(1, calls.size())
    );
    final var factory = factories.getFirst();
    final var original = factory.ty().returnTy();
    final var updated = replacements.getFirst().ty().returnTy();
    final var call = calls.getFirst();
    Assertions.assertSame(original, Tys.getCallableSignature(call));
    factory.body(replacements.getFirst().body());
    HirTyCommonVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertEquals(updated, Tys.getCallableSignature(call)),
      () -> Assertions.assertNotEquals(original, Tys.getCallableSignature(call)),
      () -> Assertions.assertSame(factory.ty().returnTy(), Tys.getCallableSignature(call)),
      () -> Assertions.assertSame(factory.ty().returnTy(), Tys.getCallableValueTy(call)),
      () -> Assertions.assertSame(Ty.DEADEND, call.ty()),
      () -> Assertions.assertSame(Ty.INFER, factory.signature().returnTypeAnnotation().ty())
    );
  }

  static Stream<Arguments> bindingTargets() {
    final var declaration = new Hir.Dec(
      new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new Hir.DynamicTy(Ty.LONG), Ty.INTEGER
    );
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), new Hir.DynamicTy(Ty.LONG), false, Ty.INTEGER);
    final var function = new TyFn(new TyParam[0], false, Ty.INTEGER);
    return Stream.of(
      Arguments.of(declaration, Ty.INTEGER),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("unresolved"), Hir.MutabilityKind.IMMUTABLE, new Hir.DynamicTy(Ty.INTEGER)), null),
      Arguments.of(new Hir.Identifier(declaration.lexeme().name(), declaration), Ty.INTEGER),
      Arguments.of(parameter, Ty.INTEGER),
      Arguments.of(new Hir.Identifier(parameter.lexeme().name(), parameter), Ty.INTEGER),
      Arguments.of(new Hir.Parameter(new Hir.Lexeme("unresolved"), new Hir.DynamicTy(Ty.INTEGER), false, null), null),
      Arguments.of(new Hir.FunctionSignature(new Hir.Parameter[0], false, new Hir.DynamicTy(Ty.INTEGER), function), function),
      Arguments.of(noncontinuingMember(), Ty.LONG)
    );
  }

  @ParameterizedTest
  @MethodSource("bindingTargets")
  void given__binding_context__when__type_is_queried__then__resolved_or_compatible_target_type_is_returned(
    final Hir.Expression target, final Ty expected
  ) {
    Assertions.assertSame(expected, Tys.getBindingTy(target));
  }

  @Test
  void given__expression_binding_target__when__queried__then__completion_is_read() {
    final var assignments = new ArrayList<Hir.Assignment>();
    Inf.codeToThir("var n = 1; n = 2").root().visit(new HirVisitor() {
      @Override
      public void visitAssignment(final Hir.Assignment assignment) {
        if (assignment.lhs() instanceof Hir.Identifier) {
          assignments.add(assignment);
        }
        HirVisitor.super.visitAssignment(assignment);
      }
    });
    Assertions.assertEquals(1, assignments.size());
    final var assignment = assignments.getFirst();
    final var target = assignment.lhs();
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.INTEGER, Tys.getBindingTy(target)),
      () -> Assertions.assertSame(Ty.INTEGER, Tys.getAssignmentContextTy(target)),
      () -> Assertions.assertSame(Ty.VOID, Tys.getBindingTy(assignment))
    );
  }

  static Stream<Arguments> assignmentContexts() {
    final var declaration = new Hir.Dec(
      new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new Hir.DynamicTy(Ty.LONG), Ty.INTEGER
    );
    final var partial = new TyValueArray(Ty.INFER, 2);
    return Stream.of(
      Arguments.of(declaration, Ty.LONG),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new Hir.DynamicTy(Ty.INFER), Ty.INTEGER), null),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new Hir.DynamicTy(partial),
        new TyValueArray(Ty.INTEGER, 2)), partial),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new Hir.DynamicTy(Ty.INFER), Ty.INTEGER), null),
      Arguments.of(new Hir.Identifier(declaration.lexeme().name(), declaration), Ty.INTEGER),
      Arguments.of(new Hir.Parameter(new Hir.Lexeme("p"), new Hir.DynamicTy(Ty.INFER), false, Ty.INTEGER), Ty.INTEGER),
      Arguments.of(noncontinuingMember(), Ty.LONG)
    );
  }

  private static Hir.DotAccess noncontinuingMember() {
    final var members = new ArrayList<Hir.DotAccess>();
    Inf.codeToThir("""
      val S = struct { val value: long; };
      val s = new heap S { value = 7; };
      ({ return true; s; }).value
      """).root().visit(new HirVisitor() {
      @Override
      public void visitDotAccess(final Hir.DotAccess expression) {
        members.add(expression);
      }
    });
    return members.getFirst();
  }

  @ParameterizedTest
  @MethodSource("assignmentContexts")
  void given__assignment_target__when__context_is_queried__then__source_constraints_are_separate_from_binding_types(
    final Hir.Expression target, final Ty expected
  ) {
    Assertions.assertSame(expected, Tys.getAssignmentContextTy(target));
  }

  static Stream<Arguments> returnContexts() {
    final var inferredArray = new TyValueArray(Ty.INFER, 2);
    final var resolvedArray = new TyValueArray(Ty.INTEGER, 2);
    final var expectedArray = new TyValueArray(Ty.BOOLEAN, 2);
    return Stream.of(
      Arguments.of(Ty.LONG, Ty.INTEGER, Ty.LONG, Ty.BOOLEAN, Ty.LONG),
      Arguments.of(Ty.INFER, Ty.INTEGER, null, Ty.BOOLEAN, Ty.BOOLEAN),
      Arguments.of(inferredArray, resolvedArray, inferredArray, expectedArray, expectedArray),
      Arguments.of(Ty.INFER, null, null, Ty.BOOLEAN, Ty.BOOLEAN),
      Arguments.of(inferredArray, null, inferredArray, expectedArray, expectedArray),
      Arguments.of(null, null, null, Ty.BOOLEAN, Ty.BOOLEAN)
    );
  }

  @ParameterizedTest
  @MethodSource("returnContexts")
  void given__return_context__when__queried__then__declared_constraints_and_inferred_types_are_selected_separately(
    final Ty declared, final Ty resolved, final Ty expected, final Ty useSiteContext, final Ty useSiteExpected
  ) {
    final var annotation = new Hir.DynamicTy(declared == null ? Ty.INFER : declared);
    final var signature = new Hir.FunctionSignature(
      new Hir.Parameter[0], false, annotation, resolved == null ? null : new TyFn(new TyParam[0], false, resolved)
    );
    final var original = signature.ty();
    Assertions.assertAll(
      () -> Assertions.assertSame(expected, Tys.getFunctionReturnContextTy(signature)),
      () -> Assertions.assertSame(useSiteExpected, Tys.getFunctionReturnContextTy(signature, useSiteContext)),
      () -> Assertions.assertSame(annotation, signature.returnTypeAnnotation()),
      () -> Assertions.assertSame(original, signature.ty())
    );
  }

  static Stream<Arguments> inferredMembers() {
    return Stream.of(
      Arguments.of(null, true),
      Arguments.of(Ty.INFER, true),
      Arguments.of(Ty.INTEGER, false),
      Arguments.of(tuple(Ty.INTEGER, new TyValueArray(Ty.INFER, 2)), true),
      Arguments.of(tuple(Ty.INTEGER, Ty.BOOLEAN), false),
      Arguments.of(new TyValueArray(Ty.INFER, 1), true),
      Arguments.of(new TyFn(new TyParam[]{new TyParam("v", Ty.INFER)}, false, Ty.INTEGER), true),
      Arguments.of(new TyFn(new TyParam[0], false, new TyValueArray(Ty.INFER, 2)), true),
      Arguments.of(new TyFn(new TyParam[]{new TyParam("v", Ty.INTEGER)}, false, Ty.INTEGER), false),
      Arguments.of(new TyUnion(new Ty[]{Ty.INTEGER, new TyValueArray(Ty.INFER, 0)}), true),
      Arguments.of(new TyUnion(new Ty[]{Ty.INTEGER, Ty.BOOLEAN}), false)
    );
  }

  @ParameterizedTest
  @MethodSource("inferredMembers")
  void given__compound_type__when__checked_for_unresolved_members__then__nested_inference_is_detected(
    final Ty type, final boolean expected
  ) {
    Assertions.assertEquals(expected, Tys.containsInferred(type));
  }

  private static TyStruct tuple(Ty... types) {
    return new TyStruct(Arrays.stream(types).map(type -> new TyField(null, type)).toArray(TyField[]::new));
  }

  static Stream<Arguments> numericJoins() {
    final var explicitInt = Ty.INTEGER.toBuilder().width(new BitWidth(32, true)).build();
    final var wideFloat = Ty.FLOAT.toBuilder().width(new BitWidth(64, false)).build();
    final var preciseFloat = Ty.FLOAT.toBuilder().precision(10).build();
    final var widePreciseFloat = wideFloat.toBuilder().precision(10).build();
    final var explicitFloat = Ty.FLOAT.toBuilder().width(new BitWidth(32, true)).build();
    return Stream.of(
      Arguments.of(Ty.INTEGER, Ty.INTEGER.toBuilder().build(), Ty.INTEGER, new TyDiffKind[0]),
      Arguments.of(Ty.INTEGER, explicitInt, explicitInt, new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXPLICIT}),
      Arguments.of(Ty.SHORT, Ty.INTEGER, Ty.INTEGER, new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXT}),
      Arguments.of(Ty.SHORT, explicitInt, explicitInt, new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXT}),
      Arguments.of(Ty.INTEGER, Ty.UINTEGER, Ty.INTEGER, new TyDiffKind[]{TyDiffKind.DIFF_SIGNED}),
      Arguments.of(Ty.INTEGER, Ty.INTEGER_HEX, Ty.INTEGER, new TyDiffKind[]{TyDiffKind.DIFF_RADIX}),
      Arguments.of(Ty.INTEGER_HEX, Ty.ULONG, Ty.INTEGER, new TyDiffKind[]{TyDiffKind.DIFF_SIGNED}),
      Arguments.of(Ty.INTEGER, Ty.FLOAT, Ty.FLOAT, new TyDiffKind[]{TyDiffKind.DIFF_PRECISION_EXT}),
      Arguments.of(Ty.LONG, Ty.FLOAT, Ty.FLOAT, new TyDiffKind[]{TyDiffKind.DIFF_PRECISION_EXT}),
      Arguments.of(Ty.INTEGER, Ty.DECIMAL, Ty.DECIMAL, new TyDiffKind[]{TyDiffKind.DIFF_PRECISION_EXT}),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().build(), Ty.FLOAT, new TyDiffKind[0]),
      Arguments.of(Ty.FLOAT, wideFloat, wideFloat, new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXT}),
      Arguments.of(Ty.FLOAT, preciseFloat, preciseFloat, new TyDiffKind[]{TyDiffKind.DIFF_PRECISION_EXT}),
      Arguments.of(Ty.FLOAT, widePreciseFloat, widePreciseFloat,
        new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXT, TyDiffKind.DIFF_PRECISION_EXT}),
      Arguments.of(Ty.FLOAT, explicitFloat, explicitFloat, new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXPLICIT}),
      Arguments.of(Ty.FLOAT, explicitFloat.toBuilder().precision(10).build(),
        explicitFloat.toBuilder().precision(10).build(), new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXPLICIT}),
      Arguments.of(Ty.FLOAT, Ty.DECIMAL, null, new TyDiffKind[]{TyDiffKind.INCOMPATIBLE}),
      Arguments.of(Ty.DECIMAL, new TyValueNumberScaled(Ty.DECIMAL.width(), 10, true, EnumSet.noneOf(TyFlags.class)),
        null, new TyDiffKind[]{TyDiffKind.INCOMPATIBLE}),
      Arguments.of(Ty.INTEGER, Ty.BOOLEAN, null, new TyDiffKind[]{TyDiffKind.INCOMPATIBLE}),
      Arguments.of(Ty.INTEGER, Ty.UNKNOWN, Ty.UNKNOWN, new TyDiffKind[]{TyDiffKind.UNKNOWN})
    );
  }

  @ParameterizedTest
  @MethodSource("numericJoins")
  void given__numeric_types__when__common_type_is_selected__then__existing_result_and_diagnostics_are_preserved(
    Ty a, Ty b, Ty expected, TyDiffKind[] differences
  ) {
    final var forward = Tys.getCommonDenominator(a, b);
    final var reverse = Tys.getCommonDenominator(b, a);
    Assertions.assertAll(
      () -> Assertions.assertEquals(expected, forward.ty()),
      () -> Assertions.assertArrayEquals(differences, forward.diffs()),
      () -> Assertions.assertEquals(expected, reverse.ty()),
      () -> Assertions.assertArrayEquals(differences, reverse.diffs())
    );
  }

  static Stream<Ty> identicalTypes() {
    return Stream.of(
      Ty.INTEGER,
      Ty.DECIMAL,
      Ty.BOOLEAN,
      Ty.UNKNOWN,
      new TyOpaque()
    );
  }

  @ParameterizedTest
  @MethodSource("identicalTypes")
  void given__identical_type_reference__when__joined__then__original_type_is_retained(Ty type) {
    final var result = Tys.getCommonDenominator(type, type);
    Assertions.assertAll(
      () -> Assertions.assertSame(type, result.ty()),
      () -> Assertions.assertArrayEquals(new TyDiffKind[0], result.diffs())
    );
  }

  static Stream<Arguments> unchangedSelectionRules() {
    return Stream.of(
      Arguments.of(Ty.INTEGER, Ty.INTEGER.toBuilder().flags(EnumSet.of(TyFlags.CONSTANT)).build()),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().signed(false).build()),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().kind(RealKind.DOUBLE).build()),
      Arguments.of(Ty.FLOAT, Ty.FLOAT.toBuilder().flags(EnumSet.of(TyFlags.IMMUTABLE)).build()),
      Arguments.of(new TyOpaque(), new TyOpaque())
    );
  }

  @ParameterizedTest
  @MethodSource("unchangedSelectionRules")
  void given__existing_left_operand_selection__when__joined__then__refactoring_does_not_change_policy(Ty a, Ty b) {
    final var forward = Tys.getCommonDenominator(a, b);
    final var reverse = Tys.getCommonDenominator(b, a);
    Assertions.assertAll(
      () -> Assertions.assertSame(a, forward.ty()),
      () -> Assertions.assertSame(b, reverse.ty()),
      () -> Assertions.assertArrayEquals(new TyDiffKind[0], forward.diffs()),
      () -> Assertions.assertArrayEquals(new TyDiffKind[0], reverse.diffs())
    );
  }

  @Test
  void given__width_change_with_flags__when__joined__then__existing_flag_merge_is_preserved() {
    final var narrow = Ty.SHORT.toBuilder().flags(EnumSet.of(TyFlags.CONSTANT)).build();
    final var wide = Ty.INTEGER.toBuilder().flags(EnumSet.of(TyFlags.MUTABLE)).build();
    final var expected = Ty.INTEGER.toBuilder().flags(EnumSet.of(TyFlags.MUTABLE)).build();
    final var result = Tys.getCommonDenominator(narrow, wide);
    Assertions.assertAll(
      () -> Assertions.assertEquals(expected, result.ty()),
      () -> Assertions.assertArrayEquals(new TyDiffKind[]{TyDiffKind.DIFF_WIDTH_EXT}, result.diffs())
    );
  }

  @Test
  void given__equivalent_tuple_layouts__when__joined__then__no_spurious_union() {
    final var inferred = tuple(Ty.INTEGER, Ty.BOOLEAN);
    final var explicit = tuple(Ty.INTEGER.toBuilder().width(new BitWidth(32, true)).build(), Ty.BOOLEAN);
    Assertions.assertAll(
      () -> Assertions.assertEquals(inferred, Tys.union(inferred, explicit)),
      () -> Assertions.assertEquals(explicit, Tys.union(explicit, inferred)),
      () -> Assertions.assertInstanceOf(TyUnion.class, Tys.union(inferred, tuple(Ty.LONG, Ty.BOOLEAN)))
    );
  }

  @Test
  void given__tuple_decimal_flag_difference__when__common_type_is_selected__then__types_are_not_equivalent() {
    final var a = tuple(Ty.DECIMAL);
    final var b = tuple(new TyValueNumberScaled(Ty.DECIMAL.width(), 10, true, EnumSet.of(TyFlags.CONSTANT)));
    final var result = Tys.getCommonDenominator(a, b);
    Assertions.assertAll(
      () -> Assertions.assertNull(result.ty()),
      () -> Assertions.assertArrayEquals(new TyDiffKind[]{TyDiffKind.INCOMPATIBLE}, result.diffs())
    );
  }
}
