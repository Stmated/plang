package org.inf.hir.util;

import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.*;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.StringJoiner;

/** Structural snapshots of HIR and THIR, without following declaration references. */
public final class ToStringTreeHirVisitor {

  public String render(final Hir.Expression expression) {
    return new Printer().print(expression);
  }

  private static final class Printer implements HirVisitor {

    private String result;

    private String print(final Hir.Expression expression) {
      if (expression == null) {
        return "null";
      }
      result = null;
      expression.visit(this);
      if (result == null) {
        throw new IllegalArgumentException("Missing structural HIR printer");
      }
      return result;
    }

    private String child(final String role, final Hir.Expression expression) {
      return expression == null ? atom(role, "null") : node(role, print(expression));
    }

    private String children(final String role, final Hir.Expression[] expressions) {
      if (expressions == null) {
        return atom(role, "null");
      }
      final var items = new String[expressions.length];
      for (int i = 0; i < expressions.length; i++) {
        items[i] = print(expressions[i]);
      }
      return node(role, items);
    }

    @Override
    public void visitExpressions(final Hir.Expressions expr) {
      result = node("Expressions", atom("generated", expr.generated()), type("ty", expr.ty()),
        children("children", expr.children()));
    }

    @Override
    public void visitArgument(final Hir.Argument expr) {
      result = node("Argument", child("label", expr.label()), child("value", expr.value()));
    }

    @Override
    public void visitArray(final Hir.Array expr) {
      result = node("Array", type("ty", expr.ty()), type("valueTy", expr.valueTy()),
        child("elementType", expr.elementType()), child("length", expr.length()),
        children("elements", expr.elements()));
    }

    @Override
    public void visitArrayAccess(final Hir.ArrayAccess expr) {
      result = node("ArrayAccess", type("ty", expr.ty()), type("valueTy", expr.valueTy()),
        child("target", expr.target()), child("accessor", expr.accessor()));
    }

    @Override
    public void visitAssignment(final Hir.Assignment expr) {
      result = node("Assignment", type("ty", expr.ty()), child("lhs", expr.lhs()), child("rhs", expr.rhs()));
    }

    @Override
    public void visitBinaryOperation(final Hir.BinaryOperation expr) {
      result = node("BinaryOperation", atom("kind", expr.kind()), type("ty", expr.ty()),
        type("valueTy", expr.valueTy()), child("lhs", expr.lhs()), child("rhs", expr.rhs()));
    }

    @Override
    public void visitCompoundAssignment(final Hir.CompoundAssignment expr) {
      result = node("CompoundAssignment", atom("kind", expr.kind()), type("ty", expr.ty()),
        child("target", expr.target()), child("rhs", expr.rhs()));
    }

    @Override
    public void visitBlock(final Hir.Block expr) {
      result = node("Block", type("ty", expr.ty()), child("children", expr.children()));
    }

    @Override
    public void visitConditional(final Hir.Conditional expr) {
      result = node("Conditional", type("ty", expr.ty()), type("valueTy", expr.valueTy()),
        child("predicate", expr.predicate()), child("pass", expr.pass()), child("fail", expr.fail()));
    }

    @Override
    public void visitFunction(final Hir.Function expr) {
      result = node("Function", child("signature", expr.signature()), child("body", expr.body()));
    }

    @Override
    public void visitFunctionSignature(final Hir.FunctionSignature expr) {
      result = node("FunctionSignature", atom("vararg", expr.vararg()), type("ty", expr.ty()),
        children("parameters", expr.parameters()), child("returnType", expr.returnType()));
    }

    @Override
    public void visitIdentifier(final Hir.Identifier expr) {
      // Even ty() follows target.valueTy(), which can recurse through unresolved declaration cycles.
      result = node("Identifier", child("lexeme", expr.lexeme()),
        atom("target", expr.target() == null ? "null" : "(reference " + quote(expr.lexeme().name()) + ")"));
    }

    @Override
    public void visitLabeling(final Hir.Labeling expr) {
      result = node("Labeling", type("ty", expr.ty()), child("lhs", expr.lhs()), child("rhs", expr.rhs()));
    }

    @Override
    public void visitLiteral(final Hir.Literal expr) {
      result = node("Literal", atom("content", quote(expr.content())), type("ty", expr.ty()));
    }

    @Override
    public void visitLoop(final Hir.Loop expr) {
      result = node("Loop", type("ty", expr.ty()), type("valueTy", expr.valueTy()), child("body", expr.body()));
    }

    @Override
    public void visitLoopBreak(final Hir.LoopBreak expr) {
      result = node("LoopBreak", type("ty", expr.ty()), child("value", expr.value()));
    }

    @Override
    public void visitLoopContinue(final Hir.LoopContinue expr) {
      result = node("LoopContinue", type("ty", expr.ty()));
    }

    @Override
    public void visitNewByBlock(final Hir.NewByBlock expr) {
      result = node("NewByBlock", type("ty", expr.ty()), type("valueTy", expr.valueTy()),
        child("target", expr.target()), child("allocator", expr.allocator()), children("fields", expr.fields()));
    }

    @Override
    public void visitNewByCtor(final Hir.NewByCtor expr) {
      result = node("NewByCtor", type("ty", expr.ty()), type("valueTy", expr.valueTy()),
        child("target", expr.target()), child("allocator", expr.allocator()), child("arguments", expr.arguments()));
    }

    @Override
    public void visitNot(final Hir.Not expr) {
      result = node("Not", type("ty", expr.ty()), type("valueTy", expr.valueTy()), child("expression", expr.expression()));
    }

    @Override
    public void visitParameter(final Hir.Parameter expr) {
      result = node("Parameter", atom("vararg", expr.vararg()), type("ty", expr.ty()),
        child("lexeme", expr.lexeme()), child("valueType", expr.valueType()));
    }

    @Override
    public void visitPath(final Hir.Path expr) {
      result = node("Path", type("ty", expr.ty()), type("valueTy", expr.valueTy()), children("elements", expr.elements()));
    }

    @Override
    public void visitRange(final Hir.Range expr) {
      result = node("Range", type("ty", expr.ty()), type("valueTy", expr.valueTy()),
        child("lower", expr.lower()), child("higher", expr.higher()));
    }

    @Override
    public void visitProgram(final Hir.Program expr) {
      result = node("Program", type("ty", expr.ty()), child("expressions", expr.expressions()));
    }

    @Override
    public void visitReturn(final Hir.Return expr) {
      result = node("Return", type("ty", expr.ty()), child("expression", expr.expression()));
    }

    @Override
    public void visitDeadEnd(final Hir.DeadEnd expr) {
      result = node("DeadEnd", type("ty", expr.ty()), child("expression", expr.expression()));
    }

    @Override
    public void visitStruct(final Hir.Struct expr) {
      result = node("Struct", type("ty", expr.ty()), children("declarations", expr.declarations()));
    }

    @Override
    public void visitTrait(final Hir.Trait expr) {
      result = node("Trait", type("ty", expr.ty()), children("children", expr.children()));
    }

    @Override
    public void visitTuple(final Hir.Tuple expr) {
      result = node("Tuple", type("ty", expr.ty()), type("valueTy", expr.valueTy()), children("children", expr.children()));
    }

    @Override
    public void visitTupleEntry(final Hir.TupleEntry expr) {
      result = node("TupleEntry", atom("label", expr.label() == null ? "null" : quote(expr.label().name())),
        type("ty", expr.ty()), type("valueTy", expr.valueTy()), child("value", expr.value()));
    }

    @Override
    public void visitTyExpr(final Hir.TyExpr expr) {
      result = node("TyExpr", type("ty", expr.ty()));
    }

    @Override
    public void visitDec(final Hir.Dec expr) {
      result = node("Dec", atom("mutability", expr.mutabilityKind()), type("ty", expr.ty()),
        child("lexeme", expr.lexeme()), child("valueType", expr.valueType()));
    }

    @Override
    public void visitCall(final Hir.Call expr) {
      result = node("Call", atom("partial", expr.partial()), type("ty", expr.ty()),
        type("valueTy", expr.valueTy()), child("target", expr.target()), children("arguments", expr.arguments()));
    }

    @Override
    public void visitLexeme(final Hir.Lexeme expr) {
      result = node("Lexeme", atom("name", quote(expr.name())), type("ty", expr.ty()));
    }
  }

  private static String type(final String role, final Ty ty) {
    return atom(role, formatType(ty, Collections.newSetFromMap(new IdentityHashMap<>())));
  }

  private static String formatType(final Ty ty, final Set<Ty> active) {
    if (ty == null) {
      return "null";
    }
    if (active.contains(ty)) {
      return "(recursive)";
    }
    active.add(ty);
    try {
      return switch (ty) {
        case Ty.TyNamed named -> "(named " + quote(named.name()) + ")";
        case TyIdentifier identifier -> "(identifier " + quote(identifier.name()) + ")";
        case TyValueNumber number -> {
          final var flags = switch (number) {
            case TyValueNumberInteger integer -> integer.flags();
            case TyValueNumberPrecisioned precisioned -> precisioned.flags();
            case TyValueNumberScaled scaled -> scaled.flags();
            default -> null;
          };
          final var extra = switch (number) {
            case TyValueNumberPrecisioned precisioned -> " precision=" + precisioned.precision();
            case TyValueNumberScaled scaled -> " scale=" + scaled.scale();
            default -> "";
          };
          yield "(number " + number.getValueKind() + " width=" + number.width().value()
            + " explicit=" + number.width().explicit() + " signed=" + number.signed()
            + " radix=" + number.radix() + " flags=" + flags + extra + ")";
        }
        case TyValueBoolean ignored -> "(boolean)";
        case TyValueString ignored -> "(string)";
        case TyOpaque ignored -> "(opaque)";
        case TyPointer<?> pointer -> "(pointer " + pointer.addressSpace() + " "
          + formatType(pointer.inner(), active) + ")";
        case TyPointerExplicit pointer -> "(explicit-pointer " + pointer.addressSpace() + " "
          + formatType(pointer.inner(), active) + ")";
        case TyUninitialized<?> uninitialized -> "(uninitialized "
          + formatType(uninitialized.inner(), active) + ")";
        case TyValueArray array -> "(array size=" + array.size() + " "
          + formatType(array.elementType(), active) + ")";
        case TyFn fn -> {
          final var parameters = new StringJoiner(" ", "(parameters ", ")").setEmptyValue("(parameters)");
          if (fn.parameters() == null) {
            parameters.add("null");
          } else {
            for (final var parameter : fn.parameters()) {
              parameters.add(parameter == null ? "null" : "(" + quote(parameter.name()) + " "
                + formatType(parameter.ty(), active) + ")");
            }
          }
          yield "(function vararg=" + fn.vararg() + " " + parameters + " (returns "
            + formatType(fn.returnTy(), active) + "))";
        }
        case TyStruct struct -> {
          final var fields = new StringJoiner(" ", "(struct ", ")").setEmptyValue("(struct)");
          if (struct.fields() == null) {
            fields.add("null");
          } else {
            for (final var field : struct.fields()) {
              fields.add(field == null ? "null" : "(" + quote(field.name()) + " "
                + formatType(field.ty(), active) + ")");
            }
          }
          yield fields.toString();
        }
        case TyUnion union -> {
          final var types = new StringJoiner(" ", "(union ", ")").setEmptyValue("(union)");
          if (union.types() == null) {
            types.add("null");
          } else {
            for (final var member : union.types()) {
              types.add(formatType(member, active));
            }
          }
          yield types.toString();
        }
        default -> throw new IllegalArgumentException("Missing structural type printer");
      };
    } finally {
      active.remove(ty);
    }
  }

  private static String atom(final String name, final Object value) {
    return "(" + name + " " + value + ")";
  }

  private static String node(final String name, final String... children) {
    final var result = new StringBuilder("(").append(name);
    for (final var child : children) {
      result.append("\n  ").append(child.replace("\n", "\n  "));
    }
    return result.append(')').toString();
  }

  private static String quote(final String value) {
    if (value == null) {
      return "null";
    }
    final var result = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      switch (character) {
        case '\\' -> result.append("\\\\");
        case '"' -> result.append("\\\"");
        case '\n' -> result.append("\\n");
        case '\r' -> result.append("\\r");
        case '\t' -> result.append("\\t");
        case '\b' -> result.append("\\b");
        case '\f' -> result.append("\\f");
        default -> {
          if (Character.isISOControl(character)) {
            result.append("\\u%04x".formatted((int) character));
          } else {
            result.append(character);
          }
        }
      }
    }
    return result.append('"').toString();
  }
}
