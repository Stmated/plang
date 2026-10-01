package org.inf.ast.util;

import org.inf.ast.Ast;
import org.inf.ast.AstVisitor;

import java.util.Arrays;

public class ToStringTreeAstVisitor implements AstVisitor<String> {

  private static final String INDENT = "  ";

  @Override
  public String visit(final Ast.Expression expr) {
    return AstVisitor.super.visit(expr);
  }

  @Override
  public String aggregate(final String a, final String b) {
    return node(null, a, b);
  }

  @Override
  public String noValue() {
    return "";
  }

  @Override
  public String visitString(final String str) {
    return quote(str);
  }

  @Override
  public <E extends Enum<E>> String visitEnum(final Enum<E> e) {
    return (e == null) ? "null" : e.name();
  }

  @Override
  public String visitAssignment(final Ast.Assignment expr) {
    return node("Assignment", visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  public String visitBecome(final Ast.Become expr) {
    return node("Become", visit(expr.expression()));
  }

  @Override
  public String visitBinaryOperation(final Ast.BinaryOperation expr) {
    return node("BinaryOperation %s".formatted(expr.kind()), visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  public String visitBlock(final Ast.Block expr) {
    return node("Block", visit(expr.expression()));
  }

  @Override
  public String visitBracket(final Ast.Bracket expr) {
    return node("Bracket", visit(expr.children()));
  }

  @Override
  public String visitPostfixExpression(final Ast.PostfixExpression expr) {
    return node("PostfixExpression", visit(expr.target()), visit(expr.suffix()));
  }

  @Override
  public <E extends Ast.Expression> String visitPartial(final Ast.Partial<E> expr) {
    return node("Partial", visit(expr.expression()));
  }

  @Override
  public String visitBubble(final Ast.Bubble expr) {
    return node("Bubble", visit(expr.expression()));
  }

  @Override
  public String visitCallable(final Ast.Callable expr) {
    return node("Callable", visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  public String visitComment(final Ast.Comment expr) {
    return atom("Comment", quote(expr.content()));
  }

  @Override
  public String visitCompTime(final Ast.CompTime expr) {
    return node("CompTime", visit(expr.target()));
  }

  @Override
  public String visitConditional(final Ast.Conditional expr) {
    return node("Conditional", visit(expr.predicate()), visit(expr.pass()), visit(expr.fail()));
  }

  @Override
  public String visitDotAccess(final Ast.DotAccess expr) {
    return node("DotAccess", visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  public String visitExport(final Ast.Export expr) {
    return node("Export", visit(expr.exported()), Boolean.toString(expr.isDefault()));
  }

  @Override
  public String visitExpressions(final Ast.Expressions expr) {
    return node("Expressions", visit(expr.children()));
  }

  @Override
  public String visitLexeme(final Ast.Lexeme expr) {
    return atom("Lexeme", quote(expr.name()));
  }

  @Override
  public String visitImport(final Ast.Import expr) {
    return node("Import", visit(expr.path()));
  }

  @Override
  public String visitImportPath(final Ast.ImportPath expr) {
    return node("ImportPath", visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  public String visitImportPathAlias(final Ast.ImportPathAlias expr) {
    return node("ImportPathAlias", visit(expr.alias()), visit(expr.target()));
  }

  @Override
  public String visitImportPathGroup(final Ast.ImportPathGroup expr) {
    return node("ImportPathGroup", visit(expr.items()));
  }

  @Override
  public String visitImportPathIdentifier(final Ast.ImportPathIdentifier expr) {
    return node("ImportPathIdentifier", visit(expr.lexeme()));
  }

  @Override
  public String visitImportPathWildcard(final Ast.ImportPathWildcard expr) {
    return node("ImportPathWildcard");
  }

  @Override
  public String visitIn(final Ast.In expr) {
    return node("In", visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  public String visitInfer(final Ast.Infer expr) {
    return node("Infer", visit(expr.expression()));
  }

  @Override
  public String visitLabeling(final Ast.Labeling expr) {
    return node("Labeling", visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  public String visitLiteral(final Ast.Literal expr) {
    return node("Literal %s".formatted(quote(expr.content())));
  }

  @Override
  public String visitLoop(final Ast.Loop expr) {
    return node("Loop", visit(expr.body()));
  }

  @Override
  public String visitLoopDoWhile(final Ast.LoopDoWhile expr) {
    return node("LoopDoWhile", visit(expr.body()), visit(expr.predicate()));
  }

  @Override
  public String visitLoopFor(final Ast.LoopFor expr) {
    return node("LoopFor", visit(expr.head()), visit(expr.block()));
  }

  @Override
  public String visitLoopWhile(final Ast.LoopWhile expr) {
    return node("LoopWhile", visit(expr.predicate()), visit(expr.body()));
  }

  @Override
  public String visitMatch(final Ast.Match expr) {
    return node("Match", visit(expr.target()), visit(expr.children()));
  }

  @Override
  public String visitNegate(final Ast.Negate expr) {
    return node("Negate", visit(expr.expression()));
  }

  @Override
  public String visitNew(final Ast.New expr) {
    return node("New", visit(expr.target()), visit(expr.allocator()), visit(expr.arguments()));
  }

  @Override
  public String visitNoOp(final Ast.NoOp expr) {
    return node("NoOp");
  }

  @Override
  public String visitNot(final Ast.Not expr) {
    return node("Not", visit(expr.expression()));
  }

  @Override
  public String visitParen(final Ast.Paren expr) {
    return node("Paren", visit(expr.expression()));
  }

  @Override
  public String visitProgram(final Ast.Program expr) {
    return node("Program", visit(expr.children()));
  }

  @Override
  public String visitRange(final Ast.Range expr) {
    return node("Range", visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  public String visitRest(final Ast.Rest expr) {
    return node("Rest");
  }

  @Override
  public String visitReturn(final Ast.Return expr) {
    return node("Return", visit(expr.expression()));
  }

  @Override
  public String visitSpread(final Ast.Spread expr) {
    return node("Spread", visit(expr.expression()));
  }

  @Override
  public String visitStaticAccess(final Ast.StaticAccess expr) {
    return node("StaticAccess", visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  public String visitStruct(final Ast.Struct expr) {
    return node("Struct", visit(expr.block()));
  }

  @Override
  public String visitThen(final Ast.Then expr) {
    return node("Then", visit(expr.expression()));
  }

  @Override
  public String visitTrait(final Ast.Trait expr) {
    return node("Trait", visit(expr.block()));
  }

  @Override
  public String visitType(final Ast.Type expr) {
    return node("Type", visit(expr.lexeme()));
  }

  @Override
  public String visitTypePlaceholder(final Ast.TypePlaceholder expr) {
    return node("TypePlaceholder", visit(expr.lexeme()));
  }

  @Override
  public String visitVariableDeclaration(final Ast.VariableDeclaration expr) {
    return node(
      "VariableDeclaration",
      visit(expr.lexeme()),
      visitEnum(expr.mutabilityKind()),
      visit(expr.type()),
      Boolean.toString(expr.ref())
    );
  }

  @Override
  public String visitVariableSink(final Ast.VariableSink expr) {
    return node("VariableSink");
  }

  @Override
  public String visitWhere(final Ast.Where expr) {
    return node("Where", visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  public String visitWith(final Ast.With expr) {
    return node("With", visit(expr.argument()), visit(expr.block()));
  }

  @Override
  public String visitYield(final Ast.Yield expr) {
    return node("Yield", visit(expr.expression()));
  }

  @Override
  public String visitImpl(final Ast.Impl expr) {
    return node(
      "Impl",
      visit(expr.traitLexeme()),
      visit(expr.forExpression()),
      visit(expr.block()),
      visit(expr.with())
    );
  }

  private static String atom(final String name, final String value) {
    return "(%s %s)".formatted(name, value);
  }

  private static String node(final String name, final String... children) {

    if (children.length == 0) {
      return "(%s)".formatted(name);
    }

    final var childString = Arrays.stream(children)
      .filter(c -> c != null && !c.isEmpty())
      .reduce("%s\n%s"::formatted)
      .orElse("");

    if (name == null && childString.isEmpty()) {
      return "()";
    } else if (name == null) {
      return childString;
    } else if (childString.isEmpty()) {
      return "(%s)".formatted(name);
    } else {
      return "(%s\n%s)".formatted(name, indent(childString));
    }
  }

  private static String indent(final String value) {
    return INDENT + value.replace("\n", "\n" + INDENT);
  }

  private static String quote(final String value) {
    return (value == null) ? "null" : "\"%s\"".formatted(value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t"));
  }

  private static String quote(final Object value) {
    return (value == null) ? "null" : quote(value.toString());
  }
}
