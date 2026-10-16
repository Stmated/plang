package org.inf.hir.util;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;
import org.inf.hir.HirVisitor;
import org.inf.hir.passes.HirTyCommonVisitorPass;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValue;
import org.inf.ty.TyValueArray;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirExprsTest {

  static Stream<Arguments> noncompletingBindings() {
    return Stream.of(
      Arguments.of("val v = { return true; }; v", Ty.INFER, Ty.INFER),
      Arguments.of("val v: bool = { return true; }; v", Ty.BOOLEAN, Ty.BOOLEAN),
      Arguments.of("val v = [{ return true; }, 7]; v", Ty.INFER, new TyValueArray(Ty.INTEGER, 2)),
      Arguments.of("val v: [;2] = [{ return true; }, 7]; v",
        new TyValueArray(Ty.INFER, 2), new TyValueArray(Ty.INTEGER, 2)),
      Arguments.of("val v = ({ return true; }, 7); v", Ty.INFER,
        new TyStruct(new TyField[]{new TyField(null, Ty.BOOLEAN), new TyField(null, Ty.INTEGER)}, true)),
      Arguments.of("val other = { return true; }; val v = other; v", Ty.INFER, Ty.INFER)
    );
  }

  @ParameterizedTest
  @MethodSource("noncompletingBindings")
  void given__noncompleting_initializer__when__binding_is_queried__then__flow_structure_and_source_requirements_remain_separate(
    final String code, final Ty constraint, final Ty structural
  ) {
    final var root = Inf.codeToThir(code).root();
    final var declaration = declaration(root);
    final var reference = nodes(root, Hir.Identifier.class).stream()
      .filter(identifier -> identifier.target() == declaration).findFirst().orElseThrow();
    final var binding = Hirs.getBindingTy(declaration);
    assertAll(
      () -> assertSame(Ty.VOID, declaration.ty().completionTy()),
      () -> assertEquals(structural, declaration.ty().structuralTy()),
      () -> assertEquals(constraint, declaration.ty().declaredConstraintTy()),
      () -> assertEquals(constraint, declaration.typeAnnotation().ty()),
      () -> assertSame(Ty.DEADEND, declaration.initialization().ty().completionTy()),
      () -> assertTrue(Tys.isDeadend(binding)),
      () -> assertEquals(structural, Tys.structuralTy(binding)),
      () -> assertNull(Tys.declaredConstraintTy(binding)),
      () -> assertSame(binding, reference.ty()),
      () -> assertSame(Ty.BOOLEAN, root.ty()),
      () -> assertNull(Hirs.getMemberReceiverLayout(reference)),
      () -> assertSame(Ty.DEADEND, Hirs.getIndexingReceiverTy(reference))
    );
  }

  @Test
  void given__widening_initializer__when__binding_is_queried__then__destination_not_initializer_type_is_read() {
    final var root = Inf.codeToThir("val v: uint16 = 255u8; v").root();
    final var declaration = declaration(root);
    final var expected = Tys.fromString("uint16", MachineTarget.T64);
    assertAll(
      () -> assertEquals(expected, Hirs.getBindingTy(declaration)),
      () -> assertEquals(expected, declaration.ty().structuralTy()),
      () -> assertEquals(expected, declaration.ty().declaredConstraintTy()),
      () -> assertNotEquals(expected, declaration.initialization().rhs().ty()),
      () -> assertEquals(expected, root.ty())
    );
  }

  @Test
  void given__noncompleting_callable_initializer__when__validated__then__its_destination_uses_structure_not_flow() {
    final var root = Inf.codeToThir("""
      val Fn = (): int;
      val make = (flag: bool): Fn => (() => 7);
      val v: Fn = make({ return true; });
      """).root();
    final var declaration = declaration(root);
    final var binding = Hirs.getBindingTy(declaration);
    assertAll(
      () -> assertTrue(Tys.isDeadend(binding)),
      () -> assertSame(declaration.ty().structuralTy(), Tys.structuralTy(binding)),
      () -> assertSame(declaration.ty().structuralTy(), Hirs.getCallableSignature(declaration)),
      () -> assertSame(declaration.ty().declaredConstraintTy(), Hirs.getAssignmentContextTy(declaration)),
      () -> assertSame(Ty.BOOLEAN, root.ty())
    );

    assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir("""
      val IntFn = (): int;
      val BoolFn = (): bool;
      val make = (flag: bool): BoolFn => (() => true);
      val v: IntFn = make({ return true; });
      """));
  }

  @Test
  void given__replaced_initializer__when__retyped__then__initialization_completion_and_binding_queries_refresh() {
    final var root = Inf.codeToThir("val v: bool = true; v").root();
    final var declaration = declaration(root);
    final var initialization = declaration.initialization();
    final var originalValue = initialization.rhs();
    final var storedType = declaration.ty();
    final var transfer = nodes(Inf.codeToThir("{ return true; }").root(), Hir.Block.class).getFirst();
    initialization.rhs(transfer);
    assertAll(
      () -> assertSame(storedType, declaration.ty()),
      () -> assertSame(Ty.VOID, initialization.ty()),
      () -> assertSame(Ty.BOOLEAN, Hirs.getBindingTy(declaration)),
      () -> assertSame(Ty.BOOLEAN, Hirs.getBindingStructureTy(declaration))
    );

    HirTyCommonVisitorPass.pass(root);
    assertAll(
      () -> assertSame(storedType, declaration.ty()),
      () -> assertSame(Ty.DEADEND, initialization.ty()),
      () -> assertTrue(Tys.isDeadend(Hirs.getBindingTy(declaration)))
    );

    initialization.rhs(originalValue);
    assertTrue(Tys.isDeadend(Hirs.getBindingTy(declaration)));
    HirTyCommonVisitorPass.pass(root);
    assertAll(
      () -> assertSame(storedType, declaration.ty()),
      () -> assertSame(Ty.VOID, initialization.ty()),
      () -> assertSame(Ty.BOOLEAN, Hirs.getBindingTy(declaration))
    );
  }

  @Test
  void given__transformed_assignment__when__queried_and_rendered__then__the_live_link_is_nonowning_and_invisible() {
    final var root = Inf.codeToThir("val v: bool = true; v").root();
    final var declaration = declaration(root);
    final var original = declaration.initialization();
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    final var declarationBefore = printer.render(declaration);
    final var hash = declaration.hashCode();
    final var transformed = root.transform(new HirTransformer() {});
    final var replacement = nodes(transformed, Hir.Assignment.class).getFirst();
    assertAll(
      () -> assertNotSame(original, replacement),
      () -> assertSame(declaration, replacement.lhs()),
      () -> assertSame(replacement, declaration.initialization()),
      () -> assertEquals(before, printer.render(transformed)),
      () -> assertEquals(1, nodes(transformed, Hir.Dec.class).size()),
      () -> assertEquals(1, nodes(transformed, Hir.Literal.class).size()),
      () -> assertEquals(hash, declaration.hashCode())
    );

    replacement.rhs(nodes(Inf.codeToThir("{ return true; }").root(), Hir.Block.class).getFirst());
    HirTyCommonVisitorPass.pass(transformed);
    final var sameDeclaration = declaration(Inf.codeToThir("val v: bool = false; v").root());
    assertAll(
      () -> assertTrue(Tys.isDeadend(Hirs.getBindingTy(declaration))),
      () -> assertEquals(declarationBefore, printer.render(declaration)),
      () -> assertEquals(sameDeclaration, declaration),
      () -> assertEquals(hash, declaration.hashCode())
    );

    replacement.lhs(new Hir.Identifier("v", declaration));
    assertAll(
      () -> assertNull(declaration.initialization()),
      () -> assertSame(Ty.BOOLEAN, Hirs.getBindingTy(declaration))
    );
  }

  @Test
  void given__reassignment_after_initialization__when__binding_is_queried__then__only_initialization_supplies_flow() {
    final var root = Inf.codeToThir("var v: bool = true; v = { return true; }; v").root();
    final var declaration = declaration(root);
    final var assignments = nodes(root, Hir.Assignment.class);
    assertAll(
      () -> assertSame(assignments.getFirst(), declaration.initialization()),
      () -> assertSame(Ty.VOID, declaration.initialization().ty()),
      () -> assertSame(Ty.DEADEND, assignments.getLast().ty()),
      () -> assertSame(Ty.BOOLEAN, Hirs.getBindingTy(declaration))
    );
  }

  @Test
  void given__recursive_initializer_reference__when__binding_is_queried__then__no_completion_evidence_is_invented() {
    final var root = Inf.codeToThir("val v = v; v").root();
    final var declaration = declaration(root);
    assertAll(
      () -> assertSame(Ty.INFER, Hirs.getBindingTy(declaration)),
      () -> assertSame(Ty.INFER, declaration.ty().declaredConstraintTy()),
      () -> assertSame(Ty.VOID, declaration.ty().completionTy()),
      () -> assertSame(declaration,
        assertInstanceOf(Hir.Identifier.class, declaration.initialization().rhs()).target())
    );
  }

  @Test
  void given__formerly_noncompleting_initializer__when__changed_to_a_recursive_reference_and_retyped__then__old_flow_is_not_reused() {
    final var root = Inf.codeToThir("val v: bool = { return true; }; v").root();
    final var declaration = declaration(root);
    final var reference = nodes(root, Hir.Identifier.class).stream()
      .filter(identifier -> identifier.target() == declaration).findFirst().orElseThrow();
    declaration.initialization().rhs(reference);

    HirTyCommonVisitorPass.pass(root);

    assertAll(
      () -> assertSame(Ty.VOID, declaration.initialization().ty()),
      () -> assertSame(Ty.BOOLEAN, Hirs.getBindingTy(declaration)),
      () -> assertSame(Ty.BOOLEAN, reference.ty())
    );
  }

  @Test
  void given__long_declaration_chain__when__typed__then__binding_queries_do_not_revisit_every_initializer() {
    final var code = new StringBuilder("val a0 = 7;");
    for (var i = 1; i < 6_400; i++) {
      code.append("val a%d = a%d;".formatted(i, i - 1));
    }
    code.append("a6399");

    final var root = Inf.codeToThir(code.toString()).root();
    final var declarations = nodes(root, Hir.Dec.class);
    final var initialization = declarations.getFirst().initialization();
    final var value = assertInstanceOf(Hir.Literal.class, initialization.rhs());
    final var reads = new AtomicInteger();
    initialization.rhs(new Hir.Literal(value.content(), value.ty()) {
      @Override
      public TyValue ty() {
        reads.incrementAndGet();
        return super.ty();
      }
    });

    for (final var declaration : declarations) {
      assertSame(Ty.INTEGER, Hirs.getBindingTy(declaration));
      assertSame(Ty.INTEGER, Hirs.getBindingStructureTy(declaration));
    }
    assertAll(
      () -> assertSame(Ty.INTEGER, root.ty()),
      () -> assertEquals(0, reads.get())
    );
  }

  private static Hir.Dec declaration(final Hir.Expression root) {
    return nodes(root, Hir.Dec.class).stream()
      .filter(declaration -> declaration.lexeme().name().equals("v")).findFirst().orElseThrow();
  }

  private static <T extends Hir.Expression> List<T> nodes(final Hir.Expression root, final Class<T> nodeType) {
    final var found = new ArrayList<T>();
    root.visit(new HirVisitor() {
      @Override
      public void visitChild(final Hir.Expression expr) {
        if (nodeType.isInstance(expr)) {
          found.add(nodeType.cast(expr));
        }
        HirVisitor.super.visitChild(expr);
      }
    });
    assertFalse(found.isEmpty(), () -> "Missing source node: " + nodeType.getSimpleName());
    return found;
  }
}
