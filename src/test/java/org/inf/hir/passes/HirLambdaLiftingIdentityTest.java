package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyStruct;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HirLambdaLiftingIdentityTest {

  @Test
  void given__captured_callee_with_transferring_argument__when__lifted__then__completion_and_signature_are_preserved() {
    final var root = Inf.codeToThir("""
      val captured: int = 7;
      val read = (value: int): int => value + captured;
      read({ return true; })
      """).root();
    final var lifted = functions(root).getFirst();
    final var calls = new ArrayList<Hir.Call>();
    root.visit(new HirVisitor() {
      @Override
      public void visitCall(final Hir.Call call) {
        calls.add(call);
        HirVisitor.super.visitCall(call);
      }
    });
    assertEquals(1, calls.size());
    final var call = calls.getFirst();
    final var capture = lifted.signature().parameters()[1];
    final var argument = assertInstanceOf(Hir.Identifier.class, call.arguments()[1].value());
    final var declaration = assertInstanceOf(Hir.Dec.class, argument.target());
    assertLocalBindings(lifted);
    assertAll(
      () -> assertEquals(Ty.BOOLEAN, root.ty()),
      () -> assertEquals(Ty.DEADEND, call.ty()),
      () -> assertEquals(Ty.DEADEND, call.arguments()[0].ty()),
      () -> assertSame(lifted.ty(), Tys.getCallableSignature(call.target())),
      () -> assertEquals(lifted.signature().returnTypeAnnotation().ty(), Tys.getCallableSignature(call.target()).returnTy()),
      () -> assertSame(declaration.resolvedTy(), argument.ty()),
      () -> assertSame(declaration.resolvedTy(), capture.resolvedTy())
    );
  }

  @Test
  void given__captured_function_returning_contextual_tuple__when__lifted__then__bindings_signature_and_layout_survive() {
    final var root = Inf.codeToThir("""
      val captured: int = 7;
      val read = (flag: bool): (value: int, flag: bool) => (flag = flag, value = captured);
      read(true)
      """).root();
    final var lifted = functions(root).getFirst();
    final var tuple = assertInstanceOf(Hir.Tuple.class, assertInstanceOf(Hir.Return.class, lifted.body()).expression());
    final var layout = assertInstanceOf(TyStruct.class, lifted.ty().returnTy());
    final var capture = lifted.signature().parameters()[1];
    final var reference = assertInstanceOf(Hir.Identifier.class, tuple.children()[1].value());
    assertLocalBindings(lifted);
    assertAll(
      () -> assertEquals(2, lifted.signature().parameters().length),
      () -> assertEquals("captured", capture.lexeme().name()),
      () -> assertSame(capture, reference.target()),
      () -> assertSame(capture.resolvedTy(), reference.ty()),
      () -> assertSame(capture.resolvedTy(), lifted.ty().parameters()[1].ty()),
      () -> assertEquals(layout, tuple.ty()),
      () -> assertEquals(layout, tuple.contextualType()),
      () -> assertEquals("value", tuple.contextualType().fields()[0].name()),
      () -> assertEquals("flag", tuple.contextualType().fields()[1].name()),
      () -> assertEquals("flag", tuple.children()[0].label().name()),
      () -> assertEquals("value", tuple.children()[1].label().name()),
      () -> assertEquals(layout, root.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val x = 7; val f = () => x; f()",
    "val x = 7; val f = () => (x, true); f()",
    "val Pair = struct { val value: int; }; val x = new heap Pair { value = 7; }; val f = (): Pair => x; f()"
  })
  void given__captured_function_with_return_annotation__when__lifted_and_retyped__then__resolved_return_and_captures_survive(
    final String code
  ) {
    final var root = Inf.codeToThir(code).root();
    final var lifted = functions(root).getFirst();
    final var signature = lifted.signature();
    final var annotation = signature.returnTypeAnnotation();
    final var resolved = signature.ty();
    assertEquals(1, signature.parameters().length);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirTyCommonVisitorPass.pass(root);
    assertLocalBindings(lifted);
    assertAll(
      () -> assertSame(annotation.expression(), signature.returnTypeAnnotation().expression()),
      () -> assertEquals(annotation.ty(), signature.returnTypeAnnotation().ty()),
      () -> assertEquals(resolved, signature.ty()),
      () -> assertEquals(root.ty(), signature.ty().returnTy()),
      () -> assertFalse(Tys.containsInferred(signature.ty()))
    );
    if (code.contains("(): Pair")) {
      assertInstanceOf(Hir.Identifier.class, annotation.expression());
    } else {
      assertEquals(Ty.INFER, annotation.ty());
    }
    assertDoesNotThrow(() -> Inf.codeToMir(code));
  }

  @Test
  void given__lifted_tuple_function__when__bound__then__binding_and_calls_use_the_lifted_signature() {
    final var root = Inf.codeToThir("""
      val captured = (7,);
      val read = (value: (int, bool)) => value[0] + captured[0];
      val argument = (1, true);
      val ordinary = read(argument);
      read argument
      """).root();
    final var lifted = functions(root).getFirst();
    assertLocalBindings(lifted);
    root.visit(new HirVisitor() {
      @Override
      public void visitAssignment(Hir.Assignment assignment) {
        if (assignment.lhs() instanceof Hir.Dec declaration && declaration.lexeme().name().equals("read")) {
          assertAll(
            () -> assertEquals(Ty.VOID, declaration.ty()),
            () -> assertEquals(lifted.ty(), declaration.resolvedTy())
          );
        }
        HirVisitor.super.visitAssignment(assignment);
      }

      @Override
      public void visitCall(Hir.Call call) {
        final var arguments = call.arguments();
        assertAll(
          () -> assertEquals(lifted.ty(), Tys.getCallableSignature(call.target())),
          () -> assertEquals(2, arguments.length),
          () -> assertEquals("argument", assertInstanceOf(Hir.Identifier.class, arguments[0].value()).name()),
          () -> assertEquals("captured", assertInstanceOf(Hir.Identifier.class, arguments[1].value()).name())
        );
        HirVisitor.super.visitCall(call);
      }
    });
    final var liftedDeclarations = new ArrayList<Hir.Dec>();
    root.visit(new HirVisitor() {
      @Override
      public void visitAssignment(final Hir.Assignment assignment) {
        if (assignment.rhs() == lifted && assignment.lhs() instanceof Hir.Dec declaration) {
          liftedDeclarations.add(declaration);
        }
        HirVisitor.super.visitAssignment(assignment);
      }
    });
    assertEquals(1, liftedDeclarations.size());
    assertAll(
      () -> assertEquals(Ty.VOID, liftedDeclarations.getFirst().ty()),
      () -> assertEquals(lifted.ty(), liftedDeclarations.getFirst().resolvedTy())
    );
  }

  private List<Hir.Function> functions(Hir.Expression expression) {
    final var result = new ArrayList<Hir.Function>();
    expression.visit(new HirVisitor() {
      @Override
      public void visitFunction(Hir.Function function) {
        result.add(function);
        HirVisitor.super.visitFunction(function);
      }
    });
    return result;
  }

  private void assertLocalBindings(Hir.Function function) {
    final Set<Hir.Expression> locals = Collections.newSetFromMap(new IdentityHashMap<>());
    final var references = new ArrayList<Hir.Identifier>();
    function.visit(new HirVisitor() {
      @Override
      public void visitFunctionSignatureReturnType(final Hir.DynamicTy annotation) {
      }

      @Override
      public void visitParameter(Hir.Parameter parameter) {
        locals.add(parameter);
        assertAll(
          () -> assertEquals(Ty.VOID, parameter.ty()),
          () -> assertNotNull(parameter.resolvedTy()),
          () -> assertSame(parameter.resolvedTy(), Tys.getBindingTy(parameter))
        );
      }

      @Override
      public void visitDec(Hir.Dec declaration) {
        locals.add(declaration);
      }

      @Override
      public void visitIdentifier(Hir.Identifier identifier) {
        if (!(identifier.ty() instanceof TyFn)) {
          references.add(identifier);
        }
      }
    });
    assertFalse(references.isEmpty());
    for (final var reference : references) {
      assertTrue(locals.contains(reference.target()), "Nonlocal binding remains: " + reference.name());
      if (reference.target() instanceof Hir.Parameter parameter) {
        assertSame(parameter.resolvedTy(), reference.ty());
      }
    }
    final var parameters = function.signature().parameters();
    for (var i = 0; i < parameters.length; i++) {
      assertEquals(parameters[i].resolvedTy(), function.ty().parameters()[i].ty());
    }
  }

  @Test
  void liftedBodyUsesCaptureParameterAndCallerPassesOriginalDeclaration() {
    final var root = Inf.codeToThir("val x = 7; val fn = () => x + x; fn()").root();
    final var lifted = functions(root).getFirst();
    assertEquals(1, lifted.signature().parameters().length);
    assertLocalBindings(lifted);

    final var calls = new ArrayList<Hir.Call>();
    final var declarations = new ArrayList<Hir.Dec>();
    root.visit(new HirVisitor() {
      @Override
      public void visitCall(Hir.Call call) {
        calls.add(call);
        HirVisitor.super.visitCall(call);
      }

      @Override
      public void visitDec(Hir.Dec declaration) {
        if (declaration.lexeme().name().equals("x")) {
          declarations.add(declaration);
        }
      }
    });
    assertEquals(1, calls.size());
    assertEquals(1, declarations.size());
    final var argument = assertInstanceOf(Hir.Identifier.class, calls.getFirst().arguments()[0].value());
    assertSame(declarations.getFirst(), argument.target());
    assertNotSame(lifted.signature().parameters()[0], argument.target());
    assertAll(
      () -> assertEquals(Ty.VOID, declarations.getFirst().ty()),
      () -> assertEquals(declarations.getFirst().resolvedTy(), argument.ty()),
      () -> assertEquals(declarations.getFirst().resolvedTy(), lifted.signature().parameters()[0].resolvedTy())
    );
  }

  @Test
  void nestedLiftingPropagatesCapturesThroughCallerParameters() {
    final var root = Inf.codeToThir(
      "val x = 7; val outer = (p: int) => { val inner = (q: int) => x + p + q; inner(1); }; outer(2)"
    ).root();
    final var lifted = functions(root);

    assertEquals(2, lifted.size());
    for (final var function : lifted) {
      assertLocalBindings(function);
    }
    assertEquals(3, lifted.get(0).signature().parameters().length);
    assertEquals(2, lifted.get(1).signature().parameters().length);
  }

  @Test
  void inlineLiftedCallReceivesItsCapture() {
    final var declaration = new Hir.Dec(
      new Hir.Lexeme("x"), Hir.MutabilityKind.IMMUTABLE, new Hir.DynamicTy(Ty.INTEGER)
    );
    final var function = new Hir.Function(
      new Hir.FunctionSignature(new Hir.Parameter[0], false, new Hir.DynamicTy(Ty.INFER), null),
      new Hir.Return(new Hir.Identifier(declaration.lexeme().name(), declaration))
    );
    final var program = new Hir.Program(new Hir.Expressions(new Hir.Expression[]{
      new Hir.Assignment(declaration, new Hir.Literal("7", Ty.INTEGER)),
      new Hir.Return(new Hir.Call(function, new Hir.Argument[0]))
    }));
    HirTyCommonVisitorPass.pass(program);
    final var root = HirLambdaLiftingTransformerPass.pass(program);
    final var lifted = functions(root);
    assertEquals(1, lifted.size());
    assertLocalBindings(lifted.getFirst());

    root.visit(new HirVisitor() {
      @Override
      public void visitCall(Hir.Call call) {
        final var arguments = call.arguments();
        assertEquals(1, arguments.length);
        assertSame(declaration, assertInstanceOf(Hir.Identifier.class, arguments[0].value()).target());
      }
    });
  }

  @Test
  void nestedCallArgumentsAreRewrittenBeforeOuterCall() {
    final var root = Inf.codeToThir(
      "val x = 7; val first = () => x; val second = (y: int) => x + y; second(first())"
    ).root();
    final var calls = new ArrayList<Hir.Call>();
    root.visit(new HirVisitor() {
      @Override
      public void visitCall(Hir.Call call) {
        calls.add(call);
        HirVisitor.super.visitCall(call);
      }
    });

    assertEquals(2, calls.size());
    assertEquals(2, calls.get(0).arguments().length);
    assertEquals(1, calls.get(1).arguments().length);
    for (final var function : functions(root)) {
      assertLocalBindings(function);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val S = struct { val a: int; }; val make = () => new heap S { a = 1; }; make()",
    "val S = struct { val a: int; }; val make = (): S => new heap S { a = 1; }; make()",
    "val S = struct { val a: int; }; val make = (): (S,) => (new heap S { a = 1; },); make()"
  })
  void given__struct_type_in_constructor_or_return__when__lifted__then__type_alias_is_not_a_runtime_capture(String code) {
    final var root = Inf.codeToThir(code).root();
    final var constructors = functions(root);

    assertEquals(1, constructors.size());
    assertEquals(0, constructors.getFirst().signature().parameters().length);
  }

  @Test
  void constructorFieldsStillCaptureRuntimeValuesWithoutTypeAliasOrAllocator() {
    final var root = Inf.codeToThir(
      "val S = struct { val a: int; }; val x = 7; val make = () => new heap S { a = x; }; make()"
    ).root();
    final var lifted = functions(root).getFirst();

    assertEquals(1, lifted.signature().parameters().length);
    final var capture = lifted.signature().parameters()[0];
    assertEquals("x", capture.lexeme().name());
    final var creations = new ArrayList<Hir.NewByBlock>();
    lifted.body().visit(new HirVisitor() {
      @Override
      public void visitNewByBlock(Hir.NewByBlock creation) {
        creations.add(creation);
      }
    });
    assertEquals(1, creations.size());
    final var fieldValue = assertInstanceOf(Hir.Identifier.class, creations.getFirst().fields()[0].rhs());
    assertSame(capture, fieldValue.target());
    final var typeName = assertInstanceOf(Hir.Identifier.class, creations.getFirst().target());
    assertInstanceOf(Hir.Dec.class, typeName.target());
    assertEquals("S", typeName.name());
  }

  @Test
  void callableParameterIsCapturedByIdentity() {

    final var root = Inf.codeToThir("""
      (fn: (): int) => {
        return (() => {
          return fn();
        })();
      }
      """).root();

    // TODO: Lexer needs to be fixed! It should not require the `(,,,)()` syntax to be valid, right now "target" for call becomes the block!

    final var functions = functions(root);
    assertEquals(2, functions.size());

    for (final var function : functions) {
      assertEquals(1, function.signature().parameters().length);
    }

    final var lifted = functions.getFirst();
    final var capture = lifted.signature().parameters()[0];
    final var liftedBody = assertInstanceOf(Hir.Return.class, lifted.body());
    final var innerCall = assertInstanceOf(Hir.Call.class, liftedBody.expression());
    final var innerCallTarget = assertInstanceOf(Hir.Identifier.class, innerCall.target());

    assertSame(capture, innerCallTarget.target());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val implementation = () => 7; val alias = implementation; val run = () => alias(); run()",
    "var alias = () => 7; val run = () => alias(); run()"
  })
  void callableLocalBindingsAreCaptured(String code) {
    final var root = Inf.codeToThir(code).root();
    final var lifted = functions(root).getFirst();

    assertEquals(1, lifted.signature().parameters().length);

    final var capture = lifted.signature().parameters()[0];
    assertEquals("alias", capture.lexeme().name());
    assertAll(
      () -> assertEquals(Ty.VOID, capture.ty()),
      () -> assertInstanceOf(TyFn.class, capture.resolvedTy()),
      () -> assertEquals(capture.resolvedTy(), lifted.ty().parameters()[0].ty())
    );

    final var call = assertInstanceOf(Hir.Call.class, assertInstanceOf(Hir.Return.class, lifted.body()).expression());
    assertSame(capture, assertInstanceOf(Hir.Identifier.class, call.target()).target());
  }

  @Test
  void directImmutableFunctionDeclarationsRemainStatic() {
    final var root = Inf.codeToThir(
      "val implementation = () => 7; val run = () => implementation(); run()"
    ).root();
    final var definitions = functions(root);

    assertEquals(2, definitions.size());
    for (final var definition : definitions) {
      assertEquals(0, definition.signature().parameters().length);
    }
  }
}
