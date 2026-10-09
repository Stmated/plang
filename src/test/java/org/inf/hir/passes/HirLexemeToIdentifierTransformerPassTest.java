package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HirLexemeToIdentifierTransformerPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "value",
    "int",
    "Alias"
  })
  void given__unresolved_word__when__upgraded__then__the_identifier_owns_its_name_without_a_type_or_target(final String name) {
    final var lexeme = new Hir.Lexeme(name);
    final var identifier = assertInstanceOf(Hir.Identifier.class, HirLexemeToIdentifierTransformerPass.pass(lexeme));
    assertAll(
      () -> assertSame(Ty.VOID, lexeme.ty()),
      () -> assertEquals(name, identifier.name()),
      () -> assertEquals(name, identifier.toString()),
      () -> assertNull(identifier.ty()),
      () -> assertNull(identifier.target())
    );
  }

  @Test
  void given__assignments_inside_a_constructor_value__when__raised__then__only_the_constructor_lhs_remains_a_lexeme() {
    final var root = assertInstanceOf(Hir.Program.class, Inf.codeToHir("""
      new heap S { value = { value = other; value; }; }
      """));
    final var construction = assertInstanceOf(Hir.NewByBlock.class,
      assertInstanceOf(Hir.Return.class, root.expressions()).expression());
    final var field = construction.fields()[0];
    final var assignments = new ArrayList<Hir.Assignment>();
    field.rhs().visit(new HirVisitor() {
      @Override
      public void visitAssignment(final Hir.Assignment assignment) {
        assignments.add(assignment);
        HirVisitor.super.visitAssignment(assignment);
      }
    });
    final var nested = assignments.getFirst();
    assertAll(
      () -> assertEquals("value", assertInstanceOf(Hir.Lexeme.class, field.lhs()).name()),
      () -> assertSame(Ty.VOID, field.lhs().ty()),
      () -> assertEquals(1, assignments.size()),
      () -> assertEquals("value", assertInstanceOf(Hir.Identifier.class, nested.lhs()).name()),
      () -> assertEquals("other", assertInstanceOf(Hir.Identifier.class, nested.rhs()).name())
    );
  }

  @Test
  void given__source_names_labels_and_members__when__raised_and_typed__then__only_reference_words_become_identifiers() {
    final var root = Inf.codeToThir("""
      val S = struct { val value: int; };
      val read = (object: S) => object.value;
      val s = new heap S { value = 7; };
      read(object = s)
      """).root();
    final var names = new ArrayList<Hir.Lexeme>();
    final var references = new ArrayList<Hir.Identifier>();
    final var paths = new ArrayList<Hir.DotAccess>();
    final var calls = new ArrayList<Hir.Call>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLexeme(final Hir.Lexeme lexeme) {
        names.add(lexeme);
      }

      @Override
      public void visitIdentifier(final Hir.Identifier identifier) {
        references.add(identifier);
      }

      @Override
      public void visitDotAccess(final Hir.DotAccess path) {
        paths.add(path);
        HirVisitor.super.visitDotAccess(path);
      }

      @Override
      public void visitCall(final Hir.Call call) {
        calls.add(call);
        HirVisitor.super.visitCall(call);
      }
    });
    final var member = paths.getFirst();
    final var label = calls.getFirst().arguments()[0].label();
    assertAll(
      () -> assertFalse(names.isEmpty()),
      () -> assertTrue(names.stream().allMatch(name -> name.ty() == Ty.VOID)),
      () -> assertEquals("value", member.name()),
      () -> assertEquals(root.ty(), member.memberTy()),
      () -> assertEquals("object", label.name()),
      () -> assertSame(Ty.VOID, label.ty()),
      () -> assertEquals(List.of("S", "object", "S", "heap", "read", "s"), references.stream().map(Hir.Identifier::name).toList()),
      () -> assertTrue(references.stream().allMatch(reference -> reference.target() != null)),
      () -> assertEquals(paths.getFirst().memberTy(), root.ty())
    );
  }
}
