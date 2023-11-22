package com.github.stmated.plang.thir.raising;

import com.github.stmated.plang.exceptions.InvalidTypeConversionException;
import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.hir.Hir;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyField;
import com.github.stmated.plang.ty.TyFn;
import com.github.stmated.plang.ty.TyIdentifier;
import com.github.stmated.plang.ty.TyParam;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.TyStruct;
import com.github.stmated.plang.ty.TyUninitialized;
import com.github.stmated.plang.ty.TyValueArray;
import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.ty.util.Tys;
import com.github.stmated.plang.util.JavaUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Stack;
import lombok.extern.slf4j.Slf4j;

/**
 * TODO: This should be rewritten to use some kind of query system like Rust, eventually.
 *        That way we can easier multi-thread the investigation, and also do it lazily on-demand, and cache more easily.
 */
@Slf4j
public class HirToThirRaising {

//  private final Map<Hir.Expression, Ty> map = new HashMap<>();
  private final Stack<ThirScope> scopeStack = new Stack<>();
  private final boolean lenient;

  public HirToThirRaising() {
    this(false);
  }

  public HirToThirRaising(boolean lenient) {
    this.lenient = lenient;
  }

  public ThirRaiseResult raise(Hir.Expression e) {

    // Call investigate on the expression.
    // Then the map inside this raising should contain all relevant types.
    try {
      scopeStack.push(new ThirScope(null, "root"));
      investigate(e);
    } finally {
      scopeStack.pop();
    }

    return new ThirRaiseResult(e);
  }

  private Ty investigate(Hir.Expression e) {

    final var existing = e.ty(); // map.get(e);
    if (existing != null && existing != Ty.INFER) {
      return existing;
    }

    return investigate_inner(e);
  }

  private Ty investigate_inner(Hir.Expression e) {

    return switch (e) {
      case Hir.BinaryOperation it -> investigate_binary_operation(it);
      case Hir.Literal it -> investigate_literal(it);
      case Hir.Return it -> investigate_return(it);
      case Hir.VariableDeclaration it -> investigate_variable_declaration(it);
      case Hir.Assignment it -> investigate_assignment(it);
      case Hir.Identifier it -> investigate_identifier(it);
      case Hir.Conditional it -> investigate_conditional(it);
      case Hir.Loop it -> investigate_loop(it);
      case Hir.Expressions it -> investigate_expressions(it);
      case Hir.Call it -> investigate_call(it);
      case Hir.Argument it -> investigate_argument(it);
      case Hir.LoopContinue it -> investigate_loop_continue(it);
      case Hir.LoopBreak it -> investigate_loop_break(it);
      case Hir.Program it -> investigate_program(it);
      case Hir.Function it -> investigate_function(it);
      case Hir.FunctionSignature it -> investigate_function_signature(it);
      case Hir.TyExpr it -> it.ty();
      case Hir.Array it -> investigate_array(it);
      case Hir.ArrayAccess it -> investigate_array_access(it);
      case Hir.Struct it -> investigate_struct(it);
      case Hir.NewByBlock it -> investigate_new_by_block(it);
      case Hir.NewByCtor it -> investigate_new_by_ctor(it);
      case Hir.Path it -> investigate_path(it);
      case Hir.Block it -> investigate_block(it);
      case Hir.Parameter it -> throw new IllegalArgumentException(STR."A Parameter itself (\{it}) does not have a type (yet?). Resolve it higher in call chain");
      default -> throw new UnexpectedExpressionException(e);
    };
  }

  private Ty investigate_block(Hir.Block it) {
    return it.ty(investigate(it.children())).ty();
  }

  private Ty investigate_path(Hir.Path it) {

    if (it.elements() == null || it.elements().length == 0) {
      return Ty.INVALID;
    }

    var pointer = investigate(it.elements()[0]);
    for (var i = 1; i < it.elements().length; i++) {

      final var current = it.elements()[i];
      switch (current) {
        case Hir.Identifier identifier -> {

          switch (pointer) {
            case TyStruct struct -> {

              final var field = Arrays.stream(struct.fields())
                .filter(f -> f.name().equals(identifier.name()))
                .findFirst().orElseThrow();

              pointer = field.ty();
            }
            default -> throw new UnexpectedExpressionException(current);
          }
        }
        default -> throw new UnexpectedExpressionException(current);
      }

      // Register each step of the path. We will likely need it.
//      map.put(current, pointer);
    }

    it.ty(pointer);

    // We have reached the end of the path and can give back the ty.
    return pointer;
  }

  private Ty investigate_new_by_ctor(Hir.NewByCtor it) {

    // TODO: The allocator can alter the type, so need to run it through the allocator's investigation.

    return it.ty(investigate_type_expression(it.target())).ty();
  }

  private Ty investigate_new_by_block(Hir.NewByBlock it) {

    // TODO: The allocator can alter the type, so need to run it through the allocator's investigation.
    return it.ty(investigate_type_expression(it.target())).ty();
  }

  private Ty investigate_struct(Hir.Struct it) {

    final var fields = new ArrayList<TyField>();

    for (final var decl : it.declarations()) {

      final var name = decl.identifier().name();
      final var ty = investigate_type_expression(decl.type());

      fields.add(new TyField(name, ty));
    }

    final var ty = new TyStruct(fields.toArray(new TyField[0]));
    it.ty(ty);

    return ty;
  }

  private Ty investigate_array_access(Hir.ArrayAccess it) {

    final var targetTy = investigate(it.target());
    final var accessorTy = investigate(it.accessor());

    final var isRange = switch (accessorTy) {
      case TyValueArray _ -> true;
      default -> false;
    };

    final var ty = switch (targetTy) {
      // The below should not return array type if is range, it should return a slice, which is different.
      case TyValueArray arrayTy -> isRange ? arrayTy : arrayTy.elementType();
      default -> throw new UnexpectedExpressionException(it.target());
    };

    return it.ty(ty).ty();
  }

  private Object resolveLiteralValue(Hir.Expression expr) {

    return switch (expr) {
      case Hir.Literal literal -> switch (literal.ty()) {
        case TyValueNumberInteger vni -> Integer.parseInt(literal.content(), vni.radix());
        default -> null;
      };
      case Hir.BinaryOperation bop -> {
        final var lhs = resolveLiteralValue(bop.lhs());
        final var rhs = resolveLiteralValue(bop.rhs());

        yield switch (bop.kind()) {
          case ADD -> JavaUtil.add(lhs, rhs);
          case SUBTRACT -> JavaUtil.subtract(lhs, rhs);
          case MULTIPLY -> JavaUtil.multiply(lhs, rhs);
          default -> null;
        };
      }
      default -> null;
    };
  }

  private Ty investigate_array(Hir.Array it) {

    // TODO: Problem is that array as a type and array as initializer need to behave differently!
    //        One is silly, and one is not...

    var arrayElementTy = investigate_type_expression(it.elementType());
    for (final var element : it.elements()) {
      final var elementTy = investigate(element);
      if (arrayElementTy == Ty.INFER) {
        arrayElementTy = elementTy;
      } else {

        final var common = Tys.getCommonDenominator(arrayElementTy, elementTy);
        final var diffs = common.diffs();
        if (!Tys.isSizeCompatible(diffs)) {
          throw new InvalidTypeConversionException("Array types must be size-compatible", elementTy, arrayElementTy);
        }
      }
    }

    Object literalValue = (it.length() == null) ? null : resolveLiteralValue(it.length());
    Integer arrayLength = (literalValue == null) ? null : ((Number) literalValue).intValue();

    // The element ty can still be INFER -- it will be used for late type decisions.
    return it.ty(new TyValueArray(arrayElementTy, arrayLength).intern()).ty();
  }

  private TyFn investigate_function_signature(Hir.FunctionSignature hir) {

    final var parameterTys = new TyParam[hir.parameters().length];
    for (var i = 0; i < hir.parameters().length; i++) {

      final var parameter = hir.parameters()[i];
      final var parameterName = switch (parameter.identifier()) {
        case Hir.Identifier id -> id.name();
        default -> throw new NotImplementedException(STR."Do not know how to get name from '\{parameter.identifier()}'");
      };

      final var paramTy = investigate_type_expression(parameter.type());
      parameter.ty(paramTy);

      parameterTys[i] = new TyParam(parameterName, paramTy);
    }

    final var returnTy = investigate_type_expression(hir.returnType());

    return new TyFn(parameterTys, hir.vararg(), returnTy);
  }

  private Ty investigate_function(Hir.Function hir) {

    final var signatureTy = investigate_function_signature(hir.signature());
    if (hir.body() != null && (signatureTy.returnTy() == null || signatureTy.returnTy() == Ty.INFER)) {

      // If the signature does not contain a ty but we have a body, then we investigate it for a ty.

      Ty bodyReturnTy;
      try {

        final var scope = new ThirScope(scopeStack.peek(), "fn");
        scopeStack.push(scope);

        for (final var parameter : signatureTy.parameters()) {
          scope.map().put(parameter.name(), parameter.ty());
        }

        bodyReturnTy = investigate(hir.body());
      } finally {
        scopeStack.pop();
      }

      return hir.ty(new TyFn(
        signatureTy.parameters(),
        signatureTy.vararg(),
        Objects.requireNonNull(bodyReturnTy, "No return kind could be inferred")
      )).ty();
    }

    return hir.ty(signatureTy).ty();
  }

  private Ty investigate_argument(Hir.Argument hir) {
    return hir.ty(investigate(hir.value())).ty();
  }

  private Ty investigate_call(Hir.Call hir) {

    final var target = hir.target();
    final var loweredTarget = investigate(target);

    for (final var argument : hir.arguments()) {

      // Investigate it, but we do not really care about the result.
      investigate(argument);
    }

    if (loweredTarget instanceof TyFn tyFn) {
      return hir.ty(tyFn.returnTy()).ty();
    } else if (loweredTarget != null && loweredTarget != Ty.INFER) {
      return hir.ty(loweredTarget).ty();
    }

    // NOTE: Hopefully we never here? Since I guess all call targets ought to be functions?
    final var ty = switch (target) {
      // NOTE: This seems od. Will it ever be the function signature?
      case Hir.FunctionSignature fns -> investigate(fns.returnType());
      // Now lookup by identifier is completely fine.
      case Hir.Identifier id -> {

        final var v = scopeStack.peek().get(id.name());
        yield Objects.requireNonNull(v, STR."No function called '\{id.name()}' found in scope");
      }
      default -> throw new UnexpectedExpressionException(target);
    };

    return hir.ty(ty).ty();
  }

  private Ty investigate_loop(Hir.Loop hir) {
    return hir.ty(investigate(hir.body())).ty();
  }

  private Ty investigate_loop_break(Hir.LoopBreak it) {

    if (it.value() != null) {
      return it.ty(investigate(it.value())).ty();
    }

    return it.ty(Ty.VOID).ty();
  }

  private Ty investigate_loop_continue(Hir.LoopContinue hir) {
    return hir.ty();
  }

  private Ty investigate_conditional(Hir.Conditional hir) {

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
      return hir.ty(branch_types[0]).ty();
    }

    return hir.ty(Tys.merge(branch_types)).ty();
  }

  /**
   * Call when the context is trying to resolve a kind.
   * <p>
   * TODO: In a future pass it would be preferential to resolve these inline and replace/rebuild the expressions.
   */
  public Ty investigate_type_expression(Hir.Expression hir) {

    try {
      typeModeCounter++;
      return switch (hir) {
        case Hir.Identifier id -> {
          final var knownTypeByName = Tys.fromString(id.name());
          if (knownTypeByName != null) {
            id.ty(knownTypeByName);
            yield knownTypeByName;
          } else {
            yield investigate(hir);
          }
        }
        default -> investigate(hir);
      };
    } finally {
      typeModeCounter--;
    }
  }

  private int typeModeCounter = 0;

  private Ty investigate_identifier(Hir.Identifier hir) {

    var resolvedVariable = scopeStack.peek().get(hir.name());

    if (resolvedVariable == null && typeModeCounter > 0) {
      resolvedVariable = Tys.fromString(hir.name());
    }

    if (resolvedVariable == null && hir.name().equals("freopen_stdout")) {

      // (filename: *char, mode: *char): *int;
      resolvedVariable = new TyFn(
        new TyParam[] {
          new TyParam("filename", new TyPointer<>(Ty.CHAR)),
          new TyParam("mode", new TyPointer<>(Ty.CHAR))
        },
        false, new TyPointer<>(Ty.INTEGER)
      );
    }

    if (lenient && resolvedVariable == null) {
      return null;
    }

    hir.ty(resolvedVariable);
    return Objects.requireNonNull(resolvedVariable, STR."Cannot get '\{hir.name()}' since its type is unknown");
  }

  private Ty investigate_assignment(Hir.Assignment hir) {

    final var lhs = investigate(hir.lhs());
    final var rhs = investigate(hir.rhs());

    // TODO: If the types are "infer" or "TyIdentifier" then we should try to resolve them.
    //        Also, we need to check that the lhs and rhs types are actually compatible.

    final var identifierName = switch (hir.lhs()) {
      case Hir.VariableDeclaration it -> it.identifier().name();
      case Hir.Identifier it -> it.name();
      default -> null;
    };

    final var mutability = switch (hir.lhs()) {
      case Hir.VariableDeclaration it -> it.mutabilityKind();
      case Hir.Identifier it -> Hir.MutabilityKind.MUTABLE;
      case Hir.Path it -> Hir.MutabilityKind.MUTABLE;
      default -> throw new NotImplementedException(STR."Do not know how to find mutability of '\{hir.lhs()}'");
    };

    // TODO: Make use of Tys.toNonConstIfRequired, to convert "a (const) + 10" to not const

    if (lhs == Ty.INFER) {

      if (identifierName != null) {

        final var existing = scopeStack.peek().map().get(identifierName);
        if (existing != null && !(existing instanceof TyUninitialized<?>)) {
          throw new NotImplementedException("What to do here?");
        } else {
          scopeStack.peek().map().put(identifierName, rhs);
        }
      }

    } else {

      final var common = Tys.getCommonDenominator(lhs, rhs);
      if (!Tys.isGenerallyCompatible(common.diffs())) {
        throw new IllegalArgumentException(STR."\{lhs} and \{rhs} are not compatible with each other");
      }

      if (scopeStack.peek().map().containsKey(identifierName)) {

        // NOTE: In the future it might be useful to have a kind of "lower bound" + "stated kind" + "higher bound"
        //        So we can know what it was said to be, and what it *actually* contains at a certain point

      } else {
        scopeStack.peek().map().put(identifierName, common.ty());
      }
    }

    // Assignment itself returns void kind
    return hir.ty(Ty.VOID).ty();
  }

  private Ty investigate_variable_declaration(Hir.VariableDeclaration hir) {

    if (hir.type() == null) {
      return hir.ty(Ty.INFER).ty();
    }

    final var ty = switch (hir.type()) {
      case Hir.Identifier it -> new TyIdentifier(it.name());
      case Hir.TyExpr it -> it.ty();
      default -> throw new UnexpectedExpressionException(hir.type());
    };

    scopeStack.peek().map().put(hir.identifier().name(), new TyUninitialized<>(ty));

    return hir.ty(ty).ty();
  }

  private Ty investigate_return(Hir.Return hir) {
    return hir.ty(investigate(hir.expression())).ty();
  }

  private Ty investigate_literal(Hir.Literal hir) {
    return hir.ty();
  }

  private Ty investigate_program(Hir.Program hir) {
    return hir.ty(investigate(hir.expressions())).ty();
  }

  private Ty investigate_expressions(Hir.Expressions expressions) {

    // NOTE: Most likely this is not correct?
    Ty lastType = null;
    for (final var expression : expressions.children()) {
      lastType = investigate(expression);
    }

    if (lastType != null) {
      return expressions.ty(lastType).ty();
    }

    throw new IllegalArgumentException(STR."Could not find type of '\{expressions}'");
  }

  private Ty investigate_binary_operation(Hir.BinaryOperation hir) {

    if (hir.kind().isPredicate()) {
      return hir.ty(Ty.BOOLEAN).ty();
    }

    final var lhst = investigate(hir.lhs());
    final var rhst = investigate(hir.rhs());

    final var result = Tys.getCommonDenominator(lhst, rhst);
    if (result.ty() == null) {
      throw new IllegalArgumentException(STR."There was no common denominator between '\{lhst}' and '\{rhst}'");
    }

    return hir.ty(result.ty()).ty();
  }
}
