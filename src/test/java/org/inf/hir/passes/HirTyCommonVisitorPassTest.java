package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyField;
import org.inf.ty.TyParam;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueArray;
import org.inf.ty.TyValueNumberInteger;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.inf.ty.util.TypeComparison;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.stream.Stream;

class HirTyCommonVisitorPassTest {

  static Stream<Arguments> completionConsumers() {
    final var integer = new Hir.Literal("1", Ty.INTEGER);
    final var returning = new Hir.Return(integer);
    final var sequence = new Hir.Expressions(new Hir.Expression[]{returning, integer});
    final var declaration = new Hir.Dec(new Hir.Lexeme("n"), Hir.MutabilityKind.IMMUTABLE, new Hir.TyExpr(Ty.INFER));
    return Stream.of(
      Arguments.of(new Hir.BinaryOperation(integer, Hir.BinaryOperationKind.ADD, integer), Ty.INTEGER),
      Arguments.of(new Hir.BinaryOperation(returning, Hir.BinaryOperationKind.ADD, integer), Ty.DEADEND),
      Arguments.of(new Hir.BinaryOperation(new Hir.Literal("true", Ty.BOOLEAN), Hir.BinaryOperationKind.AND, returning), Ty.BOOLEAN),
      Arguments.of(new Hir.BinaryOperation(returning, Hir.BinaryOperationKind.AND, integer), Ty.DEADEND),
      Arguments.of(new Hir.Range(integer, integer), new TyValueArray(Ty.INTEGER, null)),
      Arguments.of(new Hir.Range(returning, integer), Ty.DEADEND),
      Arguments.of(new Hir.Conditional(new Hir.Literal("true", Ty.BOOLEAN), integer, returning), Ty.INTEGER),
      Arguments.of(new Hir.Not(returning, null), Ty.DEADEND),
      Arguments.of(new Hir.Assignment(declaration, integer), Ty.VOID),
      Arguments.of(new Hir.Array(new Hir.Expression[]{integer}, new Hir.TyExpr(Ty.INFER), null, null, null), new TyValueArray(Ty.INTEGER, null)),
      Arguments.of(sequence, Ty.DEADEND),
      Arguments.of(new Hir.Block(sequence, null), Ty.DEADEND),
      Arguments.of(new Hir.Argument(null, returning), Ty.DEADEND),
      Arguments.of(new Hir.TupleEntry(null, returning), Ty.DEADEND)
    );
  }

  @ParameterizedTest
  @MethodSource("completionConsumers")
  void given__operands__when__common_typing_runs__then__completion_types_are_resolved(
    final Hir.Expression expression, final Ty expected
  ) {
    HirTyCommonVisitorPass.resolveAvailableTypes(expression);
    HirTyCommonVisitorPass.pass(expression);
    Assertions.assertEquals(expected, expression.ty());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '#', value = {
    "7 + 8 # int",
    "({ return true; 7; }) + 8 # bool",
    "7 + { return true; } # bool",
    "false && { return 7; } # int,bool",
    "true || { return 7; } # int,bool",
    "if ({ return 7; }) then true else false # int",
    "{ var n = 7; n = 8; } # void",
    "{ return 7; true; } # int"
  })
  void given__expression_body__when__the_pipeline_runs__then__only_completion_and_reachable_returns_determine_results(
    final String body, final String resultTypes
  ) {
    final var root = Inf.codeToThir("val use = () => %s; use()".formatted(body)).root();
    final var expected = Tys.union(Stream.of(resultTypes.split(","))
      .map(name -> name.equals("void") ? Ty.VOID : Tys.fromString(name, new MachineTarget(64)))
      .toArray(Ty[]::new));
    Assertions.assertTrue(TypeComparison.sameValueType(expected, root.ty()),
      () -> "Expected %s, got %s".formatted(expected, root.ty()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {
    false,
    true
  })
  void given__range_bound_transfer__when__typed__then__the_range_shape_does_not_unwrap_return_payloads(
    final boolean lowerTransfers
  ) {
    final var integer = new Hir.Literal("1", Ty.INTEGER);
    final var returning = new Hir.Return(integer);
    final var range = new Hir.Range(lowerTransfers ? returning : integer, lowerTransfers ? integer : returning);
    final var root = HirTyCommonVisitorPass.pass(new Hir.Program(range));
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.DEADEND, range.ty()),
      () -> Assertions.assertEquals(new TyValueArray(Ty.INVALID, null), range.rangeTy()),
      () -> Assertions.assertEquals(range.rangeTy(), Tys.getIndexingAccessorTy(range)),
      () -> Assertions.assertSame(Ty.INTEGER, root.ty())
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "a[0] | uint8 | false",
    "t[0] | uint8 | false",
    "({ return true; a; })[0] | bool | true",
    "({ return true; t; })[0] | bool | true",
    "a[{ return true; }] | bool | true",
    "(arrayFactory({ return true; }))[0] | bool | true",
    "(tupleFactory({ return true; }))[0] | bool | true",
    "(if ({ return true; }) then a else a)[0] | bool | true",
    "[{ return true; }, 7u8][0] | bool | true",
    "[a][{ return true; }][0] | bool | true",
    "[(7u8, false)][{ return true; }][0] | bool | true"
  })
  void given__indexing_inputs__when__the_pipeline_runs__then__selected_type_and_reachable_returns_are_separate(
    final String expression, final String returnType, final boolean deadEnd
  ) {
    final var root = Inf.codeToThir("""
      val a = [7u8];
      val t = (7u8, false);
      val arrayFactory = (flag: bool): [;uint8;1] => a;
      val tupleFactory = (flag: bool): (uint8, bool) => t;
      val use = () => %s;
      use()
      """.formatted(expression)).root();
    final var accesses = new ArrayList<Hir.ArrayAccess>();
    root.visit(new HirVisitor() {
      @Override
      public void visitArrayAccess(final Hir.ArrayAccess access) {
        accesses.add(access);
        HirVisitor.super.visitArrayAccess(access);
      }
    });
    final var access = accesses.getFirst();
    final var uint8 = Tys.fromString("uint8", new MachineTarget(64));
    Assertions.assertAll(
      () -> Assertions.assertTrue(TypeComparison.sameValueType(Tys.fromString(returnType, new MachineTarget(64)), root.ty())),
      () -> Assertions.assertTrue(TypeComparison.sameValueType(deadEnd ? Ty.DEADEND : uint8, access.ty())),
      () -> Assertions.assertTrue(TypeComparison.sameValueType(uint8, Tys.getIndexedTy(access))),
      () -> Assertions.assertSame(Tys.getIndexedTy(access), Tys.getBindingTy(access)),
      () -> Assertions.assertSame(Tys.getIndexedTy(access), access.indexedTy())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val Read = (a: [;uint8;1]): uint8; val read: Read = (a) => a[0]; read([7u8])",
    "val Read = (t: (uint8, bool)): uint8; val read: Read = (t) => t[0]; read((7u8, false))"
  })
  void given__inferred_receiver_parameter__when__contextual_typing_runs__then__indexing_uses_the_resolved_binding(
    final String code
  ) {
    final var root = Inf.codeToThir(code).root();
    final var accesses = new ArrayList<Hir.ArrayAccess>();
    root.visit(new HirVisitor() {
      @Override
      public void visitArrayAccess(final Hir.ArrayAccess access) {
        accesses.add(access);
        HirVisitor.super.visitArrayAccess(access);
      }
    });
    final var uint8 = Tys.fromString("uint8", new MachineTarget(64));
    Assertions.assertAll(
      () -> Assertions.assertEquals(uint8, root.ty()),
      () -> Assertions.assertEquals(uint8, Tys.getIndexedTy(accesses.getFirst()))
    );
  }

  @Test
  void given__indexed_receiver__when__its_element_type_is_refined__then__common_typing_refreshes_the_selected_type() {
    final var root = Inf.codeToThir("val a = [7u8]; a[0]").root();
    final var arrays = new ArrayList<Hir.Array>();
    final var accesses = new ArrayList<Hir.ArrayAccess>();
    root.visit(new HirVisitor() {
      @Override
      public void visitArray(final Hir.Array array) {
        arrays.add(array);
      }

      @Override
      public void visitArrayAccess(final Hir.ArrayAccess access) {
        accesses.add(access);
      }
    });
    final var access = accesses.getFirst();
    arrays.getFirst().elements()[0] = new Hir.Literal("true", Ty.BOOLEAN);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirTyCommonVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.BOOLEAN, Tys.getIndexedTy(access)),
      () -> Assertions.assertSame(Ty.BOOLEAN, access.ty()),
      () -> Assertions.assertSame(Ty.BOOLEAN, root.ty())
    );
  }

  @Test
  void given__noncontinuing_range_accessor__when__indexing_is_typed__then__the_slice_type_is_retained() {
    final var array = new Hir.Array(
      new Hir.Expression[]{new Hir.Literal("7", Ty.INTEGER)}, new Hir.TyExpr(Ty.INTEGER), null, null, null
    );
    final var lower = new Hir.Block(new Hir.Expressions(new Hir.Expression[]{
      new Hir.Return(new Hir.Literal("true", Ty.BOOLEAN)), new Hir.Literal("0", Ty.INTEGER)
    }), null);
    final var range = new Hir.Range(lower, new Hir.Literal("0", Ty.INTEGER));
    final var access = new Hir.ArrayAccess(array, range, null, null);
    final var root = HirTyCommonVisitorPass.pass(new Hir.Program(access));
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.DEADEND, access.ty()),
      () -> Assertions.assertSame(Ty.DEADEND, access.accessor().ty()),
      () -> Assertions.assertInstanceOf(TyValueArray.class, Tys.getIndexingAccessorTy(access.accessor())),
      () -> Assertions.assertEquals(Tys.getIndexingReceiverTy(access.target()), Tys.getIndexedTy(access)),
      () -> Assertions.assertSame(Ty.BOOLEAN, root.ty())
    );
  }

  @Test
  void given__diverging_tuple_receiver__when__indexing_is_typed__then__no_layout_is_manufactured() {
    final var root = Inf.codeToThir("val use = () => ({ return true; }, 7u8)[0]; use()").root();
    final var accesses = new ArrayList<Hir.ArrayAccess>();
    root.visit(new HirVisitor() {
      @Override
      public void visitArrayAccess(final Hir.ArrayAccess access) {
        accesses.add(access);
      }
    });
    final var access = accesses.getFirst();
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.DEADEND, Tys.getIndexingReceiverTy(access.target())),
      () -> Assertions.assertSame(Ty.INFER, Tys.getIndexedTy(access)),
      () -> Assertions.assertSame(Ty.DEADEND, access.ty()),
      () -> Assertions.assertSame(Ty.BOOLEAN, root.ty())
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "s.value | uint8 | false",
    "({ return true; s; }).value | bool | true",
    "make({ return true; }).value | bool | true",
    "(new heap S { value = 7; flag = { return true; }; }).value | bool | true",
    "(if ({ return true; }) then s else s).value | bool | true",
    "([s][{ return true; }]).value | bool | true",
    "(outer = s,).outer.value | uint8 | false",
    "({ return true; (outer = s,); }).outer.value | bool | true"
  })
  void given__member_receiver__when__the_pipeline_runs__then__selected_type_is_separate_from_reachable_returns(
    final String access, final String returnType, final boolean deadEnd
  ) {
    final var root = Inf.codeToThir("""
      val S = struct { val value: uint8; val flag: bool; };
      val s = new heap S { value = 7; flag = false; };
      val make = (flag: bool): S => s;
      val use = () => %s;
      use()
      """.formatted(access)).root();
    final var paths = new ArrayList<Hir.Path>();
    root.visit(new HirVisitor() {
      @Override
      public void visitPath(final Hir.Path path) {
        paths.add(path);
        HirVisitor.super.visitPath(path);
      }
    });
    final var path = paths.stream()
      .filter(candidate -> candidate.elements()[candidate.elements().length - 1] instanceof Hir.Lexeme name
        && name.name().equals("value"))
      .findFirst().orElseThrow();
    final var uint8 = Tys.fromString("uint8", new MachineTarget(64));
    Assertions.assertAll(
      () -> Assertions.assertEquals(Tys.fromString(returnType, new MachineTarget(64)), root.ty()),
      () -> Assertions.assertEquals(deadEnd ? Ty.DEADEND : uint8, path.ty()),
      () -> Assertions.assertEquals(uint8, Tys.getMemberTy(path)),
      () -> Assertions.assertEquals(uint8, Tys.getBindingTy(path))
    );
  }

  @Test
  void given__resolved_member__when__the_layout_is_refined__then__queries_follow_refreshed_member_typing() {
    final var root = Inf.codeToThir("""
      val S = struct { val value: uint8; };
      val s = new heap S { value = 7; };
      s.value
      """).root();
    final var structs = new ArrayList<Hir.Struct>();
    final var paths = new ArrayList<Hir.Path>();
    root.visit(new HirVisitor() {
      @Override
      public void visitStruct(final Hir.Struct struct) {
        structs.add(struct);
      }

      @Override
      public void visitPath(final Hir.Path path) {
        paths.add(path);
      }
    });
    final var path = paths.getFirst();
    final var original = Tys.getMemberTy(path);
    structs.getFirst().declarations()[0].typeAnnotation(new Hir.TyExpr(Ty.LONG));
    Assertions.assertSame(original, Tys.getMemberTy(path));
    HirTyCommonVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.LONG, Tys.getMemberTy(path)),
      () -> Assertions.assertEquals(Ty.LONG, Tys.getBindingTy(path)),
      () -> Assertions.assertEquals(Ty.LONG, path.ty()),
      () -> Assertions.assertEquals(Ty.LONG, root.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(booleans = {
    false,
    true
  })
  void given__construction_with_noncontinuing_inputs__when__typed__then__layout_and_reachable_return_are_separate(
    final boolean constructorCall
  ) {
    final var layout = new TyStruct(new TyField[]{new TyField("value", Ty.INTEGER)});
    final var target = new Hir.Block(new Hir.Expressions(new Hir.Expression[]{
      new Hir.Return(new Hir.Literal("true", Ty.BOOLEAN)),
      new Hir.TyExpr(layout)
    }), null);
    final var returning = new Hir.Return(new Hir.Literal("1", Ty.INTEGER));
    final Hir.Expression creation;
    if (constructorCall) {
      creation = new Hir.NewByCtor(target, null, returning, null);
    } else {
      creation = new Hir.NewByBlock(target, null, new Hir.Assignment[]{
        new Hir.Assignment(new Hir.Lexeme("value"), returning)
      }, null);
    }
    final var root = HirTyCommonVisitorPass.pass(new Hir.Program(creation));
    Assertions.assertAll(
      () -> Assertions.assertSame(layout, Tys.getConstructionTargetTy(target)),
      () -> Assertions.assertSame(layout, Tys.getMemberReceiverTy(creation)),
      () -> Assertions.assertSame(Ty.DEADEND, creation.ty()),
      () -> Assertions.assertSame(Ty.BOOLEAN, root.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "({ return true; s; }).missing",
    "make({ return true; }).missing",
    "(new heap S { value = { return true; }; }).missing"
  })
  void given__unknown_member_after_transfer__when__typed__then__the_layout_still_reports_the_missing_field(
    final String access
  ) {
    final var error = Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir("""
      val S = struct { val value: int; };
      val s = new heap S { value = 1; };
      val make = (flag: bool): S => s;
      val use = () => %s;
      use()
      """.formatted(access)));
    Assertions.assertEquals("Unknown field: missing", error.getMessage());
  }

  @Test
  void given__diverging_tuple_receiver__when__member_access_is_typed__then__no_layout_is_manufactured() {
    final var root = Inf.codeToThir("val use = () => (a = { return true; },).a; use()").root();
    final var paths = new ArrayList<Hir.Path>();
    root.visit(new HirVisitor() {
      @Override
      public void visitPath(final Hir.Path path) {
        paths.add(path);
      }
    });
    final var path = paths.getFirst();
    Assertions.assertAll(
      () -> Assertions.assertNull(Tys.getMemberReceiverTy(path.elements()[0])),
      () -> Assertions.assertSame(Ty.INFER, Tys.getMemberTy(path)),
      () -> Assertions.assertSame(Ty.DEADEND, path.ty()),
      () -> Assertions.assertSame(Ty.BOOLEAN, root.ty())
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "({ apply; })((v) => v, (1, true), 2) | uint8 | false",
    "({ return true; apply; })((v) => v, (1, true), 2) | bool | true",
    "(if ({ return true; }) then apply else apply)((v) => v, (1, true), 2) | bool | true",
    "(factory({ return true; }))((v) => v, (1, true), 2) | bool | true",
    "([apply][{ return true; }])((v) => v, (1, true), 2) | bool | true",
    "(new heap S { call = apply; flag = { return true; }; }).call((v) => v, (1, true), 2) | bool | true",
    "apply((v) => v, (1, true), { return true; }) | bool | true",
    "({ return true; apply; })(x = 2, fn = (v) => v, t = (1, true)) | bool | true"
  })
  void given__wrapped_callee__when__the_pipeline_runs__then__signature_context_and_reachable_return_are_separate(
    final String invocation, final String returnType, final boolean deadEnd
  ) {
    final var hir = Inf.codeToHir("""
      val Fn = (value: uint8): uint8;
      val Consumer = (fn: Fn, t: (uint8, bool), x: uint8): uint8;
      val apply: Consumer = (fn, t, x) => fn(x);
      val factory = (flag: bool): Consumer => apply;
      val S = struct { val call: Consumer; val flag: bool; };
      val use = () => %s;
      use()
      """.formatted(invocation));
    final var parameters = new ArrayList<Hir.Parameter>();
    hir.visit(new HirVisitor() {
      @Override
      public void visitParameter(final Hir.Parameter parameter) {
        if (parameter.lexeme().name().equals("v")) {
          parameters.add(parameter);
        }
        HirVisitor.super.visitParameter(parameter);
      }
    });
    Assertions.assertEquals(1, parameters.size());
    final var parameter = parameters.getFirst();
    final var annotation = parameter.typeAnnotation();
    final var root = new HirToThirRaising(new MachineTarget(64)).raise(hir).root();
    final var calls = new ArrayList<Hir.Call>();
    root.visit(new HirVisitor() {
      @Override
      public void visitCall(final Hir.Call call) {
        final var signature = Tys.getCallableSignature(call.target());
        if (signature != null && signature.parameters().length == 3) {
          calls.add(call);
        }
        HirVisitor.super.visitCall(call);
      }
    });
    Assertions.assertEquals(1, calls.size());
    final var call = calls.getFirst();
    final var signature = Tys.getCallableSignature(call.target());
    final var uint8 = Tys.fromString("uint8", new MachineTarget(64));
    Assertions.assertAll(
      () -> Assertions.assertEquals(Tys.fromString(returnType, new MachineTarget(64)), root.ty()),
      () -> Assertions.assertEquals(deadEnd ? Ty.DEADEND : uint8, call.ty()),
      () -> Assertions.assertEquals(uint8, signature.returnTy()),
      () -> Assertions.assertSame(annotation, parameter.typeAnnotation()),
      () -> Assertions.assertEquals(Ty.INFER, annotation.ty()),
      () -> Assertions.assertEquals(uint8, parameter.resolvedTy())
    );
    for (final var argument : call.arguments()) {
      final var value = argument.value();
      if (value instanceof Hir.Tuple tuple) {
        Assertions.assertEquals(uint8, tuple.children()[0].value().ty());
      } else if (value instanceof Hir.Literal literal) {
        Assertions.assertEquals(uint8, literal.ty());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = (p: uint8) => p; f",
    "val S = struct { val value: uint8; }; val f = (p: S) => p; f",
    "val f = (p: (uint8, bool)) => p; f",
    "val Fn = (value: uint8): uint8; val f = (p: Fn) => p(1); f",
    "val Fn = (value: uint8): uint8; val f: Fn = (p) => p; f"
  })
  void given__parameter_binding__when__typed__then__references_and_signatures_use_resolved_types(final String code) {
    final var root = Inf.codeToThir(code).root();
    final var parameters = new ArrayList<Hir.Parameter>();
    final var references = new ArrayList<Hir.Identifier>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunctionSignature(final Hir.FunctionSignature signature) {
        for (var i = 0; i < signature.parameters().length; i++) {
          final var parameter = signature.parameters()[i];
          Assertions.assertSame(parameter.resolvedTy(), signature.ty().parameters()[i].ty());
        }
        HirVisitor.super.visitFunctionSignature(signature);
      }

      @Override
      public void visitParameter(final Hir.Parameter parameter) {
        if (parameter.lexeme().name().equals("p")) {
          parameters.add(parameter);
        }
        HirVisitor.super.visitParameter(parameter);
      }

      @Override
      public void visitIdentifier(final Hir.Identifier identifier) {
        if (identifier.lexeme().name().equals("p")) {
          references.add(identifier);
        }
      }
    });
    Assertions.assertEquals(1, parameters.size());
    final var parameter = parameters.getFirst();
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.VOID, parameter.ty()),
      () -> Assertions.assertNotNull(parameter.resolvedTy()),
      () -> Assertions.assertSame(parameter.resolvedTy(), Tys.getBindingTy(parameter)),
      () -> Assertions.assertFalse(references.isEmpty())
    );
    if (!Tys.isInferred(parameter.typeAnnotation().ty())) {
      Assertions.assertEquals(parameter.typeAnnotation().ty(), parameter.resolvedTy());
    }
    for (final var reference : references) {
      Assertions.assertAll(
        () -> Assertions.assertSame(parameter, reference.target()),
        () -> Assertions.assertSame(parameter.resolvedTy(), reference.ty())
      );
    }
  }

  @Test
  void given__resolved_parameter__when__annotation_changes__then__binding_and_signature_refresh_during_typing() {
    final var root = Inf.codeToThir("val f = (p: int) => p; f").root();
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function function) {
        functions.add(function);
        HirVisitor.super.visitFunction(function);
      }
    });
    Assertions.assertEquals(1, functions.size());
    final var function = functions.getFirst();
    final var parameter = function.signature().parameters()[0];
    final var body = Assertions.assertInstanceOf(Hir.Return.class, function.body());
    final var reference = Assertions.assertInstanceOf(Hir.Identifier.class, body.expression());
    final var resolved = parameter.resolvedTy();
    parameter.typeAnnotation(new Hir.TyExpr(Ty.LONG));
    Assertions.assertAll(
      () -> Assertions.assertSame(resolved, parameter.resolvedTy()),
      () -> Assertions.assertSame(resolved, Tys.getBindingTy(parameter)),
      () -> Assertions.assertSame(resolved, reference.ty()),
      () -> Assertions.assertSame(resolved, function.ty().parameters()[0].ty())
    );
    HirTyCommonVisitorPass.pass(function.signature());
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.VOID, parameter.ty()),
      () -> Assertions.assertEquals(Ty.LONG, parameter.resolvedTy()),
      () -> Assertions.assertEquals(Ty.LONG, reference.ty()),
      () -> Assertions.assertSame(parameter.resolvedTy(), function.ty().parameters()[0].ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val n: uint8 = 7; n",
    "val n = 7; n",
    "val n = (7, true); n",
    "val Pair = struct { val value: int; }; val n: Pair = (value = 7,); n",
    "val Fn = (value: int): int; val n: Fn = (value) => value; n",
    "var n: uint8 = 7; n = 8; n"
  })
  void given__declaration_binding__when__typed__then__references_use_resolved_type_and_preserve_identity(final String code) {
    final var root = Inf.codeToThir(code).root();
    final var declarations = new ArrayList<Hir.Dec>();
    final var references = new ArrayList<Hir.Identifier>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        if (declaration.lexeme().name().equals("n")) {
          declarations.add(declaration);
        }
        HirVisitor.super.visitDec(declaration);
      }

      @Override
      public void visitIdentifier(final Hir.Identifier identifier) {
        if (identifier.lexeme().name().equals("n")) {
          references.add(identifier);
        }
      }
    });
    Assertions.assertEquals(1, declarations.size());
    final var declaration = declarations.getFirst();
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.VOID, declaration.ty()),
      () -> Assertions.assertNotNull(declaration.resolvedTy()),
      () -> Assertions.assertSame(declaration.resolvedTy(), Tys.getBindingTy(declaration)),
      () -> Assertions.assertEquals(declaration.resolvedTy(), root.ty()),
      () -> Assertions.assertFalse(references.isEmpty())
    );
    if (!Tys.containsInferred(declaration.typeAnnotation().ty())) {
      Assertions.assertEquals(declaration.typeAnnotation().ty(), declaration.resolvedTy());
    }
    for (final var reference : references) {
      Assertions.assertAll(
        () -> Assertions.assertSame(declaration, reference.target()),
        () -> Assertions.assertSame(declaration.resolvedTy(), reference.ty())
      );
    }
  }

  @Test
  void given__resolved_declaration__when__annotation_changes__then__queries_wait_for_typing_to_refresh_the_binding() {
    final var root = prepare("val n: int = 7; n");
    final var declarations = new ArrayList<Hir.Dec>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        declarations.add(declaration);
      }
    });
    HirTyCommonVisitorPass.pass(root);
    final var declaration = declarations.getFirst();
    final var resolved = declaration.resolvedTy();
    declaration.typeAnnotation(new Hir.TyExpr(Ty.LONG));
    Assertions.assertAll(
      () -> Assertions.assertSame(resolved, declaration.resolvedTy()),
      () -> Assertions.assertSame(resolved, Tys.getBindingTy(declaration)),
      () -> Assertions.assertSame(resolved, root.ty())
    );
    HirTyCommonVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.LONG, declaration.resolvedTy()),
      () -> Assertions.assertEquals(Ty.LONG, root.ty()),
      () -> Assertions.assertEquals(Ty.VOID, declaration.ty())
    );
  }

  @Test
  void given__struct_fields_with_alias_constraints__when__typed__then__layout_uses_resolved_field_bindings() {
    final var root = Inf.codeToThir("""
      val Fn = (value: int): int;
      val S = struct { val fn: Fn; val tuple: (uint8, bool); };
      S
      """).root();
    final var structs = new ArrayList<Hir.Struct>();
    root.visit(new HirVisitor() {
      @Override
      public void visitStruct(final Hir.Struct struct) {
        structs.add(struct);
      }
    });
    Assertions.assertEquals(1, structs.size());
    final var struct = structs.getFirst();
    final var layout = Assertions.assertInstanceOf(TyStruct.class, struct.ty());
    Assertions.assertEquals(struct.declarations().length, layout.fields().length);
    for (var i = 0; i < layout.fields().length; i++) {
      final var declaration = struct.declarations()[i];
      final var field = layout.fields()[i];
      Assertions.assertAll(
        () -> Assertions.assertEquals(Ty.VOID, declaration.ty()),
        () -> Assertions.assertEquals(declaration.lexeme().name(), field.name()),
        () -> Assertions.assertSame(declaration.resolvedTy(), field.ty())
      );
    }
    Assertions.assertInstanceOf(Hir.Identifier.class, struct.declarations()[0].typeAnnotation());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "[17;double;1]",
    "[17.0f;double;1]"
  })
  void given__noninteger_array_destination__when__common_typing_runs__then__existing_numeric_literal_adaptation_is_preserved(
    final String code
  ) {
    final var root = prepare(code);
    HirTyCommonVisitorPass.pass(root);
    final var literals = new ArrayList<Hir.Literal>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal expression) {
        if (expression.content().startsWith("17")) {
          literals.add(expression);
        }
      }
    });
    Assertions.assertEquals(1, literals.size());
    Assertions.assertEquals(Ty.DOUBLE, literals.getFirst().ty());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v; }; s.fn",
    "val S = struct { val fn: Fn; }; val make = () => new heap S { fn = (v) => v; }; make().fn",
    "val S = struct { val fn: Fn; }; val make = () => { val s = new heap S { fn = (v) => v; }; s; }; make().fn",
    "val S = struct { val fn: Fn; }; val values = [new heap S { fn = (v) => v; }]; values[0].fn",
    "val f: Fn = (v) => v; val wrapper = (inner = (call = f,),); wrapper.inner.call"
  })
  void given__function_valued_receivers__when__available_types_are_resolved__then__contextual_passes_can_read_the_signature(
    final String expression
  ) {
    final var root = prepare("val Fn = (value: int): int; %s".formatted(expression));
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    final var integer = Tys.fromString("int", new MachineTarget(64));
    final var expected = new TyFn(new TyParam[]{new TyParam("value", integer)}, false, integer);
    Assertions.assertTrue(TypeComparison.sameValueType(root.ty(), expected));
  }

  @Test
  void given__inferred_function_return__when__full_typing_runs__then__resolved_return_refreshes_without_replacing_annotation() {
    final var root = prepare("val Fn = (value: (uint8,)): int; val f: Fn = (v) => v[0] + 0; f((1,))");
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function expression) {
        functions.add(expression);
        HirVisitor.super.visitFunction(expression);
      }
    });
    final var annotation = functions.getFirst().signature().returnTypeAnnotation();
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    Assertions.assertTrue(Tys.isInferred(annotation.ty()));
    final var typed = new HirToThirRaising(new MachineTarget(64)).raise(root).root();
    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, functions.getFirst().signature().returnTypeAnnotation()),
      () -> Assertions.assertEquals(Ty.INFER, annotation.ty()),
      () -> Assertions.assertTrue(TypeComparison.sameValueType(
        Tys.fromString("int", new MachineTarget(64)), functions.getFirst().ty().returnTy())),
      () -> Assertions.assertEquals(Tys.fromString("int", new MachineTarget(64)), typed.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = () => 7; f",
    "val f = () => (7, true); f",
    "val f = (): (uint8, bool) => (7, true); f",
    "val Fn = (value: uint8): uint8; val f = (): Fn => ((v) => v); f",
    "val Fn = (value: uint8): uint8; val Factory = (): Fn; val f: Factory = () => ((v) => v); f",
    "val Pair = struct { val value: uint8; }; val f = (): Pair => new heap Pair { value = 7; }; f"
  })
  void given__function_return_annotation__when__typing_repeats__then__source_and_resolved_signature_remain_separate(
    final String code
  ) {
    final var root = Inf.codeToThir(code).root();
    final var signatures = new ArrayList<Hir.FunctionSignature>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function function) {
        signatures.add(function.signature());
        HirVisitor.super.visitFunction(function);
      }
    });
    Assertions.assertFalse(signatures.isEmpty());
    final var annotations = signatures.stream().map(Hir.FunctionSignature::returnTypeAnnotation).toList();
    final var types = signatures.stream().map(Hir.FunctionSignature::ty).toList();
    for (var repetition = 0; repetition < 2; repetition++) {
      HirTyCommonVisitorPass.resolveAvailableTypes(root);
      HirTyCommonVisitorPass.pass(root);
      HirFunctionValidationVisitorPass.pass(root);
      HirTupleValidationVisitorPass.pass(root);
      for (var i = 0; i < signatures.size(); i++) {
        final var signature = signatures.get(i);
        final var annotation = annotations.get(i);
        final var resolved = types.get(i);
        Assertions.assertAll(
          () -> Assertions.assertSame(annotation, signature.returnTypeAnnotation()),
          () -> Assertions.assertEquals(resolved, signature.ty()),
          () -> Assertions.assertFalse(Tys.containsInferred(signature.ty()))
        );
      }
    }
    if (code.contains("val f = (): Fn") || code.contains("val f = (): Pair")) {
      Assertions.assertInstanceOf(Hir.Identifier.class, signatures.getFirst().returnTypeAnnotation());
    } else if (!code.contains("(): (")) {
      Assertions.assertEquals(Ty.INFER, signatures.getFirst().returnTypeAnnotation().ty());
    }
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val f = () => { val t: (uint8,) = (1,); t }; f() | uint8",
    "val f = () => { val t: (uint16,) = (255u8,); t }; f() | uint16"
  })
  void given__inferred_return__when__literal_typing_or_slot_conversion_refines_body__then__resolved_return_refreshes(
    final String code, final String slotType
  ) {
    final var root = Inf.codeToThir(code).root();
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function function) {
        functions.add(function);
        HirVisitor.super.visitFunction(function);
      }
    });
    Assertions.assertEquals(1, functions.size());
    final var function = functions.getFirst();
    final var expected = new TyStruct(new TyField[]{
      new TyField(null, Tys.fromString(slotType, new MachineTarget(64)))
    }, true);
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.INFER, function.signature().returnTypeAnnotation().ty()),
      () -> Assertions.assertEquals(expected, function.ty().returnTy()),
      () -> Assertions.assertEquals(expected, root.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = () => [1, 2]; f",
    "val f = (): [;2] => [1, 2]; f"
  })
  void given__inferred_return__when__body_type_changes_after_final_resolution__then__return_is_not_frozen(
    final String code
  ) {
    final var root = Inf.codeToThir(code).root();
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function function) {
        functions.add(function);
        HirVisitor.super.visitFunction(function);
      }
    });
    final var function = functions.getFirst();
    final var annotation = function.signature().returnTypeAnnotation();
    final var original = function.ty().returnTy();
    function.body().visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal literal) {
        if (literal.ty() instanceof TyValueNumberInteger) {
          literal.ty(Ty.LONG);
        }
      }
    });
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirTyCommonVisitorPass.pass(root);
    final var expected = new TyValueArray(Ty.LONG, 2);
    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, function.signature().returnTypeAnnotation()),
      () -> Assertions.assertTrue(Tys.containsInferred(annotation.ty())),
      () -> Assertions.assertNotEquals(original, function.ty().returnTy()),
      () -> Assertions.assertEquals(expected, function.ty().returnTy()),
      () -> Assertions.assertEquals(function.ty(), root.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val n = 7; n",
    "val n = (7, true); n",
    "val n = (v: uint8) => v; n",
    "val Fn = (value: int): int; val n: Fn = (v) => v; n",
    "val Pair = struct { val value: int; }; val n: Pair = (value = 7,); n"
  })
  void given__declaration_annotation__when__full_typing_repeats__then__source_annotation_is_preserved(final String code) {
    var root = prepare(code);
    final var declarations = new ArrayList<Hir.Dec>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        declarations.add(declaration);
        HirVisitor.super.visitDec(declaration);
      }
    });
    final var declaration = declarations.stream().filter(value -> value.lexeme().name().equals("n")).findFirst().orElseThrow();
    final var annotation = declaration.typeAnnotation();
    final var raising = new HirToThirRaising(new MachineTarget(64));
    for (var i = 0; i < 2; i++) {
      final var constraint = annotation.ty();
      root = raising.raise(root).root();
      Assertions.assertAll(
        () -> Assertions.assertSame(annotation, declaration.typeAnnotation()),
        () -> Assertions.assertFalse(Tys.containsInferred(declaration.resolvedTy()))
      );
      if (constraint != null) {
        Assertions.assertEquals(constraint, annotation.ty());
      }
      Assertions.assertEquals(declaration.resolvedTy(), root.ty());
    }
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val n = { val t: (uint8,) = (1,); t }; n | uint8",
    "val n = { val t: (uint16,) = (255u8,); t }; n | uint16"
  })
  void given__inferred_binding__when__literal_typing_or_slot_conversion_refines_initializer__then__binding_refreshes(
    final String code, final String slotType
  ) {
    final var root = Inf.codeToThir(code).root();
    final var declarations = new ArrayList<Hir.Dec>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        if (declaration.lexeme().name().equals("n")) {
          declarations.add(declaration);
        }
        HirVisitor.super.visitDec(declaration);
      }
    });
    Assertions.assertEquals(1, declarations.size());
    final var declaration = declarations.getFirst();
    final var expected = new TyStruct(new TyField[]{
      new TyField(null, Tys.fromString(slotType, new MachineTarget(64)))
    }, true);
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.INFER, declaration.typeAnnotation().ty()),
      () -> Assertions.assertEquals(expected, declaration.resolvedTy()),
      () -> Assertions.assertEquals(expected, root.ty())
    );
  }

  @Test
  void given__expected_tuple_type__when__only_common_inference_runs__then__no_contextual_rewriting() {
    final var root = HirTyIdentifierToTyTransformerPass.pass(
      Inf.codeToHir("val t: (uint8,) = (1,); t"), new MachineTarget(64)
    );
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.pass(root);
    final var literals = new ArrayList<Hir.Literal>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(Hir.Literal literal) {
        literals.add(literal);
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, literals.size()),
      () -> Assertions.assertEquals(Ty.INTEGER, literals.getFirst().ty()),
      () -> Assertions.assertThrows(InvalidTypeConversionException.class, () -> HirTupleValidationVisitorPass.pass(root))
    );
  }

  @Test
  void given__inferred_declaration__when__independent_preparation_repeats__then__annotation_and_latest_value_type_are_preserved() {
    final var root = prepare("val t = (1, true); t");
    final var declarations = new ArrayList<Hir.Dec>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        declarations.add(declaration);
        HirVisitor.super.visitDec(declaration);
      }
    });
    final var declaration = declarations.getFirst();
    final var annotation = declaration.typeAnnotation();
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    final var initial = declaration.resolvedTy();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal literal) {
        if (literal.ty() instanceof TyValueNumberInteger) {
          literal.ty(Ty.LONG);
        }
      }
    });
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, declaration.typeAnnotation()),
      () -> Assertions.assertTrue(Tys.containsInferred(annotation.ty())),
      () -> Assertions.assertNotEquals(initial, declaration.resolvedTy()),
      () -> Assertions.assertEquals(root.ty(), declaration.resolvedTy()),
      () -> Assertions.assertSame(declaration.resolvedTy(), Tys.getBindingTy(declaration))
    );
    HirTyCommonVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, declaration.typeAnnotation()),
      () -> Assertions.assertTrue(Tys.containsInferred(annotation.ty())),
      () -> Assertions.assertEquals(root.ty(), declaration.resolvedTy())
    );
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal literal) {
        if (literal.ty() instanceof TyValueNumberInteger) {
          literal.ty(Ty.SHORT);
        }
      }
    });
    HirTyCommonVisitorPass.pass(root);
    final var expected = new TyStruct(new TyField[]{
      new TyField(null, Ty.SHORT),
      new TyField(null, Ty.BOOLEAN)
    }, true);
    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, declaration.typeAnnotation()),
      () -> Assertions.assertTrue(Tys.containsInferred(annotation.ty())),
      () -> Assertions.assertEquals(expected, declaration.resolvedTy()),
      () -> Assertions.assertEquals(expected, root.ty())
    );
  }

  private static Hir.Expression prepare(final String code) {
    final var root = HirTyIdentifierToTyTransformerPass.pass(Inf.codeToHir(code), new MachineTarget(64));
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    return root;
  }
}
