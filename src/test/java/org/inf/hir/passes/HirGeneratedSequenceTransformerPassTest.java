package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.Ty;
import org.inf.ty.util.MachineTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HirGeneratedSequenceTransformerPassTest {

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
    final var call = new Hir.Call(signature, new Hir.Argument[0], false, null, null);
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
      () -> assertEquals(Ty.INTEGER, thir.root().ty(), "Program ty should be return type"),
      () -> assertEquals(Ty.INTEGER, thir.root().valueTy(), "Program ty should be return type")
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
