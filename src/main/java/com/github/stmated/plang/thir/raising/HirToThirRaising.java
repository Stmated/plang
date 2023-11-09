package com.github.stmated.plang.thir.raising;

import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.hir.model.HirArgument;
import com.github.stmated.plang.hir.model.HirAssignment;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirCall;
import com.github.stmated.plang.hir.model.HirConditional;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirExpressionCollection;
import com.github.stmated.plang.hir.model.HirIdentifier;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirLoop;
import com.github.stmated.plang.hir.model.HirMutabilityKind;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import com.github.stmated.plang.hir.model.HirVariableDeclaration;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyIdentifier;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.util.Tys;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import lombok.extern.slf4j.Slf4j;

/**
 * TODO: This should be rewritten to use some kind of query system like Rust, eventually.
 *        That way we can easier multi-thread the investigation, and also do it lazily on-demand, and cache more easily.
 */
@Slf4j
public class HirToThirRaising {

  private final Map<HirExpression, Ty> map = new HashMap<>();
  private final Stack<ThirScope> scopeStack = new Stack<>();

  public Ty getType(HirExpression e) {
    return map.get(e);
  }

  public ThirRepository raise(HirExpression e) {

    // Call investigate on the expression.
    // Then the map inside this raising should contain all relevant types.
    try {
      scopeStack.push(new ThirScope(null, "root"));
      investigate(e);
    } finally {
      scopeStack.pop();
    }

    return new ThirRepository(e, map);
  }

  private Ty investigate(HirExpression e) {

    final var existing = map.get(e);
    if (existing != null) {
      return existing;
    }

    final var ty = investigate_inner(e);

    map.put(e, ty);
    return ty;
  }

  private Ty investigate_inner(HirExpression e) {

    return switch (e) {
      case HirProgram it -> investigate_program(it);
      case HirBinaryOperation it -> investigate_binary_operation(it);
      case HirLiteral it -> investigate_literal(it);
      case HirReturn it -> investigate_return(it);
      case HirVariableDeclaration it -> investigate_variable_declaration(it);
      case HirAssignment it -> investigate_assignment(it);
      case HirIdentifier it -> investigate_identifier(it);
      case HirConditional it -> investigate_conditional(it);
      case HirLoop it -> investigate_loop(it);
      case HirExpressionCollection it -> investigate_expressions(it.children());
      case HirCall it -> investigate_call(it);
      case HirArgument it -> investigate_argument(it);
      default -> Ty.UNKNOWN;
        //throw new UnexpectedExpressionException(e);
    };
  }

  private Ty investigate_argument(HirArgument it) {
    return investigate(it.value());
  }

  private Ty investigate_call(HirCall it) {

    for (final var argument : it.arguments()) {
      final var parameterType = investigate(argument);
      if (log.isTraceEnabled()) {
        log.trace(STR."Fn '\{it.functionReference().function().identifier().name()}' param type: \{parameterType.toShortString()}");
      }
    }

    return it.functionReference().function().returnType();
  }

  private Ty investigate_loop(HirLoop hir) {
    return investigate(hir.body());
  }

  private Ty investigate_conditional(HirConditional hir) {

    final var conditional_type = investigate(hir.predicate());
    if (conditional_type != Ty.BOOLEAN) {
      throw new IllegalArgumentException(STR."The conditional predicate must produce a boolean value, not: \{conditional_type}");
    }

    var branch_types = new Ty[2];
    if (hir.pass() != null) {

      try {
        scopeStack.push(new ThirScope(scopeStack.peek(), "conditional_pass"));
        branch_types[0] = investigate(hir.pass());

      } finally {
        scopeStack.pop();
      }
    }

    if (hir.fail() != null) {

      try {
        scopeStack.push(new ThirScope(scopeStack.peek(), "conditional_fail"));
        branch_types[1] = investigate(hir.fail());

      } finally {
        scopeStack.pop();
      }
    }

    if (branch_types[1] == null) {
      return branch_types[0];
    }

    return Tys.merge(branch_types);
  }

  private Ty investigate_identifier(HirIdentifier hir) {

    // TODO: Very bad. Needs serious change later.
    if ("stdout".equals(hir.name())) {
      return new TyPointer<>(Ty.CHAR);
    }

    return Objects.requireNonNull(scopeStack.peek().get(hir.name()), STR."Cannot get '\{hir.name()}' since its type is unknown");
  }

  private Ty investigate_assignment(HirAssignment hir) {

    final var lhs = investigate(hir.lhs());
    final var rhs = investigate(hir.rhs());

    // TODO: If the types are "infer" or "TyIdentifier" then we should try to resolve them.
    //        Also, we need to check that the lhs and rhs types are actually compatible.

    final var identifierName = switch (hir.lhs()) {
      case HirVariableDeclaration it -> it.identifier().name();
      case HirIdentifier it -> it.name();
      default -> null;
    };

    final var mutability = switch (hir.lhs()) {
      case HirVariableDeclaration it -> it.mutabilityKind();
      case HirIdentifier it -> HirMutabilityKind.MUTABLE;
      default -> throw new NotImplementedException(STR."Do not know how to find mutability of '\{hir.lhs()}'");
    };

    // TODO: Make use of Tys.toNonConstIfRequired, to convert "a (const) + 10" to not const

    if (lhs == Ty.INFER) {

      if (identifierName != null) {

        if (scopeStack.peek().map().containsKey(identifierName)) {
          throw new NotImplementedException("What to do here?");
        } else {
          scopeStack.peek().map().put(identifierName, rhs);
        }
      }

      // LHS is to be inferred, and obviously that is into the RHS.
//      return rhs;

    } else {

      final var common = Tys.getCommonDenominator(lhs, rhs);
      if (!Tys.isGenerallyCompatible(common.diffs())) {
        throw new IllegalArgumentException(STR."\{lhs} and \{rhs} are not compatible with each other");
      }

      if (scopeStack.peek().map().containsKey(identifierName)) {

        // NOTE: In the future it might be useful to have a type of "lower bound" + "stated type" + "higher bound"
        //        So we can know what it was said to be, and what it *actually* contains at a certain point

      } else {
        scopeStack.peek().map().put(identifierName, common.ty());
      }
    }

    // Assignment itself returns void type
    return Ty.VOID;
  }

  private Ty investigate_variable_declaration(HirVariableDeclaration hir) {

    if (hir.type() == null) {
      return Ty.INFER;
    }

    return switch (hir.type()) {
      case HirIdentifier it -> new TyIdentifier(it.name());
      default -> throw new UnexpectedExpressionException(hir.type());
    };
  }

  private Ty investigate_return(HirReturn hir) {
    return investigate(hir.expression());
  }

  private Ty investigate_literal(HirLiteral hir) {
    return hir.ty();
  }

  private Ty investigate_program(HirProgram hir) {
    return investigate_expressions(hir.expressions());
  }

  private Ty investigate_expressions(HirExpression[] expressions) {

    // NOTE: Most likely this is not correct?
    Ty lastType = null;
    for (final var expression : expressions) {
      lastType = investigate(expression);
    }

    if (lastType != null) {
      return lastType;
    }

    throw new IllegalArgumentException(STR."Could not find type of '\{expressions}'");
  }

  private Ty investigate_binary_operation(HirBinaryOperation v) {

    if (v.kind().isPredicate()) {
      return Ty.BOOLEAN;
    }

    final var lhst = investigate(v.lhs());
    final var rhst = investigate(v.rhs());

    final var result = Tys.getCommonDenominator(lhst, rhst);
    if (result.ty() == null) {
      throw new IllegalArgumentException(STR."There was no common denominator between '\{lhst}' and '\{rhst}'");
    }

    return result.ty();
  }
}
