package org.inf.ty.util;

import org.inf.Inf;
import org.inf.hir.DynamicTyDefault;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.hir.passes.HirTyCommonVisitorPass;
import org.inf.hir.util.Hirs;
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

  static Stream<Arguments> inspectedTypes() {
    return Stream.of(
      Arguments.of(Ty.INTEGER, Ty.INTEGER, Ty.INTEGER),
      Arguments.of(Ty.DOUBLE, Ty.DOUBLE, Ty.DOUBLE),
      Arguments.of(Ty.BOOLEAN, null, null),
      Arguments.of(Ty.INFER, null, null),
      Arguments.of(Ty.INVALID, null, null),
      Arguments.of(Ty.UNKNOWN, null, null),
      Arguments.of(Ty.VOID, null, null),
      Arguments.of(Ty.DEADEND, null, null),
      Arguments.of(Tys.withCompletion(Ty.DEADEND, Ty.INTEGER), null, Ty.INTEGER),
      Arguments.of(Tys.withCompletion(null, Ty.INTEGER), null, Ty.INTEGER),
      Arguments.of(Tys.withCompletion(Ty.INTEGER, Ty.DOUBLE), Ty.INTEGER, Ty.DOUBLE),
      Arguments.of(null, null, null)
    );
  }

  @ParameterizedTest
  @MethodSource("inspectedTypes")
  void given__type_or_missing_resolution__when__projected__then__the_requested_context_selects_the_kind(
    final Ty type, final TyValueNumber completion, final TyValueNumber structural
  ) {
    final var deadend = Tys.completionTy(type) == Ty.DEADEND;
    Assertions.assertAll(
      () -> Assertions.assertSame(completion, Tys.asCompletion(type, TyValueNumber.class)),
      () -> Assertions.assertSame(structural, Tys.asStructural(type, TyValueNumber.class)),
      () -> Assertions.assertEquals(deadend, Tys.isDeadend(type))
    );
    if (type != null) {
      Assertions.assertAll(
        () -> Assertions.assertSame(completion, type.asCompletion(TyValueNumber.class)),
        () -> Assertions.assertSame(structural, type.asStructural(TyValueNumber.class)),
        () -> Assertions.assertEquals(deadend, type.isDeadend())
      );
    }
  }

  @Test
  void given__completion_and_structure__when__paired__then__projections_are_explicit_and_normalized() {
    final var pair = Tys.withCompletion(Ty.DEADEND, Ty.INTEGER);
    final var unresolved = Tys.withCompletion(null, Ty.INFER);
    final var explicit = Ty.INTEGER.toBuilder().width(Ty.BW_EXPLICIT_32).build();
    final var constant = new TyValueNumberScaled(Ty.DECIMAL.width(), Ty.DECIMAL.scale(), Ty.DECIMAL.signed(),
      EnumSet.of(TyFlags.CONSTANT));
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.INTEGER, Tys.withCompletion(Ty.INTEGER, Ty.INTEGER)),
      () -> Assertions.assertSame(Ty.DEADEND, Tys.withCompletion(Ty.DEADEND, null)),
      () -> Assertions.assertNull(Tys.withCompletion(null, null)),
      () -> Assertions.assertSame(Ty.DEADEND, pair.completionTy()),
      () -> Assertions.assertSame(Ty.INTEGER, pair.structuralTy()),
      () -> Assertions.assertTrue(pair.isDeadend()),
      () -> Assertions.assertNull(pair.asCompletion(TyValueNumberInteger.class)),
      () -> Assertions.assertSame(Ty.INTEGER, pair.asStructural(TyValueNumberInteger.class)),
      () -> Assertions.assertSame(Ty.DEADEND, Ty.DEADEND.asCompletion(Ty.TyNamed.class)),
      () -> Assertions.assertNull(Ty.DEADEND.asStructural(Ty.TyNamed.class)),
      () -> Assertions.assertSame(pair, Tys.withCompletion(pair, pair)),
      () -> Assertions.assertSame(explicit, Tys.withCompletion(explicit, explicit)),
      () -> Assertions.assertSame(explicit, Tys.withCompletion(Ty.INTEGER, explicit).structuralTy()),
      () -> Assertions.assertSame(constant, Tys.withCompletion(Ty.DECIMAL, constant).structuralTy()),
      () -> Assertions.assertNotEquals(Tys.withCompletion(Ty.DEADEND, Ty.DECIMAL),
        Tys.withCompletion(Ty.DEADEND, constant)),
      () -> Assertions.assertFalse(TypeComparison.same(pair, Ty.DEADEND)),
      () -> Assertions.assertNull(unresolved.completionTy()),
      () -> Assertions.assertSame(Ty.INFER, unresolved.structuralTy()),
      () -> Assertions.assertTrue(Tys.containsInferred(unresolved)),
      () -> Assertions.assertEquals(Tys.union(Ty.INTEGER, Ty.VOID), Tys.union(Ty.INTEGER, pair, Ty.VOID))
    );
  }

  @Test
  void given__independent_type_info__when__normalized__then__only_absent_constraints_allow_collapse() {
    final var omitted = Tys.typeInfo(Ty.INTEGER, Ty.INTEGER, Ty.INFER);
    final var explicit = Ty.INTEGER.toBuilder().width(Ty.BW_EXPLICIT_32).build();
    final var constrained = Tys.typeInfo(explicit, explicit, explicit);
    final var nested = Tys.typeInfo(Ty.INTEGER, Ty.LONG, Ty.BOOLEAN);
    final var constant = new TyValueNumberScaled(Ty.DECIMAL.width(), Ty.DECIMAL.scale(), Ty.DECIMAL.signed(),
      EnumSet.of(TyFlags.CONSTANT));
    final var flagged = Tys.typeInfo(Ty.DECIMAL, constant, Ty.INFER);
    Assertions.assertAll(
      () -> Assertions.assertNull(Tys.typeInfo(null, null, null)),
      () -> Assertions.assertSame(Ty.INTEGER, Tys.typeInfo(Ty.INTEGER, Ty.INTEGER, null)),
      () -> Assertions.assertSame(Ty.DEADEND, Tys.typeInfo(Ty.DEADEND, Ty.DEADEND, null)),
      () -> Assertions.assertSame(explicit, Tys.typeInfo(explicit, explicit, null)),
      () -> Assertions.assertSame(Ty.INTEGER, Tys.typeInfo(Ty.INTEGER, Ty.INTEGER.toBuilder().build(), null)),
      () -> Assertions.assertInstanceOf(TyInfo.class, omitted),
      () -> Assertions.assertSame(omitted, Tys.typeInfo(Ty.INTEGER, Ty.INTEGER, Ty.INFER)),
      () -> Assertions.assertSame(explicit, Tys.completionTy(constrained)),
      () -> Assertions.assertSame(explicit, Tys.structuralTy(constrained)),
      () -> Assertions.assertSame(explicit, Tys.declaredConstraintTy(constrained)),
      () -> Assertions.assertSame(constant, Tys.structuralTy(flagged)),
      () -> Assertions.assertNotEquals(flagged, Tys.typeInfo(Ty.DECIMAL, Ty.DECIMAL, Ty.INFER)),
      () -> Assertions.assertNotEquals(Tys.typeInfo(Ty.INTEGER, explicit, Ty.INFER),
        Tys.typeInfo(Ty.INTEGER, Ty.INTEGER, Ty.INFER)),
      () -> Assertions.assertSame(Ty.INTEGER, Tys.typeInfo(nested, Ty.INTEGER, null)),
      () -> Assertions.assertEquals(Tys.typeInfo(Ty.INTEGER, Ty.LONG, null), Tys.typeInfo(nested, nested, null)),
      () -> Assertions.assertSame(Ty.INTEGER, Tys.declaredConstraintTy(Tys.typeInfo(Ty.VOID, Ty.INTEGER, nested))),
      () -> Assertions.assertNull(Tys.declaredConstraintTy(Ty.INTEGER)),
      () -> Assertions.assertNull(Tys.structuralTy(null)),
      () -> Assertions.assertNull(Tys.declaredConstraintTy(null))
    );
  }

  @Test
  void given__source_constraint__when__structure_and_annotation_refresh__then__only_selected_answers_change() {
    final var constraint = new TyValueArray(Ty.INFER, 2);
    final var initial = new TyValueArray(Ty.INTEGER, 2);
    final var updated = new TyValueArray(Ty.BOOLEAN, 2);
    final var info = Tys.typeInfo(Ty.VOID, initial, constraint);
    final var refined = Tys.withStructural(info, updated);
    final var diverging = Tys.typeInfo(Ty.DEADEND, updated, Tys.declaredConstraintTy(refined));
    final var refreshed = Tys.withDeclaredConstraint(diverging, new TyValueArray(Ty.BOOLEAN, 2));
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.VOID, Tys.completionTy(refined)),
      () -> Assertions.assertSame(updated, Tys.structuralTy(refined)),
      () -> Assertions.assertSame(constraint, Tys.declaredConstraintTy(refined)),
      () -> Assertions.assertSame(refined, Tys.withStructural(refined, updated)),
      () -> Assertions.assertSame(Ty.DEADEND, Tys.completionTy(diverging)),
      () -> Assertions.assertSame(updated, Tys.structuralTy(diverging)),
      () -> Assertions.assertSame(constraint, Tys.declaredConstraintTy(diverging)),
      () -> Assertions.assertSame(Ty.DEADEND, Tys.completionTy(refreshed)),
      () -> Assertions.assertSame(updated, Tys.structuralTy(refreshed)),
      () -> Assertions.assertEquals(updated, Tys.declaredConstraintTy(refreshed)),
      () -> Assertions.assertSame(initial, Tys.structuralTy(info)),
      () -> Assertions.assertSame(constraint, Tys.declaredConstraintTy(info)),
      () -> Assertions.assertSame(updated, Tys.withDeclaredConstraint(Tys.typeInfo(updated, updated, constraint), null)),
      () -> Assertions.assertEquals(Tys.typeInfo(null, Ty.INTEGER, null), Tys.withStructural(null, Ty.INTEGER)),
      () -> Assertions.assertEquals(Tys.typeInfo(null, null, Ty.INFER), Tys.withDeclaredConstraint(null, Ty.INFER))
    );
  }

  @Test
  void given__missing_structure_and_an_omitted_constraint__when__refined__then__completion_and_constraint_are_preserved() {
    final var info = Tys.typeInfo(Ty.VOID, null, Ty.INFER);
    final var refined = Tys.withStructural(info, Ty.INTEGER);
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.VOID, Tys.completionTy(info)),
      () -> Assertions.assertNull(Tys.structuralTy(info)),
      () -> Assertions.assertFalse(Tys.isDeadend(info)),
      () -> Assertions.assertTrue(Tys.containsInferred(info)),
      () -> Assertions.assertSame(Ty.VOID, Tys.completionTy(refined)),
      () -> Assertions.assertSame(Ty.INTEGER, Tys.structuralTy(refined)),
      () -> Assertions.assertFalse(Tys.containsInferred(refined)),
      () -> Assertions.assertSame(Ty.INFER, Tys.declaredConstraintTy(refined))
    );
  }

  @Test
  void given__constraint_bearing_inputs__when__expression_projections_are_composed__then__source_requirements_do_not_leak() {
    final var info = Tys.typeInfo(Ty.INTEGER, Ty.LONG, Ty.BOOLEAN);
    final var composed = Tys.withCompletion(info, info);
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.INTEGER, Tys.completionTy(composed)),
      () -> Assertions.assertSame(Ty.LONG, Tys.structuralTy(composed)),
      () -> Assertions.assertNull(Tys.declaredConstraintTy(composed)),
      () -> Assertions.assertSame(Tys.withCompletion(Ty.INTEGER, Ty.LONG), composed),
      () -> Assertions.assertSame(Ty.BOOLEAN, Tys.declaredConstraintTy(info))
    );
  }

  static Stream<Arguments> callableTargets() {
    final var integer = Tys.fromString("int", MachineTarget.T64);
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
      () -> Assertions.assertEquals(expected, Hirs.getCallableValueTy(target)),
      () -> Assertions.assertEquals(signature, Hirs.getCallableSignature(target)),
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
      () -> Assertions.assertSame(Ty.VOID, parameter.ty().completionTy()),
      () -> Assertions.assertInstanceOf(TyFn.class, parameter.ty().structuralTy()),
      () -> Assertions.assertSame(parameter.ty().structuralTy(), Hirs.getCallableSignature(reference)),
      () -> Assertions.assertSame(parameter.ty().structuralTy(), Hirs.getCallableValueTy(parameter))
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
    Assertions.assertSame(original, Hirs.getCallableSignature(call));
    factory.body(replacements.getFirst().body());
    HirTyCommonVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertEquals(updated, Hirs.getCallableSignature(call)),
      () -> Assertions.assertNotEquals(original, Hirs.getCallableSignature(call)),
      () -> Assertions.assertSame(factory.ty().returnTy(), Hirs.getCallableSignature(call)),
      () -> Assertions.assertSame(factory.ty().returnTy(), Hirs.getCallableValueTy(call)),
      () -> Assertions.assertSame(Ty.DEADEND, call.ty()),
      () -> Assertions.assertSame(Ty.INFER, factory.signature().returnTypeAnnotation().ty())
    );
  }

  static Stream<Arguments> bindingTargets() {
    final var declaration = new Hir.Dec(
      new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new DynamicTyDefault(Ty.LONG), Ty.INTEGER
    );
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), Hir.BuiltInTy.fromString("long", MachineTarget.T64), false, Ty.INTEGER);
    final var function = new TyFn(new TyParam[0], false, Ty.INTEGER);
    return Stream.of(
      Arguments.of(declaration, Ty.INTEGER),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("unresolved"), Hir.MutabilityKind.IMMUTABLE, new DynamicTyDefault(Ty.INTEGER)), null),
      Arguments.of(new Hir.Identifier(declaration.lexeme().name(), declaration), Ty.INTEGER),
      Arguments.of(parameter, Ty.INTEGER),
      Arguments.of(new Hir.Identifier(parameter.lexeme().name(), parameter), Ty.INTEGER),
      Arguments.of(new Hir.Parameter(new Hir.Lexeme("unresolved"), Hir.BuiltInTy.fromString("int", MachineTarget.T64), false, null), null),
      Arguments.of(new Hir.FunctionSignature(new Hir.Parameter[0], false, new DynamicTyDefault(Ty.INTEGER), function), function),
      Arguments.of(noncontinuingMember(), Ty.LONG)
    );
  }

  @ParameterizedTest
  @MethodSource("bindingTargets")
  void given__binding_context__when__type_is_queried__then__resolved_or_compatible_target_type_is_returned(
    final Hir.Expression target, final Ty expected
  ) {
    Assertions.assertSame(expected, Hirs.getBindingTy(target));
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
      () -> Assertions.assertSame(Ty.INTEGER, Hirs.getBindingTy(target)),
      () -> Assertions.assertSame(Ty.INTEGER, Hirs.getAssignmentContextTy(target)),
      () -> Assertions.assertSame(Ty.VOID, Hirs.getBindingTy(assignment))
    );
  }

  static Stream<Arguments> assignmentContexts() {
    final var declaration = new Hir.Dec(
      new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new DynamicTyDefault(Ty.LONG), Ty.INTEGER
    );
    final var partial = new TyValueArray(Ty.INFER, 2);
    return Stream.of(
      Arguments.of(declaration, Ty.LONG),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new DynamicTyDefault(), Ty.INTEGER), null),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new DynamicTyDefault(partial),
        new TyValueArray(Ty.INTEGER, 2)), partial),
      Arguments.of(new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new DynamicTyDefault(), Ty.INTEGER), null),
      Arguments.of(new Hir.Identifier(declaration.lexeme().name(), declaration), Ty.INTEGER),
      Arguments.of(new Hir.Parameter(new Hir.Lexeme("p"), null, false, Ty.INTEGER), Ty.INTEGER),
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
    Assertions.assertSame(expected, Hirs.getAssignmentContextTy(target));
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
    final var annotation = new DynamicTyDefault(declared == null ? Ty.INFER : declared);
    final var signature = new Hir.FunctionSignature(
      new Hir.Parameter[0], false, annotation, resolved == null ? null : new TyFn(new TyParam[0], false, resolved)
    );
    final var original = signature.ty();
    Assertions.assertAll(
      () -> Assertions.assertSame(expected, Hirs.getFunctionReturnContextTy(signature)),
      () -> Assertions.assertSame(useSiteExpected, Hirs.getFunctionReturnContextTy(signature, useSiteContext)),
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
      Arguments.of(new TyUnion(new Ty[]{Ty.INTEGER, Ty.BOOLEAN}), false),
      Arguments.of(Tys.typeInfo(Ty.VOID, Ty.INTEGER, Ty.INFER), false),
      Arguments.of(Tys.typeInfo(Ty.INTEGER, Ty.INTEGER, new TyValueArray(Ty.INFER, 2)), false),
      Arguments.of(Tys.typeInfo(Ty.VOID, Ty.INFER, Ty.INTEGER), true),
      Arguments.of(Tys.typeInfo(null, Ty.INTEGER, Ty.INTEGER), true),
      Arguments.of(Tys.typeInfo(Ty.VOID, null, Ty.INTEGER), true),
      Arguments.of(Tys.typeInfo(Ty.DEADEND, null, Ty.INFER), false),
      Arguments.of(Tys.typeInfo(Ty.DEADEND, Ty.INFER, Ty.INTEGER), true),
      Arguments.of(new TyValueArray(Tys.typeInfo(Ty.INTEGER, Ty.INTEGER, Ty.INFER), 2), false),
      Arguments.of(new TyValueArray(Tys.typeInfo(Ty.INTEGER, Ty.INFER, Ty.INTEGER), 2), true)
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
