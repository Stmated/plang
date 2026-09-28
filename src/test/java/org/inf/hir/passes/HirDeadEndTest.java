package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.Ty;
import org.inf.ty.TyUnion;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HirDeadEndTest {

  private Hir.Literal integer() {
    return new Hir.Literal("1", Ty.INTEGER);
  }

  private Hir.Literal literal_true() {
    return new Hir.Literal("true", Ty.BOOLEAN);
  }

  private Hir.Literal string() {
    return new Hir.Literal("value", Ty.STRING);
  }

  private Hir.Function function(Hir.Expression body) {
    return new Hir.Function(
      new Hir.FunctionSignature(new Hir.Parameter[0], false, new Hir.TyExpr(Ty.INFER), null),
      body
    );
  }

  private Hir.ArrayAccess arrayAccess(Hir.Expression index) {
    return new Hir.ArrayAccess(
      new Hir.Array(new Hir.Expression[]{integer()}, new Hir.TyExpr(Ty.INTEGER), null, null, null),
      index,
      null,
      null
    );
  }

  @Test
  void completingAssignmentsHaveVoidType() {
    final var assignment = new Hir.Assignment(arrayAccess(integer()), integer());
    final var compound = new Hir.CompoundAssignment(arrayAccess(integer()), Hir.BinaryOperationKind.ADD, integer());

    HirTyCommonVisitorPass.pass(assignment);
    HirTyCommonVisitorPass.pass(compound);

    assertEquals(Ty.VOID, assignment.ty());
    assertEquals(Ty.VOID, compound.ty());
  }

  @Test
  void assignmentsPropagateNonContinuingRightOperands() {
    final var inner = new Hir.Assignment(arrayAccess(integer()), new Hir.Return(integer()));
    final var assignment = new Hir.Assignment(arrayAccess(integer()), inner);
    final var compound = new Hir.CompoundAssignment(
      arrayAccess(integer()), Hir.BinaryOperationKind.ADD, new Hir.Return(integer())
    );

    HirTyCommonVisitorPass.pass(assignment);
    HirTyCommonVisitorPass.pass(compound);

    assertAll(
      () -> assertEquals(Ty.DEADEND, inner.ty()),
      () -> assertEquals(Ty.DEADEND, assignment.ty()),
      () -> assertEquals(Ty.DEADEND, compound.ty())
    );
  }

  @Test
  void assignmentsPropagateNonContinuingTargetEvaluation() {
    final var assignment = new Hir.Assignment(arrayAccess(new Hir.Return(integer())), integer());
    HirTyCommonVisitorPass.pass(assignment);
    assertEquals(Ty.DEADEND, assignment.ty());
    assertEquals(Ty.INTEGER, assignment.lhs().valueTy());
  }

  @Test
  void compoundAssignmentPropagateNonContinuingTargetEvaluation() {
    final var compound = new Hir.CompoundAssignment(
      arrayAccess(new Hir.Return(integer())),
      Hir.BinaryOperationKind.ADD,
      integer()
    );
    HirTyCommonVisitorPass.pass(compound);

    assertAll(
      () -> assertEquals(Ty.DEADEND, compound.ty()),
      () -> assertEquals(Ty.INTEGER, compound.target().valueTy()),
      () -> assertEquals(Ty.DEADEND, compound.valueTy())
    );
  }

  @Test
  void returningArmDoesNotContributeToConditionalValue() {
    Hir.Expression pass = new Hir.Return(string());
    Hir.Expression fail = integer();
    final var branch = new Hir.Conditional(literal_true(), pass, fail, null, null);
    final var fn = function(new Hir.Return(branch));

    HirTyCommonVisitorPass.pass(fn);

    assertEquals(Ty.INTEGER, branch.ty());
    assertEquals(Tys.union(Ty.STRING, Ty.INTEGER), fn.signature().returnType().ty());
  }

  @Test
  void twoReturningArmsHaveNoContinuingValueButRetainReturnOperands() {
    Hir.Expression pass = new Hir.Return(integer());
    Hir.Expression fail = new Hir.Return(string());
    final var branch = new Hir.Conditional(literal_true(), pass, fail, null, null);
    final var fn = function(new Hir.Return(branch));

    HirTyCommonVisitorPass.pass(fn);

    assertEquals(Ty.DEADEND, branch.ty());
    assertEquals(Tys.union(Ty.INTEGER, Ty.STRING), fn.signature().returnType().ty());
  }

  @Test
  void missingElseAddsVoidOnlyToContinuingValue() {
    Hir.Expression pass1 = integer();
    final var valueBranch = new Hir.Conditional(literal_true(), pass1, null, null, null);
    Hir.Expression pass = new Hir.Return(integer());
    final var returningBranch = new Hir.Conditional(literal_true(), pass, null, null, null);

    HirTyCommonVisitorPass.pass(valueBranch);
    HirTyCommonVisitorPass.pass(returningBranch);

    assertEquals(Tys.union(Ty.INTEGER, Ty.VOID), valueBranch.ty());
    assertEquals(Ty.VOID, returningBranch.ty());
  }

  @Test
  void loopTransfersDoNotContributeValuesToConditionalMerge() {
    Hir.Expression pass1 = new Hir.LoopBreak(integer());
    Hir.Expression fail1 = string();
    final var breaking = new Hir.Conditional(literal_true(), pass1, fail1, null, null);
    Hir.Expression pass = new Hir.LoopContinue();
    Hir.Expression fail = integer();
    final var continuing = new Hir.Conditional(literal_true(), pass, fail, null, null);

    HirTyCommonVisitorPass.pass(breaking);
    HirTyCommonVisitorPass.pass(continuing);

    assertEquals(Ty.STRING, breaking.ty());
    assertEquals(Ty.INTEGER, continuing.ty());
  }

  @Test
  void nonContinuingMiddleChildMakesSequenceDeadEnd() {
    final var before = integer();
    final var transfer = new Hir.Return(integer());
    final var after = string();
    final var sequence = new Hir.Expressions(new Hir.Expression[]{before, transfer, after});

    HirTyCommonVisitorPass.pass(sequence);

    assertEquals(Ty.INTEGER, before.ty());
    assertEquals(Ty.DEADEND, transfer.ty());
    assertEquals(Ty.STRING, after.ty());
    assertEquals(Ty.DEADEND, sequence.ty());
  }

  @Test
  void returningConditionalArmDoesNotMakeSequenceDeadEnd() {
    final var branch = new Hir.Conditional(literal_true(), new Hir.Return(integer()), integer(), null, null);
    final var after = string();
    final var sequence = new Hir.Expressions(new Hir.Expression[]{branch, after});

    HirTyCommonVisitorPass.pass(sequence);

    assertEquals(Ty.DEADEND, branch.pass().ty());
    assertEquals(Ty.INTEGER, branch.ty());
    assertEquals(Ty.STRING, sequence.ty());
  }

  @Test
  void unreachableTrailingValuesDoNotChangeFunctionReturnType() {
    final var fn = function(new Hir.Expressions(new Hir.Expression[]{
      new Hir.Return(integer()),
      new Hir.Return(string())
    }));

    HirTyCommonVisitorPass.pass(fn);

    assertEquals(Ty.INTEGER, fn.signature().returnType().ty());
    assertEquals(Ty.DEADEND, fn.body().ty());
  }

  @Test
  void nestedFunctionsDoNotLeakTheirReturns() {
    final var nested = function(new Hir.Return(string()));
    final var outer = function(new Hir.Expressions(new Hir.Expression[]{
      nested,
      new Hir.Return(integer())
    }));

    HirTyCommonVisitorPass.pass(outer);

    assertEquals(Ty.STRING, nested.signature().returnType().ty());
    assertEquals(Ty.INTEGER, outer.signature().returnType().ty());
  }

  @Test
  void conditionalDeclarationUsesOnlyContinuingArmType() {
    final var declaration = new Hir.Dec(
      new Hir.Lexeme("value"),
      Hir.MutabilityKind.IMMUTABLE,
      new Hir.TyExpr(Ty.INFER)
    );
    final var fn = function(new Hir.Expressions(new Hir.Expression[]{
      new Hir.Assignment(
        declaration,
        new Hir.Conditional(literal_true(), new Hir.Return(string()), integer(), null, null)
      ),
      new Hir.Return(new Hir.Identifier(declaration.lexeme(), declaration))
    }));

    HirTyCommonVisitorPass.pass(fn);

    assertEquals(Ty.INTEGER, declaration.valueTy());
    assertEquals(Tys.union(Ty.STRING, Ty.INTEGER), fn.signature().returnType().ty());
  }

  @Test
  void loopExitValuesBelongToLoopRatherThanBranchMerge() {
    final var branch = new Hir.Conditional(
      literal_true(),
      new Hir.LoopBreak(integer()),
      new Hir.LoopContinue()
    );
    final var loop = new Hir.Loop(branch);

    HirTyCommonVisitorPass.pass(loop);

    assertAll(
      () -> assertEquals(Ty.DEADEND, branch.ty()),
      () -> assertEquals(Ty.INTEGER, loop.ty())
    );
  }

  @Test
  void divergingPredicateDoesNotContributeUnexecutedArmReturns() {
    final var branch = new Hir.Conditional(
      new Hir.Return(integer()),
      new Hir.Return(string()),
      new Hir.Return(literal_true()),
      null, null
    );
    final var fn = function(branch);

    HirTyCommonVisitorPass.pass(fn);

    assertEquals(Ty.DEADEND, branch.ty());
    assertEquals(Ty.INTEGER, fn.signature().returnType().ty());
  }

  @Test
  void shortCircuitMayContinueWhenItsRightOperandReturns() {
    final var logical = new Hir.BinaryOperation(
      literal_true(),
      Hir.BinaryOperationKind.AND,
      new Hir.Return(integer()),
      null,
      null
    );
    final var fn = function(new Hir.Return(logical));

    HirTyCommonVisitorPass.pass(fn);

    assertEquals(Ty.BOOLEAN, logical.ty());
    assertEquals(Tys.union(Ty.INTEGER, Ty.BOOLEAN), fn.signature().returnType().ty());
  }

  @Test
  void programTypeDescribesReturnedValueRatherThanControlFlow() {
    final var program = new Hir.Program(new Hir.Return(integer()));

    HirTyCommonVisitorPass.pass(program);

    assertEquals(Ty.INTEGER, program.ty());
  }

  @Test
  void parsedConditionalDeclarationAndFunctionReturnAgree() {
    final var root = Inf.codeToThir("val fn = (x: int) => { val y = if (x == 0) { return 7; } else 8; return y; }; fn(1)").root();

    HirTyCommonVisitorPass.pass(root);

    assertEquals(Ty.INTEGER, root.ty());
    assertEquals(Ty.INTEGER, root.valueTy());
  }

  @Test
  void parsedMissingElseRetainsTaggedVoidAlternative() {
    final var root = Inf.codeToThir("if (1 == 1) 42").root();

    HirTyCommonVisitorPass.pass(root);

    assertEquals(Tys.union(Ty.INTEGER, Ty.VOID), root.ty());
    assertEquals(root.ty(), root.valueTy());
  }

  @Test
  void indexedAssignmentLowersToArrayAccessTarget() {
    final var root = Inf.codeToThir("var a = [1, 2]; a[0] = 9; return a[0];").root();
    final var stores = new java.util.ArrayList<Hir.Assignment>();
    root.visit(new HirVisitor() {
      @Override
      public void visitAssignment(Hir.Assignment assignment) {
        if (assignment.lhs() instanceof Hir.ArrayAccess) {
          stores.add(assignment);
        }
        HirVisitor.super.visitAssignment(assignment);
      }
    });

    assertEquals(1, stores.size());
    assertEquals(Ty.INTEGER, stores.getFirst().lhs().ty());
    assertEquals(Ty.INTEGER, root.ty());
  }

  @Test
  void generatedLoopTailIsRemovedOnlyAfterTyping() {
    final var hir = Inf.codeToHir("for (var i = 0; i < 3; i += 1) { return 7; } return 9;");
    final var loops = new java.util.ArrayList<Hir.Loop>();
    hir.visit(new HirVisitor() {
      @Override
      public void visitLoop(Hir.Loop loop) {
        loops.add(loop);
      }
    });

    assertEquals(1, loops.size());
    final var branch = assertInstanceOf(Hir.Conditional.class, loops.getFirst().body());
    final var iteration = assertInstanceOf(Hir.Expressions.class, branch.pass());
    assertTrue(iteration.generated());
    assertEquals(2, iteration.children().length);
    assertInstanceOf(Hir.CompoundAssignment.class, iteration.children()[1]);

    new HirToThirRaising(new MachineTarget(64)).raise(hir);

    assertEquals(1, iteration.children().length);
    assertEquals(Ty.DEADEND, iteration.children()[0].ty());
  }

  @Test
  void unionFlatteningPreservesFirstSeenTagOrderAndOmitsBottom() {
    final var union = Tys.union(
      Ty.DEADEND,
      new TyUnion(new Ty[]{Ty.INTEGER, Ty.STRING}),
      Ty.INTEGER,
      new TyUnion(new Ty[]{Ty.VOID, Ty.STRING})
    );

    assertArrayEquals(new Ty[]{Ty.INTEGER, Ty.STRING, Ty.VOID}, assertInstanceOf(TyUnion.class, union).types());
    assertEquals(Ty.DEADEND, Tys.union(Ty.DEADEND, Ty.DEADEND));
  }

  @Test
  void unionsCompareStructurallyWithoutCopyingTheirTypes() {
    final var source = new Ty[]{Ty.INTEGER, Ty.STRING};
    final var union = new TyUnion(source);
    final var same = new TyUnion(new Ty[]{Ty.INTEGER, Ty.STRING});

    assertSame(source, union.types());
    assertEquals(same, union);
    assertEquals(same.hashCode(), union.hashCode());
    assertArrayEquals(new Ty[]{Ty.INTEGER, Ty.STRING}, union.types());
  }
}
