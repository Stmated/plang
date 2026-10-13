package org.inf.thir.raising;

import lombok.extern.slf4j.Slf4j;
import org.inf.hir.Hir;
import org.inf.hir.passes.*;
import org.inf.ty.util.MachineTarget;

import java.util.Random;

/**
 * TODO: This should be rewritten to use some kind of query system like Rust, eventually.
 *        That way we can easier multi-thread the investigation, and also do it lazily on-demand, and cache more easily.
 */
@Slf4j
public class HirToThirRaising {

  private final Random random;
  private final MachineTarget machineTarget;

  public HirToThirRaising(MachineTarget machineTarget) {
    this.random = new Random();
    this.machineTarget = machineTarget;
  }

  public HirToThirRaising(Random random, MachineTarget machineTarget) {
    this.random = random;
    this.machineTarget = machineTarget;
  }

  public ThirRaiseResult raise(Hir.Expression e) {

    HirTupleSyntaxValidationVisitorPass.pass(e);
    e = HirTyIdentifierToTyTransformerPass.pass(e, machineTarget);
    e = HirUnionSyntaxTransformerPass.pass(e, machineTarget);
    HirTyCommonVisitorPass.resolveAvailableTypes(e);
    HirFunctionSignatureValidationVisitorPass.passStandalone(e);
    HirFunctionContextualTypingVisitorPass.pass(e);
    HirFunctionParameterValidationVisitorPass.pass(e);
    HirTyCommonVisitorPass.resolveAvailableTypes(e);
    HirTupleContextVisitorPass.pass(e);
    HirIntegerLiteralTypingVisitorPass.pass(e);
    HirTyCommonVisitorPass.resolveAvailableTypes(e);
    HirTupleSlotConversionVisitorPass.pass(e);
    HirTyCommonVisitorPass.pass(e);
    HirFunctionSignatureValidationVisitorPass.pass(e);
    HirFunctionValidationVisitorPass.pass(e);
    HirTupleValidationVisitorPass.pass(e);
    HirArrayValidationPass.pass(e);
    HirUnionValidationPass.pass(e);
    HirValidationPass.pass(e);
    e = HirGeneratedSequenceTransformerPass.pass(e);
    e = HirLambdaLiftingTransformerPass.pass(this.random, e);
    e = HirDependencyReorderingTransformerPass.pass(e);

    // Call investigate on the expression.
    // Then the map inside this raising should contain all relevant types.

    return new ThirRaiseResult(e);
  }
}
