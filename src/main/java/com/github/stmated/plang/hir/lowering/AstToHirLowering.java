package com.github.stmated.plang.hir.lowering;

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
import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.hir.model.HirArgument;
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
import com.github.stmated.plang.hir.model.HirParameter;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import com.github.stmated.plang.hir.model.HirTuple;
import com.github.stmated.plang.hir.model.HirTupleKeyValue;
import com.github.stmated.plang.hir.model.HirType;
import com.github.stmated.plang.hir.model.HirVariableDeclaration;

public class AstToHirLowering {

  public HirProgram lower_program(AstProgram astProgram) {
    return new HirProgram(lower_expressions(astProgram.children()));
  }

  private HirExpression[] lower_expressions(AstExpression[] astExpressions) {

    final var lowered = new HirExpression[astExpressions.length];
    var targetIndex = 0;
    for (var i = 0; i < astExpressions.length; i++) {
      final var hir = lower_expression(astExpressions[i]);;
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
      case AstNoOp ast -> null;
      default ->
        throw new IllegalArgumentException(STR."Unknown AST Expression (\{expr.getClass().getSimpleName()}) '\{expr}'");
    };
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
    final HirExpression[] hirArgumentExpressions = switch (hirParen) {
      case HirExpressionCollection hir -> hir.children();
      default -> throw new NotImplementedException();
    };

    final var hirArguments = new HirArgument[hirArgumentExpressions.length];
    for (var i = 0; i < hirArgumentExpressions.length; i++) {
      hirArguments[i] = new HirArgument(hirArgumentExpressions[i]);
    }

    // TODO: There should be another layer between HIR and LLVM (the MIR).
    //        In the MIR, the function call should always refer to a function pointer in global space (maybe?)
    return new HirCall(
      new HirFunctionReference(
        new HirFunction(
          new HirIdentifier(targetName),
          new HirParameter[0], // TODO: This needs to be added
          new HirType(new HirIdentifier("Unknown"))
        )
      ),
      hirArguments,
      false
    );
  }

  private HirExpression lower_block(AstBlock ast) {

    final var lowered = lower_expressions(ast.children());

    if (lowered.length == 1) {
      return lower_expression(ast.children()[0]);
    }

    return new HirBlock(lowered);
  }

  private HirReturn lower_return(AstReturn ast) {
    return new HirReturn(lower_expression(ast.expression()));
  }

  private HirLiteral lower_literal(AstLiteral ast) {
    return new HirLiteral(ast.value());
  }

  private HirBinaryOperation lower_binary_operation(AstBinaryOperation ast) {

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
      case DIVIDE -> HirBinaryOperationKind.DIVIDE;
      case EQUALS -> HirBinaryOperationKind.EQUALS;
      case GT -> HirBinaryOperationKind.GT;
      case GTE -> HirBinaryOperationKind.GTE;
      case IS -> HirBinaryOperationKind.IS;
      case LT -> HirBinaryOperationKind.LT;
      case LTE -> HirBinaryOperationKind.LTE;
      case MODULUS -> HirBinaryOperationKind.MODULUS;
      case MULTIPLY -> HirBinaryOperationKind.MULTIPLY;
      case OR -> HirBinaryOperationKind.OR;
      case POW -> HirBinaryOperationKind.POW;
      case REMAINDER -> HirBinaryOperationKind.REMAINDER;
    };
  }

  private HirLoop lower_loop_for(AstLoopFor astLoopFor) {

    final var loweredHead = lower_expression(astLoopFor.head());

    final HirVariableDeclaration[] loopCounterFields;

    switch (loweredHead) {
//      case HirTuple tuple -> {
//
//      }
      default -> throw new IllegalArgumentException(STR."Unknown head expression '\{loweredHead}'");
    }

//    final var loopBody = new HirBlock(new HirExpression[] {
//
//    });

//    final var loop = new HirLoop(loopBody);
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
    final var children_lowered = new HirExpression[children.length];

    for (var i = 0; i < children.length; i++) {

      children_lowered[i] = switch (children[i]) {
        case AstLabeling labeling -> {
          labeledExpressionCount++;
          yield lower_labeling_to_tuple_key_value(labeling);
        }
        default -> {
          unlabeledExpressionCount++;
          yield lower_expression(children[i]);
        }
      };
    }

    if (labeledExpressionCount > 0 && unlabeledExpressionCount == 0) {

      // This is a tuple. There are probably smarted ways of doing this.
      return new HirTuple(
        (HirTupleKeyValue[]) children_lowered
      );

    } else {

      return new HirExpressionCollection(children_lowered);

      //throw new NotImplementedException();
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

    // A conditional is never allowed to not have an "else" -- we must always produce a value
    final var fail = astConditional.fail() == null
      ? new HirLiteral(null) // TODO: THIS IS WRONG! It should be some kind of "Optional"
      : lower_expression(astConditional.fail());

    return new HirConditional(
      lower_expression(astConditional.predicate()),
      lower_expression(astConditional.pass()),
      fail
    );
  }
}
