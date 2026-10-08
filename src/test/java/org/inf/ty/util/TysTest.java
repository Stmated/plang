package org.inf.ty.util;

import org.inf.hir.Hir;
import org.inf.ty.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Stream;

class TysTest {

  static Stream<Arguments> callableTargets() {
    final var function = new TyFn(new TyParam[]{new TyParam("value", Ty.INTEGER)}, false, Ty.INTEGER);
    final var signature = new Hir.FunctionSignature(
      new Hir.Parameter[0], false, new Hir.TyExpr(Ty.LONG), function
    );
    final var lambda = new Hir.Function(signature, new Hir.Return(new Hir.Literal("true", Ty.BOOLEAN)));
    final var declaration = new Hir.Dec(
      new Hir.Lexeme("f"), Hir.MutabilityKind.IMMUTABLE, new Hir.TyExpr(Ty.INFER), function
    );
    final var parameter = new Hir.Parameter(new Hir.Lexeme("f"), new Hir.TyExpr(Ty.INFER), false, function);
    final var factory = new TyFn(new TyParam[0], false, function);
    final var union = new TyUnion(new Ty[]{function, Ty.BOOLEAN});
    final var returning = new Hir.Return(new Hir.TyExpr(function));
    return Stream.of(
      Arguments.of(signature, function),
      Arguments.of(lambda, function),
      Arguments.of(declaration, function),
      Arguments.of(parameter, function),
      Arguments.of(new Hir.Identifier(declaration.lexeme(), declaration), function),
      Arguments.of(new Hir.Identifier(parameter.lexeme(), parameter), function),
      Arguments.of(new Hir.Block(lambda, Ty.DEADEND), function),
      Arguments.of(new Hir.Expressions(new Hir.Expression[]{returning, lambda}, Ty.DEADEND), function),
      Arguments.of(new Hir.Argument(null, new Hir.Block(lambda, Ty.DEADEND)), function),
      Arguments.of(new Hir.TupleEntry(null, lambda), function),
      Arguments.of(new Hir.Assignment(declaration, lambda, Ty.VOID), function),
      Arguments.of(new Hir.Convert(returning, function), function),
      Arguments.of(new Hir.Path(new Hir.Expression[0], Ty.DEADEND, function), function),
      Arguments.of(new Hir.ArrayAccess(returning, returning, Ty.DEADEND, function), function),
      Arguments.of(new Hir.Conditional(returning, lambda, lambda, Ty.DEADEND), function),
      Arguments.of(new Hir.Call(new Hir.TyExpr(factory), new Hir.Argument[0], false, Ty.DEADEND), function),
      Arguments.of(new Hir.TyExpr(union), union),
      Arguments.of(new Hir.Conditional(returning, lambda, new Hir.Literal("true", Ty.BOOLEAN), Ty.DEADEND), union),
      Arguments.of(returning, null),
      Arguments.of(new Hir.LoopBreak(lambda), null),
      Arguments.of(new Hir.DeadEnd(lambda), null),
      Arguments.of(new Hir.Block(returning, Ty.DEADEND), null),
      Arguments.of(new Hir.Expressions(new Hir.Expression[0], Ty.VOID), null),
      Arguments.of(new Hir.Identifier(new Hir.Lexeme("unresolved"), null), null),
      Arguments.of(new Hir.FunctionSignature(new Hir.Parameter[0], false, new Hir.TyExpr(function), null), null),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("f"), Hir.MutabilityKind.IMMUTABLE, new Hir.TyExpr(function)), null),
      Arguments.of(new Hir.Convert(returning, Ty.INTEGER), null),
      Arguments.of(new Hir.ArrayAccess(returning, returning, Ty.DEADEND, Ty.INTEGER), null),
      Arguments.of(new Hir.TyExpr(new TyStruct(new TyField[]{new TyField("f", function)}, true)), null)
    );
  }

  @ParameterizedTest
  @MethodSource("callableTargets")
  void given__resolved_result_wrappers__when__callable_information_is_queried__then__only_function_values_are_exposed(
    final Hir.Expression target, final Ty expected
  ) {
    final var completion = target.ty();
    final var signature = expected instanceof TyFn function ? function : null;
    Assertions.assertAll(
      () -> Assertions.assertEquals(expected, Tys.getCallableValueTy(target)),
      () -> Assertions.assertSame(signature, Tys.getCallableSignature(target)),
      () -> Assertions.assertSame(completion, target.ty())
    );
  }

  @Test
  void given__higher_order_call__when__resolved_signature_changes__then__callable_query_uses_the_latest_return_signature() {
    final var original = new TyFn(new TyParam[0], false, Ty.INTEGER);
    final var updated = new TyFn(new TyParam[0], false, Ty.BOOLEAN);
    final var signature = new Hir.FunctionSignature(
      new Hir.Parameter[0], false, new Hir.TyExpr(Ty.INFER), new TyFn(new TyParam[0], false, original)
    );
    final var call = new Hir.Call(signature, new Hir.Argument[0], false, Ty.DEADEND);
    Assertions.assertSame(original, Tys.getCallableSignature(call));
    signature.ty(signature.ty().toBuilder().returnTy(updated).build());
    Assertions.assertAll(
      () -> Assertions.assertSame(updated, Tys.getCallableSignature(call)),
      () -> Assertions.assertSame(updated, Tys.getCallableValueTy(call)),
      () -> Assertions.assertSame(Ty.DEADEND, call.ty()),
      () -> Assertions.assertSame(Ty.INFER, signature.returnTypeAnnotation().ty())
    );
  }

  static Stream<Arguments> bindingTargets() {
    final var declaration = new Hir.Dec(
      new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new Hir.TyExpr(Ty.LONG), Ty.INTEGER
    );
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), new Hir.TyExpr(Ty.LONG), false, Ty.INTEGER);
    final var function = new TyFn(new TyParam[0], false, Ty.INTEGER);
    return Stream.of(
      Arguments.of(declaration, Ty.INTEGER),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("unresolved"), Hir.MutabilityKind.IMMUTABLE, new Hir.TyExpr(Ty.INTEGER)), null),
      Arguments.of(new Hir.Identifier(declaration.lexeme(), declaration), Ty.INTEGER),
      Arguments.of(parameter, Ty.INTEGER),
      Arguments.of(new Hir.Identifier(parameter.lexeme(), parameter), Ty.INTEGER),
      Arguments.of(new Hir.Parameter(new Hir.Lexeme("unresolved"), new Hir.TyExpr(Ty.INTEGER), false, null), null),
      Arguments.of(new Hir.FunctionSignature(new Hir.Parameter[0], false, new Hir.TyExpr(Ty.INTEGER), function), function),
      Arguments.of(new Hir.Path(new Hir.Expression[0], Ty.DEADEND, Ty.LONG), Ty.LONG)
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
    final var target = new Hir.TyExpr(Ty.INTEGER);
    final var assignment = new Hir.Assignment(target, target, Ty.VOID);
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.INTEGER, Tys.getBindingTy(target)),
      () -> Assertions.assertSame(Ty.INTEGER, Tys.getAssignmentContextTy(target)),
      () -> Assertions.assertSame(Ty.VOID, Tys.getBindingTy(assignment))
    );
  }

  static Stream<Arguments> assignmentContexts() {
    final var declaration = new Hir.Dec(
      new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new Hir.TyExpr(Ty.LONG), Ty.INTEGER
    );
    final var partial = new TyValueArray(Ty.INFER, 2);
    return Stream.of(
      Arguments.of(declaration, Ty.LONG),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new Hir.TyExpr(Ty.INFER), Ty.INTEGER), null),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new Hir.TyExpr(partial),
        new TyValueArray(Ty.INTEGER, 2)), partial),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, null, Ty.INTEGER), null),
      Arguments.of(new Hir.Identifier(declaration.lexeme(), declaration), Ty.INTEGER),
      Arguments.of(new Hir.Parameter(new Hir.Lexeme("p"), new Hir.TyExpr(Ty.INFER), false, Ty.INTEGER), Ty.INTEGER),
      Arguments.of(new Hir.Path(new Hir.Expression[0], Ty.DEADEND, Ty.LONG), Ty.LONG)
    );
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
    final var annotation = declared == null ? null : new Hir.TyExpr(declared);
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
