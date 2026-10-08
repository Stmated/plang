package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.Ty;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HirGeneratedSequenceTransformerPassTest {

  @Test
  void given__typed_generated_sequence__when__transformed__then__bindings_signatures_and_tuple_context_survive() {
    final var source = assertInstanceOf(Hir.Program.class, Inf.codeToThir("""
      val read = (t: (a: uint8, b: bool)) => t.a;
      val argument: (a: uint8, b: bool) = (b = true, a = 7);
      read(argument)
      """).root());
    final var assignments = new ArrayList<Hir.Assignment>();
    final var calls = new ArrayList<Hir.Call>();
    source.visit(new HirVisitor() {
      @Override
      public void visitAssignment(final Hir.Assignment assignment) {
        if (assignment.lhs() instanceof Hir.Dec) {
          assignments.add(assignment);
        }
        HirVisitor.super.visitAssignment(assignment);
      }

      @Override
      public void visitCall(final Hir.Call call) {
        calls.add(call);
        HirVisitor.super.visitCall(call);
      }
    });
    final var original = assignments.stream()
      .filter(assignment -> ((Hir.Dec) assignment.lhs()).lexeme().name().equals("argument"))
      .findFirst().orElseThrow();
    final var declaration = assertInstanceOf(Hir.Dec.class, original.lhs());
    final var binding = declaration.resolvedTy();
    final var tuple = assertInstanceOf(Hir.Tuple.class, original.rhs());
    final var completion = tuple.ty();
    final var layout = tuple.contextualType();
    final var call = calls.getFirst();
    final var signature = Tys.getCallableSignature(call.target());
    assertAll(
      () -> assertNotNull(binding),
      () -> assertNotNull(layout),
      () -> assertNotNull(signature)
    );
    final var generated = new Hir.Expressions(new Hir.Expression[]{
      source.expressions(), new Hir.Return(new Hir.Literal("7", Ty.INTEGER)), new Hir.Literal("8", Ty.INTEGER)
    }, Ty.DEADEND, true);

    HirGeneratedSequenceTransformerPass.pass(generated);

    final var retained = assertInstanceOf(Hir.Expressions.class, generated.children()[0]);
    final var replacement = Arrays.stream(retained.children())
      .filter(expression -> expression instanceof Hir.Assignment assignment && assignment.lhs() == declaration)
      .map(Hir.Assignment.class::cast).findFirst().orElseThrow();
    assertAll(
      () -> assertEquals(1, generated.children().length),
      () -> assertNotSame(original, replacement),
      () -> assertSame(original.ty(), replacement.ty()),
      () -> assertSame(declaration, replacement.lhs()),
      () -> assertSame(binding, declaration.resolvedTy()),
      () -> assertSame(tuple, replacement.rhs()),
      () -> assertSame(completion, tuple.ty()),
      () -> assertSame(layout, tuple.contextualType()),
      () -> assertEquals("b", tuple.children()[0].label().name()),
      () -> assertEquals("a", layout.fields()[0].name()),
      () -> assertSame(signature, Tys.getCallableSignature(call.target())),
      () -> assertSame(declaration, assertInstanceOf(Hir.Identifier.class, call.arguments()[0].value()).target())
    );
  }

  @Test
  void sourceStatementsAreRetainedInsideGeneratedSequences() {
    for (final var transfer : List.of(
      new Hir.Return(new Hir.Literal("7", Ty.INTEGER)),
      new Hir.LoopBreak(null),
      new Hir.LoopContinue()
    )) {
      final var source = new Hir.Expressions(new Hir.Expression[]{
        transfer, new Hir.Literal("8", Ty.INTEGER)
      });
      final var generated = new Hir.Expressions(new Hir.Expression[]{
        source, new Hir.Literal("9", Ty.INTEGER)
      }, null, true);

      var raised = new HirToThirRaising(new MachineTarget(64)).raise(new Hir.Program(generated)).root();
      HirTyCommonVisitorPass.pass(raised);
      raised = HirGeneratedSequenceTransformerPass.pass(raised);

      assertEquals(1, generated.children().length);
      assertSame(source, generated.children()[0]);
      assertEquals(2, source.children().length);
      assertEquals(Ty.DEADEND, generated.ty());
    }
  }

  @Test
  void resolvedNonReturningCallStopsGeneratedSequencingEvenInsideAssignment() {
    final var signature = new Hir.FunctionSignature(
      new Hir.Parameter[0], false, new Hir.TyExpr(Ty.DEADEND), null
    );
    final var call = new Hir.Call(signature, new Hir.Argument[0], false, null);
    final var declaration = new Hir.Dec(
      new Hir.Lexeme("value"), Hir.MutabilityKind.MUTABLE, new Hir.TyExpr(Ty.INTEGER)
    );
    final var assignment = new Hir.Assignment(declaration, call);
    final var literal = new Hir.Literal("9", Ty.INTEGER);
    final var generated = new Hir.Expressions(new Hir.Expression[]{
      assignment, literal
    }, null, true);

    final var thir = new HirToThirRaising(new MachineTarget(64)).raise(new Hir.Program(generated));

    assertAll(
      () -> assertEquals(Ty.DEADEND, call.ty()),
      () -> assertEquals(1, generated.children().length)
    );

    final var first = assertInstanceOf(Hir.Assignment.class, generated.children()[0]);

    assertAll(
      () -> assertEquals(Ty.DEADEND, first.ty(), "Assignment ty should be dead end"),
      () -> assertEquals(Ty.DEADEND, first.rhs().ty(), "Assignment RHS ty should be dead end"),
      () -> assertEquals(Ty.DEADEND, thir.root().ty(), "Program has no reachable return or normal completion")
    );
  }

  @Test
  void unreachableUpdateStillUndergoesIdentifierResolution() {
    final var error = assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(
      "for (var i = 0; i < 3; i += missing) { return 7; } return 9;"
    ));

    assertTrue(error.getMessage().contains("missing"));
  }

  @Test
  void unreachableUpdateStillUndergoesTyping() {
    assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir(
      "for (var i = 0; i < 3; i += [1, \"bad\"]) { return 7; } return 9;"
    ));
  }
}
