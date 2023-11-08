package com.github.stmated.plang.hir.raising;

import com.github.stmated.plang.ast.model.AstAssignment;
import com.github.stmated.plang.ast.model.AstBinaryOperation;
import com.github.stmated.plang.ast.model.AstBinaryOperationKind;
import com.github.stmated.plang.ast.model.AstBlock;
import com.github.stmated.plang.ast.model.AstCall;
import com.github.stmated.plang.ast.model.AstConditional;
import com.github.stmated.plang.ast.model.AstExpression;
import com.github.stmated.plang.ast.model.AstExpressionCollection;
import com.github.stmated.plang.ast.model.AstIdentifier;
import com.github.stmated.plang.ast.model.AstLabeling;
import com.github.stmated.plang.ast.model.AstLiteral;
import com.github.stmated.plang.ast.model.AstLoopFor;
import com.github.stmated.plang.ast.model.AstNoOp;
import com.github.stmated.plang.ast.model.AstParen;
import com.github.stmated.plang.ast.model.AstProgram;
import com.github.stmated.plang.ast.model.AstReturn;
import com.github.stmated.plang.ast.model.AstThen;
import com.github.stmated.plang.ast.model.AstType;
import com.github.stmated.plang.ast.model.AstVariableDeclaration;
import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.hir.model.HirArgument;
import com.github.stmated.plang.hir.model.HirAssignment;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirBinaryOperationKind;
import com.github.stmated.plang.hir.model.HirBlock;
import com.github.stmated.plang.hir.model.HirCall;
import com.github.stmated.plang.hir.model.HirConditional;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirExpressionCollection;
import com.github.stmated.plang.hir.model.HirFunction;
import com.github.stmated.plang.hir.model.HirFunctionReference;
import com.github.stmated.plang.hir.model.HirIdentifier;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirLoop;
import com.github.stmated.plang.hir.model.HirLoopBreak;
import com.github.stmated.plang.hir.model.HirLoopContinue;
import com.github.stmated.plang.hir.model.HirMutabilityKind;
import com.github.stmated.plang.hir.model.HirParameter;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import com.github.stmated.plang.hir.model.HirTuple;
import com.github.stmated.plang.hir.model.HirTupleKeyValue;
import com.github.stmated.plang.hir.model.HirVariableDeclaration;
import com.github.stmated.plang.ty.Ty;

public class AstToHirRaising {

  public HirProgram lower_program(AstProgram astProgram) {
    return new HirProgram(implicit_return(lower_expressions(astProgram.children())));
  }

  private HirExpression[] implicit_return(HirExpression[] expressions) {

    if (expressions.length > 0) {

      // TODO: This is bad since it will add a "return" even if all paths inside this are terminal
      //        We would need a visitor pattern to visit the last expression of every node, and see if it is terminal
      //        Only then should we add this implicit return...
      final var last = expressions[expressions.length - 1];
      if (!(last instanceof HirReturn)) {

        final var implicitReturn = new HirReturn(last);
        expressions[expressions.length - 1] = implicitReturn;

        return expressions;
      }
    }

    return expressions;
  }

  private HirExpression[] lower_expressions(AstExpression[] astExpressions) {

    final var lowered = new HirExpression[astExpressions.length];
    var targetIndex = 0;
    for (var i = 0; i < astExpressions.length; i++) {
      final var hir = lower_expression(astExpressions[i]); ;
      if (hir != null) {
        lowered[targetIndex] = hir;
        targetIndex++;
      }
    }

    if (targetIndex != lowered.length) {

      final var shrunk = new HirExpression[targetIndex];
      System.arraycopy(lowered, 0, shrunk, 0, targetIndex);
      return shrunk;
    }

    return lowered;
  }

  public HirExpression lower_expression(AstExpression expr) {

    return switch (expr) {
      case AstLoopFor ast -> lower_loop_for(ast);
      case AstConditional ast -> lower_conditional(ast);
      case AstBinaryOperation ast -> lower_binary_operation(ast);
      case AstLiteral ast -> lower_literal(ast);
      case AstReturn ast -> lower_return(ast);
      case AstBlock ast -> lower_block(ast);
      case AstCall ast -> lower_call(ast);
      case AstIdentifier ast -> lower_identifier(ast);
      case AstParen ast -> lower_paren(ast);
      case AstThen ast -> lower_expression(ast.expression());
      case AstVariableDeclaration ast -> lower_variable_declaration(ast);
      case AstAssignment ast -> lower_assignment(ast);
      // TODO: Important that a NoOp means "nothing" if last expression of block.
      //        Since everything is an expression, if "x" is last expression, then give back "x"
      //        But if it's "x;" then it means we should return "nothing".
      case AstNoOp _ -> null;
      default -> throw new IllegalArgumentException(STR."Unknown AST Expression (\{expr.getClass().getSimpleName()}) '\{expr}'");
    };
  }

  private HirVariableDeclaration lower_variable_declaration(AstVariableDeclaration ast) {

    return new HirVariableDeclaration(
      lower_identifier(ast.identifier()),
      switch (ast.mutabilityKind()) {
        case Immutable -> HirMutabilityKind.IMMUTABLE;
        case Mutable -> HirMutabilityKind.MUTABLE;
      },
      ast.type() == null ? null : lower_type(ast.type())
    );
  }

  private HirExpression lower_type(AstExpression expression) {

    return switch (expression) {
      case AstType ast -> lower_identifier(ast.identifier());
      default -> throw new NotImplementedException(STR."Do not know how to handle '\{expression}'");
    };
  }

  private HirExpression lower_assignment(AstAssignment ast) {

    final var target = switch (ast.lhs()) {
      case AstVariableDeclaration lhs -> lower_expression(lhs);
      case AstIdentifier lhs -> lower_expression(lhs);
      default -> throw new NotImplementedException(STR."Do not know how to handle '\{ast.lhs()}' in assignment");
    };

    final var source = lower_expression(ast.rhs());

    return new HirAssignment(target, source);
  }

  private HirIdentifier lower_identifier(AstIdentifier ast) {
    return new HirIdentifier(ast.name());
  }

  private HirCall lower_call(AstCall ast) {

    final var target = lower_expression(ast.target());
    String targetName = switch (target) {
      case HirIdentifier hir -> hir.name();
      default -> throw new IllegalArgumentException(STR."Unknown call target '\{target}'");
    };

    final var hirParen = lower_paren(ast.paren());

    // TODO: This could be a HirTuple, but it is badly handled right now, and awful support for mixing positional and named arguments
    final var hirArgumentExpressions = switch (hirParen) {
      case HirExpressionCollection hir -> hir.children();
      case HirTuple hir -> hir.children();
      default -> throw new NotImplementedException();
    };

    final var varArgIndex = getKnownFunctionVarargIndex(targetName);

    // TODO: This is all rather stupid. We should NOT define functions based on the caller.
    //        This must be done in some other way, where we already have the available functions mapped.
    //        Will probably need to do multiple passes.
    //        OR -- This is partially fine and we are just describing what we believe the function will look like.
    //              Then it is up to later stages to validate and match them up.
    final var hirArguments = new HirArgument[hirArgumentExpressions.length];
    final var hirParameters = new HirParameter[hirArgumentExpressions.length - (varArgIndex == -1 ? 0 : (hirArgumentExpressions.length - varArgIndex))];
    for (var i = 0; i < hirArgumentExpressions.length; i++) {

      hirArguments[i] = switch (hirArgumentExpressions[i]) {
        case HirTupleKeyValue kv -> new HirArgument(kv.key().name(), kv.value());
        default -> new HirArgument(null, hirArgumentExpressions[i]);
      };

      if (i < varArgIndex) {

        // TODO: Can this be better partially resolved now, or just leave it to later stages to figure out?
        // TODO: Right now we only guessed the target ty from the literal, but might be a related but different type.
        final var parameterTy = switch (hirArguments[i].value()) {
          case HirLiteral literal -> literal.ty();
          default -> Ty.UNKNOWN;
        };

        hirParameters[i] = new HirParameter(null, parameterTy);
      }
    }

    // TODO: This is wrong. Or the best we can do at the moment? Up to later stages to actually resolve it?
    final var function = new HirFunction(
      new HirIdentifier(targetName),
      hirParameters,
      // TODO: These two are very hacky
      varArgIndex != -1,
      getKnownFunctionReturnTypes(targetName)
    );

    return new HirCall(
      new HirFunctionReference(function),
      hirArguments,
      false
    );
  }

  private Ty getKnownFunctionReturnTypes(String fnName) {

    return switch (fnName) {
      case "printf" -> Ty.INTEGER;
      default -> Ty.UNKNOWN;
    };
  }

  private int getKnownFunctionVarargIndex(String fnName) {

    return switch (fnName) {
      case "printf" -> 1;
      default -> -1;
    };
  }

  private HirExpression lower_block(AstBlock ast) {

    final var lowered = lower_expressions(ast.children());

    if (lowered.length == 1) {
      return lowered[0];
    }

    return new HirBlock(lowered);
  }

  private HirReturn lower_return(AstReturn ast) {
    return new HirReturn(lower_expression(ast.expression()));
  }

  private HirLiteral lower_literal(AstLiteral ast) {
    return new HirLiteral(ast.content(), ast.ty());
  }

  private HirExpression lower_binary_operation(AstBinaryOperation ast) {

    AstBinaryOperationKind expandedKind = switch (ast.type()) {
      case ADDITION_ASSIGNMENT -> AstBinaryOperationKind.ADD;
      case SUBTRACTION_ASSIGNMENT -> AstBinaryOperationKind.SUBTRACT;
      case MULTIPLY_ASSIGNMENT -> AstBinaryOperationKind.MULTIPLY;
      case DIVIDE_ASSIGNMENT -> AstBinaryOperationKind.DIVIDE;
      default -> null;
    };

    if (expandedKind != null) {
      return lower_expression(new AstAssignment(ast.lhs(), new AstBinaryOperation(ast.lhs(), expandedKind, ast.rhs())));
    }

    return lower_binary_operation_explicit(ast);
  }

  private HirBinaryOperation lower_binary_operation_explicit(AstBinaryOperation ast) {

    return new HirBinaryOperation(
      lower_expression(ast.lhs()),
      lower_binary_operation_type(ast.type()),
      lower_expression(ast.rhs())
    );
  }

  private HirBinaryOperationKind lower_binary_operation_type(AstBinaryOperationKind type) {
    return switch (type) {
      case ADD -> HirBinaryOperationKind.ADD;
      case SUBTRACT -> HirBinaryOperationKind.SUBTRACT;
      case AND -> HirBinaryOperationKind.AND;
      case BIT_AND -> HirBinaryOperationKind.BIT_AND;
      case BIT_OR -> HirBinaryOperationKind.BIT_OR;
      case BIT_SHIFT_LEFT -> HirBinaryOperationKind.BIT_SHIFT_LEFT;
      case BIT_SHIFT_RIGHT -> HirBinaryOperationKind.BIT_SHIFT_RIGHT;
      case MULTIPLY -> HirBinaryOperationKind.MULTIPLY;
      case DIVIDE -> HirBinaryOperationKind.DIVIDE;
      case EQUALS -> HirBinaryOperationKind.EQUALS;
      case NOT_EQUALS -> HirBinaryOperationKind.NOT_EQUALS;
      case GT -> HirBinaryOperationKind.GT;
      case GTE -> HirBinaryOperationKind.GTE;
      case IS -> HirBinaryOperationKind.IS;
      case LT -> HirBinaryOperationKind.LT;
      case LTE -> HirBinaryOperationKind.LTE;
      case MODULUS -> HirBinaryOperationKind.MODULUS;
      case OR -> HirBinaryOperationKind.OR;
      case POW -> HirBinaryOperationKind.POW;
      case REMAINDER -> HirBinaryOperationKind.REMAINDER;
      case ADDITION_ASSIGNMENT, SUBTRACTION_ASSIGNMENT, MULTIPLY_ASSIGNMENT, DIVIDE_ASSIGNMENT ->
        throw new IllegalArgumentException("Compound assignment binary operators must be expanded by caller not converted to HIR op kind");
    };
  }

  private HirExpression lower_loop_for(AstLoopFor astLoopFor) {

    final var loweredHead = lower_expression(astLoopFor.head());
    final var loweredBody = lower_expression(astLoopFor.block());

    final HirExpression[] loopFields;
    final HirExpression loopPredicate;
    final HirExpression loopAction;

    switch (loweredHead) {
      case HirExpressionCollection head -> {
        if (head.children().length == 3) {

          final var first = head.children()[0];
          switch (first) {
            case HirAssignment hir -> loopFields = new HirExpression[]{hir};
            case HirVariableDeclaration hir -> loopFields = new HirExpression[]{hir};
            default -> throw new NotImplementedException(STR."Unknown first for-loop part '\{first}'");
          }

          final var second = head.children()[1];
          switch (second) {
            case HirBinaryOperation hir -> {
              if (hir.kind().isPredicate()) {
                loopPredicate = hir;
              } else {
                throw new IllegalArgumentException(STR."The second for-loop part must be a predicate binary op, not '\{hir}'");
              }
            }
            case HirCall hir -> loopPredicate = hir;
            case HirIdentifier hir -> loopPredicate = hir;
            default -> throw new IllegalArgumentException(STR."The second for-loop part cannot be a '\{second}'");
          }

          final var third = head.children()[2];
          switch (third) {
//            case HirBinaryOperation hir -> {
//              if (hir.type().isAction()) {
//                hirAction = hir;
//              } else {
//                throw new IllegalArgumentException(STR."The third for-loop part must be an action binary op, not '\{hir}'");
//              }
//            }
            case HirAssignment hir -> loopAction = hir;
            default -> throw new IllegalArgumentException(STR."The second for-loop part cannot be a '\{third}'");
          }

        } else {
          throw new IllegalArgumentException(STR."A for-loop is a three-part expression list, not '\{loweredHead}'");
        }
      }
//      case HirTuple tuple ->
      default -> throw new NotImplementedException(STR."Unknown head expression '\{loweredHead}'");
    }

    final var loopExpressions = new HirExpression[loopFields.length + 1];
    System.arraycopy(loopFields, 0, loopExpressions, 0, loopFields.length);

    loopExpressions[loopExpressions.length - 1] = new HirLoop(

      // Q: Is it better if this was flipped and do nothing on fail but break on pass? Less branching???
      new HirConditional(
        loopPredicate,
        new HirExpressionCollection(new HirExpression[]{
          loweredBody,
          loopAction,
          new HirLoopContinue()
        }),
        new HirLoopBreak()
      )
    );

    return new HirExpressionCollection(loopExpressions);
  }

  private HirExpression lower_paren(AstParen astParen) {

    final var astExpr = astParen.expression();

    return switch (astExpr) {
      case AstExpressionCollection collection -> lower_paren_expression_collection(collection);
      default -> lower_expression(astExpr);

      //throw new NotImplementedException(STR."Unknown expression '\{astExpr}'");
    };
  }

  private HirExpression lower_paren_expression_collection(AstExpressionCollection astExpressionCollection) {

    var labeledExpressionCount = 0;
    var unlabeledExpressionCount = 0;

    final var children = astExpressionCollection.children();
    var children_lowered = new HirExpression[children.length];

    var targetIndex = 0;
    for (var i = 0; i < children.length; i++) {

      final var child = switch (children[i]) {
        case AstLabeling labeling -> {
          labeledExpressionCount++;
          yield lower_labeling_to_tuple_key_value(labeling);
        }
        default -> {
          unlabeledExpressionCount++;
          yield lower_expression(children[i]);
        }
      };

      if (child != null) {
        children_lowered[targetIndex] = child;
        targetIndex++;
      }
    }

    if (targetIndex != children_lowered.length) {

      final var shrunk = new HirExpression[targetIndex];
      System.arraycopy(children_lowered, 0, shrunk, 0, targetIndex);
      children_lowered = shrunk;
    }

    if (labeledExpressionCount > 0 && unlabeledExpressionCount == 0) {

      // This is a tuple. There are probably smarted ways of doing this.
      return new HirTuple(
        (HirTupleKeyValue[]) children_lowered
      );

    } else {
      return new HirExpressionCollection(children_lowered);
    }
  }

  private HirTupleKeyValue lower_labeling_to_tuple_key_value(AstLabeling astLabeling) {

    return new HirTupleKeyValue(
      lower_expression_to_identifier(astLabeling.lhs()),
      lower_expression(astLabeling.rhs())
    );
  }

  private HirIdentifier lower_expression_to_identifier(AstExpression astExpression) {

    throw new NotImplementedException();
  }

  public HirConditional lower_conditional(AstConditional astConditional) {

    final var fail = (astConditional.fail() == null)
      ? null //new HirLiteral("0", Ty.INTEGER)
      : lower_expression(astConditional.fail());

    return new HirConditional(
      lower_expression(astConditional.predicate()),
      lower_expression(astConditional.pass()),
      fail
    );
  }
}
