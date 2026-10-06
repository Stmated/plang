package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.util.MachineTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

class HirTupleContextVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (uint8,) = (1,); t",
    "val t: (uint8,) = { (1,) }; t",
    "val t: (uint8,) = if (true) then (1,) else (2,); t",
    "val f = (): (uint8,) => { return (1,); }; f()",
    "val f = (t: (uint8,)) => t; f((1,))",
    "val f = (t: (a: uint8,)) => t; f(...((1,),))",
    "val t: ((uint8,),) = ((1,),); t"
  })
  void given__resolved_destination__when__context_is_linked__then__types_and_entry_values_are_not_rewritten(final String code) {
    final var root = prepare(code);
    final var tuples = tuples(root);
    final var beforeTypes = tuples.stream().map(Hir.Tuple::ty).toList();
    final var beforeRoot = root.ty();
    HirTupleContextVisitorPass.pass(root);
    final var contextual = tuples.stream().filter(tuple -> tuple.contextualType() != null).toList();
    final var integers = new ArrayList<Hir.Literal>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal expression) {
        if (expression.ty() != Ty.BOOLEAN) {
          integers.add(expression);
        }
      }

      @Override
      public void visitConvert(final Hir.Convert expression) {
        Assertions.fail("Context linking must not insert conversions");
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertFalse(contextual.isEmpty()),
      () -> Assertions.assertEquals(beforeRoot, root.ty()),
      () -> Assertions.assertEquals(beforeTypes, tuples.stream().map(Hir.Tuple::ty).toList()),
      () -> Assertions.assertTrue(integers.stream().allMatch(literal -> literal.ty().equals(Ty.INTEGER)))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t = (1,); t",
    "val t: (uint8,) = { val unrelated = (2,); (1,) }; t",
    "val t: (uint8,) = { val nested = () => (2,); (1,) }; t",
    "val S = struct { val t: (uint8,); }; new heap S { t = (1,); }",
    "val t = [(1,)]; t"
  })
  void given__unrelated_tuple__when__context_is_linked__then__it_does_not_gain_a_destination(final String code) {
    final var root = prepare(code);
    HirTupleContextVisitorPass.pass(root);
    final var unrelated = tuples(root).stream()
      .filter(tuple -> tuple.children()[0].value() instanceof Hir.Literal literal
        && literal.content().equals(code.contains("(2,)") ? "2" : "1"))
      .toList();
    Assertions.assertAll(
      () -> Assertions.assertFalse(unrelated.isEmpty()),
      () -> Assertions.assertTrue(unrelated.stream().allMatch(tuple -> tuple.contextualType() == null))
    );
  }

  private static ArrayList<Hir.Tuple> tuples(final Hir.Expression root) {
    final var tuples = new ArrayList<Hir.Tuple>();
    root.visit(new HirVisitor() {
      @Override
      public void visitTuple(final Hir.Tuple expression) {
        tuples.add(expression);
        HirVisitor.super.visitTuple(expression);
      }
    });
    return tuples;
  }

  private static Hir.Expression prepare(final String code) {
    final var root = HirTyIdentifierToTyTransformerPass.pass(Inf.codeToHir(code), new MachineTarget(64));
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    return root;
  }
}
