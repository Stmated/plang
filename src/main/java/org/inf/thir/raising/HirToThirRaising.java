package org.inf.thir.raising;

import lombok.extern.slf4j.Slf4j;
import org.inf.hir.Hir;
import org.inf.hir.passes.*;
import org.inf.ty.util.MachineTarget;

/**
 * TODO: This should be rewritten to use some kind of query system like Rust, eventually.
 *        That way we can easier multi-thread the investigation, and also do it lazily on-demand, and cache more easily.
 */
@Slf4j
public class HirToThirRaising {

  private final MachineTarget machineTarget;

  public HirToThirRaising(MachineTarget machineTarget) {
    this.machineTarget = machineTarget;
  }

  public ThirRaiseResult raise(Hir.Expression e) {

    e = HirTyIdentifierToTyTransformerPass.pass(e, machineTarget);
    HirIdentifierResolverVisitorPass.pass(e, Hir.Identifier::target);
    HirTyCommonVisitorPass.pass(e);
    e = HirGeneratedSequenceTransformerPass.pass(e);
    e = HirLambdaLiftingTransformerPass.pass(e);
    e = HirDependencyReorderingTransformerPass.pass(e);

    // Call investigate on the expression.
    // Then the map inside this raising should contain all relevant types.

    return new ThirRaiseResult(e);
  }
}
