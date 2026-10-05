package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
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
          assertEquals(lifted.ty(), declaration.valueTy());
        }
        HirVisitor.super.visitAssignment(assignment);
      }

      @Override
      public void visitCall(Hir.Call call) {
        assertAll(
          () -> assertEquals(lifted.ty(), call.target().valueTy()),
          () -> assertEquals(2, call.arguments().length),
          () -> assertEquals("argument", assertInstanceOf(Hir.Identifier.class, call.arguments()[0].value()).lexeme().name()),
          () -> assertEquals("captured", assertInstanceOf(Hir.Identifier.class, call.arguments()[1].value()).lexeme().name())
        );
        HirVisitor.super.visitCall(call);
      }
    });
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
      public void visitParameter(Hir.Parameter parameter) {
        locals.add(parameter);
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
      assertTrue(locals.contains(reference.target()), "Nonlocal binding remains: " + reference.lexeme().name());
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
      new Hir.Lexeme("x"), Hir.MutabilityKind.IMMUTABLE, new Hir.TyExpr(Ty.INTEGER)
    );
    final var function = new Hir.Function(
      new Hir.FunctionSignature(new Hir.Parameter[0], false, new Hir.TyExpr(Ty.INFER), null),
      new Hir.Return(new Hir.Identifier(declaration.lexeme(), declaration))
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
        assertEquals(1, call.arguments().length);
        assertSame(declaration, assertInstanceOf(Hir.Identifier.class, call.arguments()[0].value()).target());
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
    assertEquals("S", typeName.lexeme().name());
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
    assertInstanceOf(TyFn.class, capture.ty());

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
