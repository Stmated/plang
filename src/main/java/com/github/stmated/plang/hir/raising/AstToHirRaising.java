package com.github.stmated.plang.hir.raising;

import com.github.stmated.plang.ast.model.AstAssignment;
import com.github.stmated.plang.ast.model.AstBinaryOperation;
import com.github.stmated.plang.ast.model.AstBinaryOperationKind;
import com.github.stmated.plang.ast.model.AstBlock;
import com.github.stmated.plang.ast.model.AstBracket;
import com.github.stmated.plang.ast.model.AstBracketAccess;
import com.github.stmated.plang.ast.model.AstCall;
import com.github.stmated.plang.ast.model.AstCallable;
import com.github.stmated.plang.ast.model.AstConditional;
import com.github.stmated.plang.ast.model.AstDotAccess;
import com.github.stmated.plang.ast.model.AstExpression;
import com.github.stmated.plang.ast.model.AstExpressions;
import com.github.stmated.plang.ast.model.AstIdentifier;
import com.github.stmated.plang.ast.model.AstLabeling;
import com.github.stmated.plang.ast.model.AstLiteral;
import com.github.stmated.plang.ast.model.AstLoopFor;
import com.github.stmated.plang.ast.model.AstNew;
import com.github.stmated.plang.ast.model.AstNoOp;
import com.github.stmated.plang.ast.model.AstParen;
import com.github.stmated.plang.ast.model.AstProgram;
import com.github.stmated.plang.ast.model.AstReturn;
import com.github.stmated.plang.ast.model.AstSpread;
import com.github.stmated.plang.ast.model.AstStruct;
import com.github.stmated.plang.ast.model.AstThen;
import com.github.stmated.plang.ast.model.AstVariableDeclaration;
import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.hir.model.HirArgument;
import com.github.stmated.plang.hir.model.HirArray;
import com.github.stmated.plang.hir.model.HirArrayAccess;
import com.github.stmated.plang.hir.model.HirAssignment;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirBinaryOperationKind;
import com.github.stmated.plang.hir.model.HirBlock;
import com.github.stmated.plang.hir.model.HirCall;
import com.github.stmated.plang.hir.model.HirConditional;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirExpressions;
import com.github.stmated.plang.hir.model.HirFunction;
import com.github.stmated.plang.hir.model.HirFunctionSignature;
import com.github.stmated.plang.hir.model.HirIdentifier;
import com.github.stmated.plang.hir.model.HirLabeling;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirLoop;
import com.github.stmated.plang.hir.model.HirLoopBreak;
import com.github.stmated.plang.hir.model.HirLoopContinue;
import com.github.stmated.plang.hir.model.HirMutabilityKind;
import com.github.stmated.plang.hir.model.HirNewByBlock;
import com.github.stmated.plang.hir.model.HirNewByCtor;
import com.github.stmated.plang.hir.model.HirParameter;
import com.github.stmated.plang.hir.model.HirPath;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import com.github.stmated.plang.hir.model.HirStruct;
import com.github.stmated.plang.hir.model.HirTuple;
import com.github.stmated.plang.hir.model.HirTupleKeyValue;
import com.github.stmated.plang.hir.model.HirTy;
import com.github.stmated.plang.hir.model.HirVariableDeclaration;
import com.github.stmated.plang.thir.raising.HirToThirRaising;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyValueNumber;
import java.util.ArrayList;
import java.util.Objects;

/**
 * The AstToHirRaising class is responsible for converting an AST program into a HIR program.
 * <p/>
 * Note that the AST representation does not necessarily have to match the logical tree-structure of the code.
 * <p>
 * The AST is rather a very high-level representation of the flow of the code, and not its meaning. For example an array access is an expression of some sort
 * followed by a bracket syntax. It is up to this AST -> HIR raising to notice the contextual significance of those brackets and turn it into an array access.
 */
public class AstToHirRaising {

  public HirProgram lower_program(AstProgram astProgram) {
    return new HirProgram(implicit_return(lower(astProgram.children())));
  }

  private HirExpression implicit_return(HirExpression expression) {

    if (expression instanceof HirExpressions exprs) {

      final var children = exprs.children();
      if (children.length > 1) {

        // TODO: This is bad since it will add a "return" even if all paths inside this are terminal
        //        We would need a visitor pattern to visit the last expression of every node, and see if it is terminal
        //        Only then should we add this implicit return...
        final var last = children[children.length - 1];
        if (!(last instanceof HirReturn)) {

          final var implicitReturn = new HirReturn(last);
          children[children.length - 1] = implicitReturn;

          return exprs;
        } else {
          return expression;
        }
      } else if (children.length > 0) {
        expression = children[0];
      } else {

        // TODO: This should probably just give VOID. Implement a way to return void as a literal.
        throw new IllegalArgumentException("There was no expression");
      }
    }

    if (expression instanceof HirReturn) {
      return expression;
    } else {
      return new HirReturn(expression);
    }
  }

  private HirExpression[] lower_expressions(AstExpression[] astExpressions) {

    final var lowered = new HirExpression[astExpressions.length];
    var targetIndex = 0;
    for (var i = 0; i < astExpressions.length; i++) {
      final var hir = lower(astExpressions[i]); ;
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

  public HirExpression lower(AstExpression expr) {

    return switch (expr) {
      case AstLoopFor ast -> lower_loop_for(ast);
      case AstConditional ast -> lower_conditional(ast);
      case AstBinaryOperation ast -> lower_binary_operation(ast);
      case AstLiteral ast -> lower_literal(ast);
      case AstReturn ast -> lower_return(ast);
      case AstBlock ast -> lower_block(ast);
      case AstCall ast -> lower_call(ast);
      case AstBracketAccess ast -> lower_bracket_access(ast);
      case AstIdentifier ast -> lower_identifier(ast);
      case AstParen ast -> lower_paren(ast);
      case AstBracket ast -> lower_bracket(ast);
      case AstThen ast -> lower(ast.expression());
      case AstVariableDeclaration ast -> lower_variable_declaration(ast);
      case AstAssignment ast -> lower_assignment(ast);
      case AstLabeling ast -> lower_labeling(ast);
      case AstCallable ast -> lower_callable(ast);
      case AstExpressions ast -> new HirExpressions(lower_expressions(ast.children()));
      case AstStruct ast -> lower_struct(ast);
      case AstNew ast -> lower_new(ast);
      case AstDotAccess ast -> lower_dot_access(ast);
      // TODO: Important that a NoOp means "nothing" if last expression of block.
      //        Since everything is an expression, if "x" is last expression, then give back "x"
      //        But if it's "x;" then it means we should return "nothing".
      case AstNoOp _ -> null;
      default -> throw new IllegalArgumentException(STR."Unknown AST Expression (\{expr.getClass().getSimpleName()}) '\{expr}'");
    };
  }

  private HirExpression lower_dot_access(AstDotAccess ast) {

    final var elements = new ArrayList<HirExpression>();

    AstExpression pointer = ast;
    while (pointer instanceof AstDotAccess dot) {

      final var lhs = lower(dot.lhs());
      elements.add(lhs);

      pointer = ast.rhs();
    }

    // The last child of the path should be added.
    final var edge = lower(pointer);
    assert edge != null;
    elements.add(edge);

    return new HirPath(elements.toArray(new HirExpression[0]));
  }

  private HirExpression lower_new(AstNew ast) {

    final var target = lower(ast.target());
    final var allocator = lower_identifier(ast.allocator());
    final var argumentExpr = lower(ast.arguments());

    return switch (argumentExpr) {
      // This is a creation using `new Obj { Val = '1' }` syntax. Which all structs inherently can do.
      case HirBlock block -> {
        final var assignments = new ArrayList<HirAssignment>();
        for (final var expr : expand(block.children())) {
          switch (expr) {
            case HirAssignment assignment -> assignments.add(assignment);
            default -> throw new UnexpectedExpressionException(expr);
          }
        }

        yield new HirNewByBlock(target, allocator, assignments.toArray(new HirAssignment[0]));
      }

      // This is a creation using `new Obj('1')` syntax, meaning it is trying to call a manually added constructor.
      case HirExpressions exprs -> new HirNewByCtor(target, allocator, exprs);
      default -> throw new UnexpectedExpressionException(argumentExpr);
    };
  }

  private HirExpression lower_struct(AstStruct ast) {

    final var declarations = new ArrayList<HirVariableDeclaration>();
    final var block = lower(ast.block().expression());

    for (final var field : expand(block)){

      switch (field) {
        case HirVariableDeclaration dec -> {
          declarations.add(dec);
        }
        default -> throw new UnexpectedExpressionException(field);
      }
    }

    return new HirStruct(declarations.toArray(new HirVariableDeclaration[0]));
  }

  private HirExpression[] expand(HirExpression expr) {
    return switch (expr) {
      case HirExpressions exprs -> exprs.children();
      default -> new HirExpression[]{expr};
    };
  }

  private HirExpression lower_bracket(AstBracket ast) {

    final var elements = new ArrayList<HirExpression>();
    var section = 0;

    final var sections = new HirExpression[3];

    for (final var entry : ast.children()) {

      if (entry instanceof AstNoOp) {
        section++;
        continue;
      }

      final var lowered = lower(entry);

      if (section == 0) {
        elements.add(lowered);
      } else {
        if (sections[section] == null) {
          sections[section] = lowered;
        } else {
          throw new IllegalArgumentException("Illegal array syntax, only one initializer allowed");
        }
      }
    }

    // 0: [0, 1, 2] -- initialize with values
    // 1: [0u8; 500] -- initialize with 0, type inferred uint8, size 500
    // 2: [;uint8;500] -- do not initialize, type uint8, size 500
    // 2: [1,2,3;uint8;500] -- initialize with 1,2,3 repeating, type uint8, size 500

    final var elementArray = elements.toArray(new HirExpression[0]);

    if (section == 0) {

      // There are only elements. We will derive the rest from that.
      final var tyExpr = new HirTy((elementArray.length > 0) ? getTy(elementArray[0]) : Ty.INFER);
      return new HirArray(elementArray, tyExpr, new HirLiteral(Objects.toString(elementArray.length), Ty.INTEGER));

    } else if (section == 1) {

      final var tyExpr = new HirTy((elementArray.length > 0) ? getTy(elementArray[0]) : Ty.INFER);
      final var size = sections[1];
      return new HirArray(elementArray, tyExpr, size);

    } else if (section == 2) {

      final var tyExpr = sections[1];
      final var explArrayElementTy = getTyFromType(tyExpr);

      if (explArrayElementTy instanceof TyValueNumber vn) {
        for (var i = 0; i < elementArray.length; i++) {

          final var element = elementArray[i];
          if (element instanceof HirLiteral lit && lit.ty() instanceof TyValueNumber litTy_n) {
            if (!litTy_n.width().explicit() && (litTy_n.width().value() != vn.width().value() || litTy_n.signed() != vn.signed())) {

              // val array = [1, 2, 3; uint8]
              // val array = [1, 2, 3; uint]
              // Should automatically translate non-explicit integers in array to stated type.
              elementArray[i] = new HirLiteral(lit.content(), vn);
            }
          }
        }
      }

      final var size = sections[2];
      return new HirArray(elementArray, tyExpr, size);

    } else {
      throw new IllegalArgumentException("Unknown array syntax");
    }
  }

  private Ty getTy(HirExpression expr) {

    final var thir = new HirToThirRaising(true);
    final var found = thir.raise(expr).getType(expr);
    return Objects.requireNonNullElse(found, Ty.INFER);
  }

  private Ty getTyFromType(HirExpression expr) {

    final var thir = new HirToThirRaising(true);
    var found = thir.investigate_type_expression(expr);
    if (found == null) {
      found = thir.raise(expr).getType(expr);
    }

    return Objects.requireNonNullElse(found, Ty.INFER);
  }

  private HirExpression lower_labeling(AstLabeling ast) {

    final var lhs = lower(ast.lhs());
    final var rhs = lower(ast.rhs());

    return new HirLabeling(lhs, rhs);
  }

  private HirExpression lower_callable(AstCallable ast) {

    final var signature = find_and_lower_parameters(ast.lhs());
    final var body = implicit_return(lower(ast.rhs()));

    return new HirFunction(signature, body);
  }

  private HirFunctionSignature find_and_lower_parameters(AstExpression ast) {

    return switch (ast) {
      case AstLabeling labeling -> {

        final var signature = find_and_lower_parameters(labeling.lhs());
        final var returnTypeExpr = lower(labeling.rhs());

        yield new HirFunctionSignature(signature.parameters(), signature.vararg(), returnTypeExpr);
      }
      case AstParen paren -> {

        final var signature = (paren.expression() == null) ? null : find_and_lower_parameters_inner(paren.expression());
        final var parameters = (signature == null) ? new HirParameter[0] : signature.parameters();
        final var vararg = signature != null && signature.vararg();

        yield new HirFunctionSignature(parameters, vararg, new HirTy(Ty.INFER));
      }
      default -> find_and_lower_parameters_inner(ast);
    };
  }

  private HirFunctionSignature find_and_lower_parameters_inner(AstExpression ast) {

    final var parameters = new ArrayList<HirParameter>();

    switch (ast) {
      case AstExpressions it -> {
        for (final var astParam : it.children()) {
          final var param = lower_parameter(astParam);
          parameters.add(param);
        }
      }
      default -> throw new NotImplementedException(STR."Do not know how to handle '\{ast}' as callable lhs");
    }

    final var isVarArg = parameters.stream().anyMatch(HirParameter::vararg);
    return new HirFunctionSignature(parameters.toArray(new HirParameter[0]), isVarArg, null);
  }

  private HirParameter lower_parameter(AstExpression expr) {

    return switch (expr) {
      case AstSpread spread -> new HirParameter(lower(spread.expression()), new HirTy(Ty.INFER), true);
      case AstLabeling labeling -> {
        final var labelingLhs = lower_parameter(labeling.lhs());
        final var labelingRhs = lower(labeling.rhs());
        yield new HirParameter(labelingLhs.identifier(), labelingRhs, labelingLhs.vararg());
      }
      default -> new HirParameter(lower(expr), new HirTy(Ty.INFER), false);
    };
  }

  private HirVariableDeclaration lower_variable_declaration(AstVariableDeclaration ast) {

    return new HirVariableDeclaration(
      lower_identifier(ast.identifier()),
      switch (ast.mutabilityKind()) {
        case Immutable -> HirMutabilityKind.IMMUTABLE;
        case Mutable -> HirMutabilityKind.MUTABLE;
      },
      ast.type() == null ? new HirTy(Ty.INFER) : lower(ast.type())
    );
  }

//  private HirExpression lower_type(AstExpression expression) {
//
//    return switch (expression) {
//      case AstType ast -> lower_identifier(ast.identifier());
//      default -> throw new NotImplementedException(STR."Do not know how to handle '\{expression}' (\{expression.getClass().getSimpleName()}})");
//    };
//  }

  private HirExpression lower_assignment(AstAssignment ast) {

    final var target = switch (ast.lhs()) {
      case AstVariableDeclaration lhs -> lower(lhs);
      case AstIdentifier lhs -> lower(lhs);
      case AstDotAccess lhs -> lower(lhs);
      default -> throw new UnexpectedExpressionException(ast.lhs());
    };

    final var source = lower(ast.rhs());

    return new HirAssignment(target, source);
  }

  private HirIdentifier lower_identifier(AstIdentifier ast) {
    return new HirIdentifier(ast.name());
  }

  private HirArrayAccess lower_bracket_access(AstBracketAccess ast) {

    final var target = lower(ast.target());
    final var astAccessors = ast.accessor().children();
    final var hirAccessors = new HirExpression[astAccessors.length];
    for (var i = 0; i < astAccessors.length; i++) {
      hirAccessors[i] = lower(astAccessors[i]);
    }

    if (hirAccessors.length == 0) {
      throw new IllegalArgumentException("Missing array access index");
    } else if (hirAccessors.length == 1) {
      return new HirArrayAccess(target, hirAccessors[0]);
    } else {
      return new HirArrayAccess(target, new HirExpressions(hirAccessors));
    }
  }

  private HirCall lower_call(AstCall ast) {

    final var target = lower(ast.target());
    final var hirParen = lower_paren(ast.paren());

    // TODO: This could be a HirTuple, but it is badly handled right now, and awful support for mixing positional and named arguments
    final var hirArgumentExpressions = switch (hirParen) {
      case HirExpressions hir -> hir.children();
      case HirTuple hir -> hir.children();
      default -> throw new NotImplementedException();
    };

    final var hirArguments = new HirArgument[hirArgumentExpressions.length];
    for (var i = 0; i < hirArgumentExpressions.length; i++) {

      hirArguments[i] = switch (hirArgumentExpressions[i]) {
        case HirTupleKeyValue kv -> new HirArgument(kv.key().name(), kv.value());
        default -> new HirArgument(null, hirArgumentExpressions[i]);
      };
    }

    return new HirCall(target, hirArguments, ast.partial());
  }

  private HirExpression lower_block(AstBlock ast) {

    final var lowered = lower(ast.expression());
    return new HirBlock(lowered);
  }

  private HirReturn lower_return(AstReturn ast) {
    return new HirReturn(lower(ast.expression()));
  }

  private HirLiteral lower_literal(AstLiteral ast) {
    return new HirLiteral(ast.content(), ast.ty());
  }

  private HirExpression lower_binary_operation(AstBinaryOperation ast) {

    AstBinaryOperationKind expandedKind = switch (ast.kind()) {
      case ADDITION_ASSIGNMENT -> AstBinaryOperationKind.ADD;
      case SUBTRACTION_ASSIGNMENT -> AstBinaryOperationKind.SUBTRACT;
      case MULTIPLY_ASSIGNMENT -> AstBinaryOperationKind.MULTIPLY;
      case DIVIDE_ASSIGNMENT -> AstBinaryOperationKind.DIVIDE;
      default -> null;
    };

    if (expandedKind != null) {
      return lower(new AstAssignment(ast.lhs(), new AstBinaryOperation(ast.lhs(), expandedKind, ast.rhs())));
    }

    return lower_binary_operation_explicit(ast);
  }

  private HirBinaryOperation lower_binary_operation_explicit(AstBinaryOperation ast) {

    return new HirBinaryOperation(
      lower(ast.lhs()),
      lower_binary_operation_type(ast.kind()),
      lower(ast.rhs())
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

    final var loweredHead = lower(astLoopFor.head());
    final var loweredBody = lower(astLoopFor.block());

    final HirExpression[] loopFields;
    final HirExpression loopPredicate;
    final HirExpression loopAction;

    switch (loweredHead) {
      case HirExpressions head -> {
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
            case HirAssignment hir -> loopAction = hir;
            default -> throw new IllegalArgumentException(STR."The second for-loop part cannot be a '\{third}'");
          }

        } else {
          throw new IllegalArgumentException(STR."A for-loop is a three-part expression list, not '\{loweredHead}'");
        }
      }
      default -> throw new NotImplementedException(STR."Unknown head expression '\{loweredHead}'");
    }

    final var loopExpressions = new HirExpression[loopFields.length + 1];
    System.arraycopy(loopFields, 0, loopExpressions, 0, loopFields.length);

    loopExpressions[loopExpressions.length - 1] = new HirLoop(

      // Q: Is it better if this was flipped and do nothing on fail but break on pass? Less branching???
      new HirConditional(
        loopPredicate,
        new HirExpressions(new HirExpression[]{
          loweredBody,
          loopAction,
          new HirLoopContinue()
        }),
        // TODO: Add support for adding value to the break
        new HirLoopBreak(null)
      )
    );

    return new HirExpressions(loopExpressions);
  }

  private HirExpression lower_paren(AstParen astParen) {

    final var astExpr = astParen.expression();
    if (astExpr == null) {
      return new HirExpressions(new HirExpression[0]);
    }

    return switch (astExpr) {
      case AstExpressions collection -> lower_paren_expression_collection(collection);
      default -> lower(astExpr);
    };
  }

  private HirExpression lower_paren_expression_collection(AstExpressions astExpressions) {

    var labeledExpressionCount = 0;
    var unlabeledExpressionCount = 0;

    final var children = astExpressions.children();
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
          yield lower(children[i]);
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
      return new HirExpressions(children_lowered);
    }
  }

  private HirTupleKeyValue lower_labeling_to_tuple_key_value(AstLabeling astLabeling) {

    return new HirTupleKeyValue(
      lower_expression_to_identifier(astLabeling.lhs()),
      lower(astLabeling.rhs())
    );
  }

  private HirIdentifier lower_expression_to_identifier(AstExpression astExpression) {

    throw new NotImplementedException();
  }

  public HirConditional lower_conditional(AstConditional astConditional) {

    final var fail = (astConditional.fail() == null)
      ? null
      : lower(astConditional.fail());

    return new HirConditional(
      lower(astConditional.predicate()),
      lower(astConditional.pass()),
      fail
    );
  }
}
