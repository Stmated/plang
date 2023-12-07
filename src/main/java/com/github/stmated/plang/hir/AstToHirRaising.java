package com.github.stmated.plang.hir;

import com.github.stmated.plang.ast.Ast;
import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.hir.passes.HirLexemeToIdentifierTransformerPass;
import com.github.stmated.plang.thir.raising.HirToThirRaising;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyValueNumber;
import com.github.stmated.plang.ty.util.MachineTarget;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The AstToHirRaising class is responsible for converting an AST program into a HIR program.
 * <p/>
 * Note that the AST representation does not necessarily have to match the logical tree-structure of the code.
 * <p>
 * The AST is rather a very high-level representation of the flow of the code, and not its meaning. For example an array access is an expression of some sort
 * followed by a bracket syntax. It is up to this AST -> HIR raising to notice the contextual significance of those brackets and turn it into an array access.
 */
public class AstToHirRaising {

  private final MachineTarget machineTarget;

  public AstToHirRaising(MachineTarget machineTarget) {
    this.machineTarget = machineTarget;
  }

  public static Hir.Expression lower_program(Ast.Program astProgram, MachineTarget machineTarget) {

    final var raising = new AstToHirRaising(machineTarget);

    final var program = new Hir.Program(raising.implicit_return(raising.lower(astProgram.children()), true));

    return HirLexemeToIdentifierTransformerPass.pass(program);
  }

  private Hir.Expression implicit_return(Hir.Expression expression, boolean program) {

    if (expression instanceof Hir.Block block) {
      expression = block.children();
    }

    if (expression instanceof Hir.Expressions exprs) {

      final var children = exprs.children();
      if (children.length > 1) {

        // TODO: This is bad since it will add a "return" even if all paths inside this are terminal
        //        We would need a visitor pattern to visit the last expression of every node, and see if it is terminal
        //        Only then should we add this implicit return...
        final var last = children[children.length - 1];
        if (!(last instanceof Hir.Return)) {

          final var implicitReturn = new Hir.Return(last, null);
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

    if (program && expression instanceof Hir.Assignment ass && ass.rhs() instanceof Hir.Function fn) {
      // Do not make the function declaration into a return.
      return expression;
    } else if (expression instanceof Hir.Return) {
      return expression;
    } else {
      return new Hir.Return(expression, null);
    }
  }

  private Hir.Expression[] lower_expressions(Ast.Expression[] expressions) {

    final var lowered = new Hir.Expression[expressions.length];
    var targetIndex = 0;
    for (var i = 0; i < expressions.length; i++) {
      final var hir = lower(expressions[i]); ;
      if (hir != null) {
        lowered[targetIndex] = hir;
        targetIndex++;
      }
    }

    if (targetIndex != lowered.length) {

      final var shrunk = new Hir.Expression[targetIndex];
      System.arraycopy(lowered, 0, shrunk, 0, targetIndex);
      return shrunk;
    }

    return lowered;
  }

  public Hir.Expression lower(Ast.Expression expr) {

    return switch (expr) {
      case Ast.LoopFor ast -> lower_loop_for(ast);
      case Ast.Conditional ast -> lower_conditional(ast);
      case Ast.BinaryOperation ast -> lower_binary_operation(ast);
      case Ast.Literal ast -> lower_literal(ast);
      case Ast.Return ast -> lower_return(ast);
      case Ast.Block ast -> lower_block(ast);
      case Ast.Call ast -> lower_call(ast);
      case Ast.BracketAccess ast -> lower_bracket_access(ast);
      case Ast.Lexeme ast -> lower_lexeme(ast);
      case Ast.Paren ast -> lower_paren(ast);
      case Ast.Bracket ast -> lower_bracket(ast);
      case Ast.Then ast -> lower(ast.expression());
      case Ast.VariableDeclaration ast -> lower_variable_declaration(ast);
      case Ast.Assignment ast -> lower_assignment(ast);
      case Ast.Labeling ast -> lower_labeling(ast);
      case Ast.Callable ast -> lower_callable(ast);
      case Ast.Expressions ast -> new Hir.Expressions(lower_expressions(ast.children()));
      case Ast.Struct ast -> lower_struct(ast);
      case Ast.New ast -> lower_new(ast);
      case Ast.DotAccess ast -> lower_dot_access(ast);
      case Ast.Comment _ -> null;
      // TODO: Important that a NoOp means "nothing" if last expression of block.
      //        Since everything is an expression, if "x" is last expression, then give back "x"
      //        But if it's "x;" then it means we should return "nothing".
      case Ast.NoOp _ -> null;
      default -> throw new IllegalArgumentException(STR."Unknown AST Expression (\{expr.getClass().getSimpleName()}) '\{expr}'");
    };
  }

  private Hir.Expression lower_dot_access(Ast.DotAccess ast) {

    final var elements = new ArrayList<Hir.Expression>();

    Ast.Expression pointer = ast;
    while (pointer instanceof Ast.DotAccess dot) {

      final var lhs = lower(dot.lhs());
      elements.add(lhs);

      pointer = ast.rhs();
    }

    // The last child of the path should be added.
    final var edge = lower(pointer);
    assert edge != null;
    elements.add(edge);

    return new Hir.Path(elements.toArray(new Hir.Expression[0]), null);
  }

  private Hir.Expression lower_new(Ast.New ast) {

    final var target = lower(ast.target());
    final var allocatorLexeme = lower_lexeme(ast.allocator());
    final var argumentExpr = lower(ast.arguments());

    final var allocatorIdentifier = new Hir.Identifier(allocatorLexeme, null, null);

    return switch (argumentExpr) {
      // This is a creation using `new Obj { Val = '1' }` syntax. Which all structs inherently can do.
      case Hir.Block block -> {
        final var assignments = new ArrayList<Hir.Assignment>();
        for (final var expr : expand(block.children())) {
          switch (expr) {
            case Hir.Assignment assignment -> assignments.add(assignment);
            default -> throw new UnexpectedExpressionException(expr);
          }
        }


        yield new Hir.NewByBlock(target, allocatorIdentifier, assignments.toArray(new Hir.Assignment[0]), null);
      }

      // This is a creation using `new Obj('1')` syntax, meaning it is trying to call a manually added constructor.
      case Hir.Expressions exprs -> new Hir.NewByCtor(target, allocatorIdentifier, exprs, null);
      default -> throw new UnexpectedExpressionException(argumentExpr);
    };
  }

  private Hir.Expression lower_struct(Ast.Struct ast) {

    final var declarations = new ArrayList<Hir.Dec>();

    if (ast.block() != null && ast.block().expression() != null) {
      final var block = lower(ast.block().expression());
      for (final var field : expand(block)) {

        switch (field) {
          case Hir.Dec dec -> {
            declarations.add(dec);
          }
          default -> throw new UnexpectedExpressionException(field);
        }
      }
    }

    return new Hir.Struct(declarations.toArray(new Hir.Dec[0]), null);
  }

  private Hir.Expression[] expand(Hir.Expression expr) {
    return switch (expr) {
      case Hir.Expressions exprs -> exprs.children();
      default -> new Hir.Expression[]{expr};
    };
  }

  private Hir.Expression lower_bracket(Ast.Bracket ast) {

    final var elements = new ArrayList<Hir.Expression>();
    var section = 0;

    final var sections = new Hir.Expression[3];

    for (final var entry : ast.children()) {

      if (entry instanceof Ast.NoOp) {
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

    final var elementArray = elements.toArray(new Hir.Expression[0]);

    if (section == 0) {

      // There are only elements. We will derive the rest from that.
      // TODO: Ty here should be "inferred" until THIR kicks in
      final var tyExpr = new Hir.TyExpr(Ty.INFER); // (elementArray.length > 0) ? getTy(elementArray[0]) : Ty.INFER);
      final var arrayLengthExpr = new Hir.Literal(Objects.toString(elementArray.length), Ty.INTEGER);
      return new Hir.Array(elementArray, tyExpr, arrayLengthExpr, null);

    } else if (section == 1) {

      final var tyExpr = new Hir.TyExpr(Ty.INFER); // new Hir.TyExpr((elementArray.length > 0) ? getTy(elementArray[0]) : Ty.INFER);
      final var size = sections[1];
      return new Hir.Array(elementArray, tyExpr, size, null);

    } else if (section == 2) {

      final var tyExpr = sections[1];
      // TODO: Ty here should be "inferred" until THIR kicks in (?) Or is info lost here?
      //final var exprArrayElementTy = getTyFromType(tyExpr);
      final var sizeExpr = sections[2];
      return new Hir.Array(elementArray, tyExpr, sizeExpr, null);

    } else {
      throw new IllegalArgumentException("Unknown array syntax");
    }
  }

//  private Ty getTy(Hir.Expression expr) {
//
//    final var thir = new HirToThirRaising(true, machineTarget);
//    final var found = thir.raise(expr).root().ty();
//    return Objects.requireNonNullElse(found, Ty.INFER);
//  }
//
//  private Ty getTyFromType(Hir.Expression expr) {
//
//    final var thir = new HirToThirRaising(true, machineTarget);
//    var found = thir.investigate_type_expression(expr);
//    if (found == null) {
//      found = thir.raise(expr).root().ty();
//    }
//
//    return Objects.requireNonNullElse(found, Ty.INFER);
//  }

  private Hir.Expression lower_labeling(Ast.Labeling ast) {

    if (ast.lhs() instanceof Ast.Paren lparen && ast.rhs() instanceof Ast.Lexeme rid) {

      // If this is true, then it is a callable function signature.
      // NOTE: This might not always be true, but we'll go for it for now.
      // TODO: The rhs must be more permissive than just "identifier", and lhs should be more restrictive (needs to be tuple)
      return find_and_lower_parameters(ast);
    }

    final var lhs = lower(ast.lhs());
    final var rhs = lower(ast.rhs());

    return new Hir.Labeling(lhs, rhs, null);
  }

  private Hir.Expression lower_callable(Ast.Callable ast) {

    final var signature = find_and_lower_parameters(ast.lhs());
    final var body = implicit_return(lower(ast.rhs()), false);

    return new Hir.Function(signature, body);
  }

  private Hir.FunctionSignature find_and_lower_parameters(Ast.Expression ast) {

    return switch (ast) {
      case Ast.Labeling labeling -> {

        final var signature = find_and_lower_parameters(labeling.lhs());
        final var returnTypeExpr = lower(labeling.rhs());

        yield new Hir.FunctionSignature(signature.parameters(), signature.vararg(), returnTypeExpr, null);
      }
      case Ast.Paren paren -> {

        final var signature = (paren.expression() == null) ? null : find_and_lower_parameters_inner(paren.expression());
        final var parameters = (signature == null) ? new Hir.Parameter[0] : signature.parameters();
        final var vararg = signature != null && signature.vararg();

        yield new Hir.FunctionSignature(parameters, vararg, new Hir.TyExpr(Ty.INFER), null);
      }
      default -> find_and_lower_parameters_inner(ast);
    };
  }

  private Hir.FunctionSignature find_and_lower_parameters_inner(Ast.Expression ast) {

    final var parameters = new ArrayList<Hir.Parameter>();
    var isVarArg = new AtomicBoolean(false);

    switch (ast) {
      case Ast.Expressions it -> {
        for (final var astParam : it.children()) {
          final var param = lower_parameter(astParam, isVarArg);
          if (param != null) {
            parameters.add(param);
            isVarArg.set(isVarArg.get() || param.vararg());
          }
        }
      }
      default -> parameters.add(lower_parameter(ast, isVarArg));
    }

    return new Hir.FunctionSignature(parameters.toArray(new Hir.Parameter[0]), isVarArg.get(), null, null);
  }

  private Hir.Parameter lower_parameter(Ast.Expression expr, AtomicBoolean restVararg) {

    return switch (expr) {
      case Ast.Spread spread -> new Hir.Parameter(asLexeme(spread.expression()), new Hir.TyExpr(Ty.INFER), true, null);
      case Ast.Rest _ -> {
        restVararg.set(true);
        yield null;
        //new Hir.Parameter(new Hir.Identifier("..."), null, true);
      }
      case Ast.Labeling labeling -> {
        final var labelingLhs = lower_parameter(labeling.lhs(), restVararg);
        final var labelingRhs = lower(labeling.rhs());
        yield new Hir.Parameter(labelingLhs.lexeme(), labelingRhs, labelingLhs.vararg(), null);
      }
      default -> new Hir.Parameter(asLexeme(expr), new Hir.TyExpr(Ty.INFER), false, null);
    };
  }

  private Hir.Lexeme asLexeme(Ast.Expression ast) {
    return switch (ast) {
      case Ast.Lexeme it -> lower_lexeme(it);
      default -> throw new UnexpectedExpressionException(ast);
    };
  }

  private Hir.Dec lower_variable_declaration(Ast.VariableDeclaration ast) {

    return new Hir.Dec(
      lower_lexeme(ast.lexeme()),
      switch (ast.mutabilityKind()) {
        case Immutable -> Hir.MutabilityKind.IMMUTABLE;
        case Mutable -> Hir.MutabilityKind.MUTABLE;
      },
      ast.type() == null ? new Hir.TyExpr(Ty.INFER) : lower(ast.type())
    );
  }

  private Hir.Expression lower_assignment(Ast.Assignment ast) {

    final var target = switch (ast.lhs()) {
      case Ast.VariableDeclaration lhs -> lower(lhs);
      case Ast.Lexeme lhs -> lower(lhs);
      case Ast.DotAccess lhs -> lower(lhs);
      default -> throw new UnexpectedExpressionException(ast.lhs());
    };

    final var source = lower(ast.rhs());

    return new Hir.Assignment(target, source);
  }

  private Hir.Lexeme lower_lexeme(Ast.Lexeme ast) {
    return new Hir.Lexeme(ast.name(), null);
  }

  private Hir.ArrayAccess lower_bracket_access(Ast.BracketAccess ast) {

    final var target = lower(ast.target());
    final var astAccessors = ast.accessor().children();
    final var hirAccessors = new Hir.Expression[astAccessors.length];
    for (var i = 0; i < astAccessors.length; i++) {
      hirAccessors[i] = lower(astAccessors[i]);
    }

    if (hirAccessors.length == 0) {
      throw new IllegalArgumentException("Missing array access index");
    } else if (hirAccessors.length == 1) {
      return new Hir.ArrayAccess(target, hirAccessors[0], null);
    } else {
      return new Hir.ArrayAccess(target, new Hir.Expressions(hirAccessors), null);
    }
  }

  private Hir.Call lower_call(Ast.Call ast) {

    final var target = lower(ast.target());
    final var hirParen = lower_paren(ast.paren());

    // TODO: This could be a HirTuple, but it is badly handled right now, and no support for mixing positional and named arguments
    final var hirArgumentExpressions = switch (hirParen) {
      case Hir.Expressions hir -> hir.children();
      case Hir.Tuple hir -> hir.children();
      default -> new Hir.Expression[]{hirParen};
      //throw new UnexpectedExpressionException(hirParen);
    };

    final var hirArguments = new Hir.Argument[hirArgumentExpressions.length];
    for (var i = 0; i < hirArgumentExpressions.length; i++) {

      hirArguments[i] = switch (hirArgumentExpressions[i]) {
        case Hir.TupleKeyValue kv -> new Hir.Argument(kv.key().lexeme(), kv.value());
        default -> new Hir.Argument(null, hirArgumentExpressions[i]);
      };
    }

    return new Hir.Call(target, hirArguments, ast.partial(), null);
  }

  private Hir.Expression lower_block(Ast.Block ast) {

    final var lowered = lower(ast.expression());
    return new Hir.Block(lowered, null);
  }

  private Hir.Return lower_return(Ast.Return ast) {
    return new Hir.Return(lower(ast.expression()), null);
  }

  private Hir.Literal lower_literal(Ast.Literal ast) {
    return new Hir.Literal(ast.content(), ast.ty());
  }

  private Hir.Expression lower_binary_operation(Ast.BinaryOperation ast) {

    Ast.BinaryOperationKind expandedKind = switch (ast.kind()) {
      case ADDITION_ASSIGNMENT -> Ast.BinaryOperationKind.ADD;
      case SUBTRACTION_ASSIGNMENT -> Ast.BinaryOperationKind.SUBTRACT;
      case MULTIPLY_ASSIGNMENT -> Ast.BinaryOperationKind.MULTIPLY;
      case DIVIDE_ASSIGNMENT -> Ast.BinaryOperationKind.DIVIDE;
      default -> null;
    };

    if (expandedKind != null) {
      return lower(new Ast.Assignment(ast.lhs(), new Ast.BinaryOperation(ast.lhs(), expandedKind, ast.rhs())));
    }

    return lower_binary_operation_explicit(ast);
  }

  private Hir.BinaryOperation lower_binary_operation_explicit(Ast.BinaryOperation ast) {

    return new Hir.BinaryOperation(
      lower(ast.lhs()),
      lower_binary_operation_type(ast.kind()),
      lower(ast.rhs()),
      null
    );
  }

  private Hir.BinaryOperationKind lower_binary_operation_type(Ast.BinaryOperationKind type) {
    return switch (type) {
      case ADD -> Hir.BinaryOperationKind.ADD;
      case SUBTRACT -> Hir.BinaryOperationKind.SUBTRACT;
      case AND -> Hir.BinaryOperationKind.AND;
      case BIT_AND -> Hir.BinaryOperationKind.BIT_AND;
      case BIT_OR -> Hir.BinaryOperationKind.BIT_OR;
      case BIT_SHIFT_LEFT -> Hir.BinaryOperationKind.BIT_SHIFT_LEFT;
      case BIT_SHIFT_RIGHT -> Hir.BinaryOperationKind.BIT_SHIFT_RIGHT;
      case MULTIPLY -> Hir.BinaryOperationKind.MULTIPLY;
      case DIVIDE -> Hir.BinaryOperationKind.DIVIDE;
      case EQUALS -> Hir.BinaryOperationKind.EQUALS;
      case NOT_EQUALS -> Hir.BinaryOperationKind.NOT_EQUALS;
      case GT -> Hir.BinaryOperationKind.GT;
      case GTE -> Hir.BinaryOperationKind.GTE;
      case IS -> Hir.BinaryOperationKind.IS;
      case LT -> Hir.BinaryOperationKind.LT;
      case LTE -> Hir.BinaryOperationKind.LTE;
      case MODULUS -> Hir.BinaryOperationKind.MODULUS;
      case OR -> Hir.BinaryOperationKind.OR;
      case POW -> Hir.BinaryOperationKind.POW;
      case REMAINDER -> Hir.BinaryOperationKind.REMAINDER;
      case ADDITION_ASSIGNMENT, SUBTRACTION_ASSIGNMENT, MULTIPLY_ASSIGNMENT, DIVIDE_ASSIGNMENT ->
        throw new IllegalArgumentException("Compound assignment binary operators must be expanded by caller not converted to HIR op kind");
    };
  }

  private Hir.Expression lower_loop_for(Ast.LoopFor astLoopFor) {

    final var loweredHead = lower(astLoopFor.head());
    final var loweredBody = lower(astLoopFor.block());

    final Hir.Expression[] loopFields;
    final Hir.Expression loopPredicate;
    final Hir.Expression loopAction;

    switch (loweredHead) {
      case Hir.Expressions head -> {
        if (head.children().length == 3) {

          final var first = head.children()[0];
          switch (first) {
            case Hir.Assignment hir -> loopFields = new Hir.Expression[]{hir};
            case Hir.Dec hir -> loopFields = new Hir.Expression[]{hir};
            default -> throw new NotImplementedException(STR."Unknown first for-loop part '\{first}'");
          }

          final var second = head.children()[1];
          switch (second) {
            case Hir.BinaryOperation hir -> {
              if (hir.kind().isPredicate()) {
                loopPredicate = hir;
              } else {
                throw new IllegalArgumentException(STR."The second for-loop part must be a predicate binary op, not '\{hir}'");
              }
            }
            case Hir.Call hir -> loopPredicate = hir;
            case Hir.Identifier hir -> loopPredicate = hir;
            default -> throw new IllegalArgumentException(STR."The second for-loop part cannot be a '\{second}'");
          }

          final var third = head.children()[2];
          switch (third) {
            case Hir.Assignment hir -> loopAction = hir;
            default -> throw new IllegalArgumentException(STR."The second for-loop part cannot be a '\{third}'");
          }

        } else {
          throw new IllegalArgumentException(STR."A for-loop is a three-part expression list, not '\{loweredHead}'");
        }
      }
      default -> throw new NotImplementedException(STR."Unknown head expression '\{loweredHead}'");
    }

    final var loopExpressions = new Hir.Expression[loopFields.length + 1];
    System.arraycopy(loopFields, 0, loopExpressions, 0, loopFields.length);

    loopExpressions[loopExpressions.length - 1] = new Hir.Loop(

      // Q: Is it better if this was flipped and do nothing on fail but break on pass? Less branching???
      new Hir.Conditional(
        loopPredicate,
        new Hir.Expressions(
          new Hir.Expression[]{
            loweredBody,
            loopAction,
            new Hir.LoopContinue()
          },
          null
        ),
        // TODO: Add support for adding value to the break
        new Hir.LoopBreak(null, null),
        null
      ),
      null
    );

    return new Hir.Expressions(loopExpressions);
  }

  private Hir.Expression lower_paren(Ast.Paren astParen) {

    final var astExpr = astParen.expression();
    if (astExpr == null) {
      return new Hir.Expressions(new Hir.Expression[0]);
    }

    return switch (astExpr) {
      case Ast.Expressions collection -> lower_paren_expression_collection(collection);
      default -> lower(astExpr);
    };
  }

  private Hir.Expression lower_paren_expression_collection(Ast.Expressions astExpressions) {

    var labeledExpressionCount = 0;
    var unlabeledExpressionCount = 0;

    final var children = astExpressions.children();
    var children_lowered = new Hir.Expression[children.length];

    var targetIndex = 0;
    for (var i = 0; i < children.length; i++) {

      final var child = switch (children[i]) {
        case Ast.Labeling labeling -> {
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

      final var shrunk = new Hir.Expression[targetIndex];
      System.arraycopy(children_lowered, 0, shrunk, 0, targetIndex);
      children_lowered = shrunk;
    }

    if (labeledExpressionCount > 0 && unlabeledExpressionCount == 0) {

      // This is a tuple. There are probably smarted ways of doing this.
      return new Hir.Tuple(
        (Hir.TupleKeyValue[]) children_lowered,
        null
      );

    } else {
      return new Hir.Expressions(children_lowered);
    }
  }

  private Hir.TupleKeyValue lower_labeling_to_tuple_key_value(Ast.Labeling astLabeling) {

    return new Hir.TupleKeyValue(
      lower_expression_to_identifier(astLabeling.lhs()),
      lower(astLabeling.rhs()),
      null
    );
  }

  private Hir.Identifier lower_expression_to_identifier(Ast.Expression expression) {

    throw new NotImplementedException();
  }

  public Hir.Conditional lower_conditional(Ast.Conditional astConditional) {

    final var fail = (astConditional.fail() == null)
      ? null
      : lower(astConditional.fail());

    return new Hir.Conditional(
      lower(astConditional.predicate()),
      lower(astConditional.pass()),
      fail,
      null
    );
  }
}
