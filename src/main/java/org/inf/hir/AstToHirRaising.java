package org.inf.hir;

import org.inf.ast.Ast;
import org.inf.ast.AstVisitor;
import org.inf.exceptions.NotImplementedException;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.passes.HirIndexedPathTransformerPass;
import org.inf.hir.passes.HirLexemeToIdentifierTransformerPass;
import org.inf.hir.passes.HirSimplifyTransformerPass;
import org.inf.ty.Ty;
import org.inf.ty.util.MachineTarget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/// The AstToHirRaising class is responsible for converting an AST program into a HIR program.
///
/// Note that the AST representation does not necessarily have to match the logical tree-structure of the code.
///
/// The AST is rather a very high-level representation of the flow of the code, and not its meaning. For example an array access is an expression of some sort
/// followed by a bracket syntax. It is up to this AST -> HIR raising to notice the contextual significance of those brackets and turn it into an array access.
public class AstToHirRaising {

  private final MachineTarget machineTarget;

  public AstToHirRaising(final MachineTarget machineTarget) {
    this.machineTarget = machineTarget;
  }

  public static Hir.Expression lower_program(final Ast.Program astProgram, final MachineTarget machineTarget) {

    final var raising = new AstToHirRaising(machineTarget);
    final var raised = raising.raise(astProgram.children());
    final var implicitlyReturned = raising.implicit_return(raised, true);

    final var program = new Hir.Program(implicitlyReturned);
    final var indexedPaths = HirIndexedPathTransformerPass.pass(program);
    final var identifiersResolved = HirLexemeToIdentifierTransformerPass.pass(indexedPaths);
    final var simplified = HirSimplifyTransformerPass.pass(identifiersResolved);

    return simplified;
  }

  private Hir.Expression implicit_return(Hir.Expression expression, final boolean program) {

    if (expression instanceof final Hir.Block block) {
      expression = block.children();
    }

    if (expression instanceof final Hir.Expressions exprs) {

      final var children = exprs.children();
      if (children.length > 1) {

        // TODO: This is bad since it will add a "return" even if all paths inside this are terminal
        //        We would need a visitor pattern to visit the last expression of every node, and see if it is terminal
        //        Only then should we add this implicit return...
        final var last = children[children.length - 1];
        if (!(last instanceof Hir.Return)) {

          final var implicitReturn = new Hir.Return(last);
          children[children.length - 1] = implicitReturn;

          return exprs;
        } else {
          return expression;
        }
      } else if (children.length > 0) {
        expression = children[0]; // There is only one (1) child.
      } else {

        // TODO: This should probably just give VOID. Implement a way to return void as a literal.
        throw new IllegalArgumentException("There was no expression");
      }
    }

    if (program && expression instanceof final Hir.Assignment ass && ass.rhs() instanceof Hir.Function) {
      // Do not make the function declaration into a return.
      return expression;
    } else if (expression instanceof Hir.Return) {
      return expression;
    } else {
      return new Hir.Return(expression);
    }
  }

  private Hir.Expression[] lower_expressions(final Ast.Expression[] expressions) {

    final var lowered = new Hir.Expression[expressions.length];
    var targetIndex = 0;
    for (final var expression : expressions) {
      final var hir = raise(expression);

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

  public Hir.Expression raise(final Ast.Expression expr) {

    return switch (expr) {
      case final Ast.LoopFor ast -> lower_loop_for(ast);
      case final Ast.Conditional ast -> lower_conditional(ast);
      case final Ast.BinaryOperation ast -> lower_binary_operation(ast);
      case final Ast.Literal ast -> lower_literal(ast);
      case final Ast.Return ast -> lower_return(ast);
      case final Ast.Block ast -> lower_block(ast);
      case final Ast.PostfixExpression ast -> lower_postfix(ast);
      case final Ast.Juxtaposition ast -> raise_juxta(ast);
      case final Ast.Partial ast -> lower_partial(ast);
      case final Ast.Lexeme ast -> lower_lexeme(ast);
      case final Ast.Paren ast -> lower_paren(ast);
      case final Ast.Bracket ast -> lower_bracket(ast);
      case final Ast.Then ast -> raise(ast.expression());
      case final Ast.VariableDeclaration ast -> lower_variable_declaration(ast);
      case final Ast.Assignment ast -> lower_assignment(ast);
      case final Ast.Labeling ast -> lower_labeling(ast);
      case final Ast.Callable ast -> lower_callable(ast);
      case final Ast.Expressions ast -> new Hir.Expressions(lower_expressions(ast.children()));
      case final Ast.Struct ast -> lower_struct(ast);
      case final Ast.New ast -> lower_new(ast);
      case final Ast.DotAccess ast -> lower_dot_access(ast);
      case Ast.Comma _ -> throw new IllegalArgumentException("Unexpected comma outside a parenthesized list");
      case Ast.Comment _ -> null;
      // TODO: Important that a NoOp means "nothing" if last expression of block.
      //        Since everything is an expression, if "x" is last expression, then give back "x"
      //        But if it's "x;" then it means we should return "nothing".
      case Ast.NoOp _ -> null;

      default -> throw new IllegalArgumentException("Unknown AST Expression (" + expr.getClass().getSimpleName() + ") '" + expr + "'");
    };
  }

  private Hir.Expression raise_juxta(Ast.Juxtaposition ast) {

    final var target = raise(ast.target());
    final var arguments = new Hir.Argument[ast.arguments().length];
    for (var i = 0; i < arguments.length; i++) {
      arguments[i] = raiseCallArgument(ast.arguments()[i]);
    }

    // Keep the same path representation as explicit member calls.
    if (target instanceof final Hir.Path path) {
      final var elements = path.elements();
      final var last = elements.length - 1;
      elements[last] = new Hir.Call(elements[last], arguments);
      return path;
    }

    return new Hir.Call(target, arguments);
  }

  private Hir.Argument raiseCallArgument(final Ast.Expression argument) {
    if (argument instanceof Ast.Spread spread) {
      return new Hir.Argument(null, new Hir.Spread(raise(spread.expression())));
    }
    if (argument instanceof Ast.Labeling) {
      throw new IllegalArgumentException("Named call arguments require '=' instead of ':'");
    }
    if (argument instanceof Ast.Assignment assignment) {
      if (assignment.rhs() instanceof Ast.Spread) {
        throw new IllegalArgumentException("A spread call argument cannot have a label");
      }
      return new Hir.Argument(asLexeme(assignment.lhs()), raise(assignment.rhs()));
    }
    final var value = raise(argument);
    return value == null ? null : new Hir.Argument(null, value);
  }

  private Hir.Expression lower_dot_access(final Ast.DotAccess ast) {

    final var elements = new ArrayList<Hir.Expression>();
    ast.visit(new AstVisitor<Void>() {
      @Override
      public Void aggregate(final Void a, final Void b) {
        return null;
      }

      @Override
      public Void noValue() {
        return null;
      }

      @Override
      public Void visit(final Ast.Expression expression) {
        if (expression instanceof Ast.DotAccess) {
          return expression.visit(this);
        }
        elements.add(raise(expression));
        return null;
      }
    });

    return new Hir.Path(elements.toArray(new Hir.Expression[0]), null, null);
  }

  private Hir.Expression lower_new(final Ast.New ast) {

    final var target = raise(ast.target());
    final var allocatorLexeme = lower_lexeme(ast.allocator());
    final var argumentExpr = raise(ast.arguments());

    final var allocatorIdentifier = new Hir.Identifier(allocatorLexeme, null);

    return switch (argumentExpr) {
      // This is a creation using `new Obj { Val = '1' }` syntax. Which all structs inherently can do.
      case final Hir.Block block -> {
        final var assignments = new ArrayList<Hir.Assignment>();
        for (final var expr : expand(block.children())) {
          switch (expr) {
            case final Hir.Assignment assignment -> assignments.add(assignment);
            default -> throw new UnexpectedExpressionException(expr);
          }
        }


        yield new Hir.NewByBlock(target, allocatorIdentifier, assignments.toArray(new Hir.Assignment[0]), null, null);
      }

      // This is a creation using `new Obj('1')` syntax, meaning it is trying to call a manually added constructor.
      case final Hir.Expressions exprs -> new Hir.NewByCtor(target, allocatorIdentifier, exprs, null, null);
      default -> throw new UnexpectedExpressionException(argumentExpr);
    };
  }

  private Hir.Expression lower_struct(final Ast.Struct ast) {

    final var declarations = new ArrayList<Hir.Dec>();

    if (ast.block() != null && ast.block().expression() != null) {
      final var block = raise(ast.block().expression());
      for (final var field : expand(block)) {

        switch (field) {
          case final Hir.Dec dec -> declarations.add(dec);
          default -> throw new UnexpectedExpressionException(field);
        }
      }
    }

    return new Hir.Struct(declarations.toArray(new Hir.Dec[0]), null);
  }

  private Hir.Expression[] expand(final Hir.Expression expr) {
    return switch (expr) {
      case final Hir.Expressions exprs -> exprs.children();
      default -> new Hir.Expression[]{expr};
    };
  }

  private Hir.Expression lower_bracket(final Ast.Bracket ast) {

    final var elements = new ArrayList<Hir.Expression>();
    var section = 0;

    final var sections = new Hir.Expression[3];

    for (final var entry : ast.children()) {

      if (entry instanceof Ast.NoOp) {
        section++;
        continue;
      }

      final var lowered = raise(entry);

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
      return new Hir.Array(elementArray, tyExpr, arrayLengthExpr, null, null);

    } else if (section == 1) {

      final var tyExpr = new Hir.TyExpr(Ty.INFER); // new Hir.TyExpr((elementArray.length > 0) ? getTy(elementArray[0]) : Ty.INFER);
      final var size = sections[1];
      return new Hir.Array(elementArray, tyExpr, size, null, null);

    } else if (section == 2) {

      final var tyExpr = sections[1];
      // TODO: Ty here should be "inferred" until THIR kicks in (?) Or is info lost here?
      //final var exprArrayElementTy = getTyFromType(tyExpr);
      final var sizeExpr = sections[2];
      return new Hir.Array(elementArray, tyExpr, sizeExpr, null, null);

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

  private Hir.Expression lower_labeling(final Ast.Labeling ast) {

    if (ast.lhs() instanceof final Ast.Paren lparen && ast.rhs() instanceof final Ast.Lexeme rid) {

      // If this is true, then it is a callable function signature.
      // NOTE: This might not always be true, but we'll go for it for now.
      // TODO: The rhs must be more permissive than just "identifier", and lhs should be more restrictive (needs to be tuple)
      return find_and_lower_parameters(ast);
    }

    final var lhs = raise(ast.lhs());
    final var rhs = raise(ast.rhs());

    return new Hir.Labeling(lhs, rhs, null);
  }

  private Hir.Expression lower_callable(final Ast.Callable ast) {

    final var signature = find_and_lower_parameters(ast.lhs());
    final var body = implicit_return(raise(ast.rhs()), false);

    return new Hir.Function(signature, body);
  }

  private Hir.FunctionSignature find_and_lower_parameters(final Ast.Expression ast) {

    return switch (ast) {
      case final Ast.Labeling labeling -> {

        final var signature = find_and_lower_parameters(labeling.lhs());
        final var returnTypeExpr = raise(labeling.rhs());

        yield new Hir.FunctionSignature(signature.parameters(), signature.vararg(), returnTypeExpr, null);
      }
      case final Ast.Paren paren -> {

        final var contents = paren_contents(paren);
        final var signature = (contents == null) ? null : find_and_lower_parameters_inner(contents);
        final var parameters = (signature == null) ? new Hir.Parameter[0] : signature.parameters();
        final var vararg = signature != null && signature.vararg();

        yield new Hir.FunctionSignature(parameters, vararg, new Hir.TyExpr(Ty.INFER), null);
      }
      default -> find_and_lower_parameters_inner(ast);
    };
  }

  private Hir.FunctionSignature find_and_lower_parameters_inner(final Ast.Expression ast) {

    final var parameters = new ArrayList<Hir.Parameter>();
    final var isVarArg = new AtomicBoolean(false);

    switch (ast) {
      case final Ast.Expressions it -> {
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

  private Hir.Parameter lower_parameter(final Ast.Expression expr, final AtomicBoolean restVararg) {

    return switch (expr) {
      case final Ast.Spread spread -> new Hir.Parameter(asLexeme(spread.expression()), new Hir.TyExpr(Ty.INFER), true, null);
      case Ast.Rest _ -> {
        restVararg.set(true);
        yield null;
        //new Hir.Parameter(new Hir.Identifier("..."), null, true);
      }
      case final Ast.Labeling labeling -> {
        final var labelingLhs = lower_parameter(labeling.lhs(), restVararg);
        final var labelingRhs = raise(labeling.rhs());
        yield new Hir.Parameter(labelingLhs.lexeme(), labelingRhs, labelingLhs.vararg(), null);
      }
      default -> new Hir.Parameter(asLexeme(expr), new Hir.TyExpr(Ty.INFER), false, null);
    };
  }

  private Hir.Lexeme asLexeme(final Ast.Expression ast) {
    return switch (ast) {
      case final Ast.Lexeme it -> lower_lexeme(it);
      default -> throw new UnexpectedExpressionException(ast);
    };
  }

  private Hir.Dec lower_variable_declaration(final Ast.VariableDeclaration ast) {

    return new Hir.Dec(
      lower_lexeme(ast.lexeme()),
      switch (ast.mutabilityKind()) {
        case Immutable -> Hir.MutabilityKind.IMMUTABLE;
        case Mutable -> Hir.MutabilityKind.MUTABLE;
      },
      ast.type() == null ? new Hir.TyExpr(Ty.INFER) : raise(ast.type())
    );
  }

  private Hir.Expression lower_assignment(final Ast.Assignment ast) {

    final var target = switch (ast.lhs()) {
      case final Ast.VariableDeclaration lhs -> raise(lhs);
      case final Ast.Lexeme lhs -> raise(lhs);
      case final Ast.DotAccess lhs -> raise(lhs);
      case final Ast.PostfixExpression lhs -> raise(lhs);
      default -> throw new UnexpectedExpressionException(ast.lhs());
    };

    final var source = raise(ast.rhs());

    return new Hir.Assignment(target, source);
  }

  private Hir.Lexeme lower_lexeme(final Ast.Lexeme ast) {
    return new Hir.Lexeme(ast.name());
  }

  private Hir.Expression lower_postfix(final Ast.PostfixExpression ast) {

    if (ast.suffix() instanceof final Ast.Bracket bracket) {

      // Array access. Might perhaps be other things as well. Will need to add some abstraction in that case.
      final var target = raise(ast.target());
      final var astAccessors = bracket.children();
      final var hirAccessors = new Hir.Expression[astAccessors.length];
      for (var i = 0; i < astAccessors.length; i++) {
        hirAccessors[i] = raise(astAccessors[i]);
      }

      if (hirAccessors.length == 0) {
        throw new IllegalArgumentException("Missing array access index");
      } else if (hirAccessors.length == 1) {
        return new Hir.ArrayAccess(target, hirAccessors[0], null, null);
      } else {
        return new Hir.ArrayAccess(target, new Hir.Expressions(hirAccessors), null, null);
      }

    } else if (ast.suffix() instanceof final Ast.Paren paren) {

      // A function call. Might perhaps be other things as well. Will need to add some abstraction in that case.
      final var target = raise(ast.target());
      final var contents = paren_contents(paren);
      final var astArguments = switch (contents) {
        case final Ast.Expressions expressions -> expressions.children();
        case null -> new Ast.Expression[0];
        default -> new Ast.Expression[]{contents};
      };
      final var arguments = new ArrayList<Hir.Argument>();
      for (final var argument : astArguments) {
        final var entry = raiseCallArgument(argument);
        if (entry != null) {
          arguments.add(entry);
        }
      }

      return new Hir.Call(target, arguments.toArray(new Hir.Argument[0]), false, null, null);
    }

    throw new IllegalArgumentException("Unknown postfix expression");
  }

  private <E extends Ast.Expression> Hir.Expression lower_partial(final Ast.Partial<E> ast) {

    final var child = raise(ast.expression());

    if (child instanceof final Hir.Call call) {
      return call.partial(true);
    }

    if (child instanceof final Hir.Path path
      && path.elements().length > 0
      && path.elements()[path.elements().length - 1] instanceof final Hir.Call call) {
      call.partial(true);
      return path;
    }

    throw new IllegalArgumentException("Do not know how to make a %s partial".formatted(child));
  }

  private Hir.Expression lower_block(final Ast.Block ast) {

    final var lowered = raise(ast.expression());
    return new Hir.Block(lowered, null);
  }

  private Hir.Return lower_return(final Ast.Return ast) {
    return new Hir.Return(raise(ast.expression()));
  }

  private Hir.Literal lower_literal(final Ast.Literal ast) {
    return new Hir.Literal(ast.content(), ast.ty());
  }

  private Hir.Expression lower_binary_operation(final Ast.BinaryOperation ast) {

    final Ast.BinaryOperationKind expandedKind = switch (ast.kind()) {
      case ADDITION_ASSIGNMENT -> Ast.BinaryOperationKind.ADD;
      case SUBTRACTION_ASSIGNMENT -> Ast.BinaryOperationKind.SUBTRACT;
      case MULTIPLY_ASSIGNMENT -> Ast.BinaryOperationKind.MULTIPLY;
      case DIVIDE_ASSIGNMENT -> Ast.BinaryOperationKind.DIVIDE;
      default -> null;
    };

    if (expandedKind != null) {
      return new Hir.CompoundAssignment(raise(ast.lhs()), lower_binary_operation_type(expandedKind), raise(ast.rhs()));
    }

    return lower_binary_operation_explicit(ast);
  }

  private Hir.BinaryOperation lower_binary_operation_explicit(final Ast.BinaryOperation ast) {

    return new Hir.BinaryOperation(
      raise(ast.lhs()),
      lower_binary_operation_type(ast.kind()),
      raise(ast.rhs()),
      null, null
    );
  }

  private Hir.BinaryOperationKind lower_binary_operation_type(final Ast.BinaryOperationKind type) {
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

  private Hir.Expression lower_loop_for(final Ast.LoopFor astLoopFor) {

    final var loweredHead = raise(astLoopFor.head());
    final var loweredBody = raise(astLoopFor.block());

    final Hir.Expression[] loopFields;
    final Hir.Expression loopPredicate;
    final Hir.Expression loopAction;

    switch (loweredHead) {
      case final Hir.Expressions head -> {
        if (head.children().length == 3) {

          final var first = head.children()[0];
          switch (first) {
            case final Hir.Assignment hir -> loopFields = new Hir.Expression[]{hir};
            case final Hir.Dec hir -> loopFields = new Hir.Expression[]{hir};
            default -> throw new NotImplementedException("Unknown first for-loop part '" + first + "'");
          }

          final var second = head.children()[1];
          switch (second) {
            case final Hir.BinaryOperation hir -> {
              if (hir.kind().isPredicate()) {
                loopPredicate = hir;
              } else {
                throw new IllegalArgumentException("The second for-loop part must be a predicate binary op, not '" + hir + "'");
              }
            }
            case final Hir.Call hir -> loopPredicate = hir;
            case final Hir.Identifier hir -> loopPredicate = hir;
            default -> throw new IllegalArgumentException("The second for-loop part cannot be a '" + second + "'");
          }

          final var third = head.children()[2];
          switch (third) {
            case final Hir.Assignment hir -> loopAction = hir;
            case final Hir.CompoundAssignment hir -> loopAction = hir;
            default -> throw new IllegalArgumentException("The second for-loop part cannot be a '" + third + "'");
          }

        } else {
          throw new IllegalArgumentException("A for-loop is a three-part expression list, not '" + loweredHead + "'");
        }
      }
      default -> throw new NotImplementedException("Unknown head expression '" + loweredHead + "'");
    }

    final var loopExpressions = new Hir.Expression[loopFields.length + 1];
    System.arraycopy(loopFields, 0, loopExpressions, 0, loopFields.length);

    loopExpressions[loopExpressions.length - 1] = new Hir.Loop(

      // Q: Is it better if this was flipped and do nothing on fail but break on pass? Less branching???
      new Hir.Conditional(
        loopPredicate,
        new Hir.Expressions(new Hir.Expression[]{loweredBody, loopAction}, null, true),
        // TODO: Add support for adding value to the break
        new Hir.LoopBreak(null),
        null, null
      )
    );

    return new Hir.Expressions(loopExpressions, null, true);
  }

  /// Remove only this group's separators; nested groups resolve their own commas when raised.
  private Ast.Expression paren_contents(final Ast.Paren paren) {
    return switch (paren.expression()) {
      case Ast.Expressions expressions -> Ast.Expressions.from(
        Arrays.stream(expressions.children()).filter(child -> !(child instanceof Ast.Comma)).toList()
      );
      case Ast.Comma _ -> null;
      case null -> null;
      default -> paren.expression();
    };
  }

  private Hir.Expression lower_paren(final Ast.Paren astParen) {

    if (astParen.expression() instanceof final Ast.Expressions expressions
      && Arrays.stream(expressions.children()).anyMatch(child -> child instanceof Ast.Comma)) {
      final var entries = new ArrayList<Hir.TupleEntry>();
      for (final var child : expressions.children()) {
        if (child instanceof Ast.Comma) {
          continue;
        }
        if (child instanceof final Ast.Labeling labeling) {
          entries.add(lower_labeling_to_tuple_entry(labeling));
        } else if (child instanceof Ast.Assignment assignment && assignment.lhs() instanceof Ast.Lexeme label) {
          entries.add(new Hir.TupleEntry(lower_lexeme(label), raise(assignment.rhs()), false));
        } else {
          final var value = raise(child);
          if (value != null) {
            entries.add(new Hir.TupleEntry(null, value));
          }
        }
      }
      return new Hir.Tuple(entries.toArray(new Hir.TupleEntry[0]), null);
    }

    final var astExpr = paren_contents(astParen);
    if (astExpr == null) {
      return new Hir.Expressions(new Hir.Expression[0]);
    }

    return switch (astExpr) {
      case final Ast.Expressions collection -> lower_paren_expression_collection(collection);
      case final Ast.Labeling labeling -> lower_labeling_to_tuple_entry(labeling);
      default -> raise(astExpr);
    };
  }

  private Hir.Expression lower_paren_expression_collection(final Ast.Expressions astExpressions) {

    final var children = astExpressions.children();
    var children_lowered = new Hir.Expression[children.length];

    var targetIndex = 0;
    for (final var expression : children) {

      final var child = switch (expression) {
        case final Ast.Labeling labeling -> lower_labeling_to_tuple_entry(labeling);
        default -> raise(expression);
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

    return new Hir.Expressions(children_lowered);
  }

  private Hir.TupleEntry lower_labeling_to_tuple_entry(final Ast.Labeling astLabeling) {

    return new Hir.TupleEntry(
      asLexeme(astLabeling.lhs()),
      raise(astLabeling.rhs())
    );
  }

  public Hir.Conditional lower_conditional(final Ast.Conditional astConditional) {

    final var fail = (astConditional.fail() == null)
      ? null
      : raise(astConditional.fail());

    return new Hir.Conditional(
      raise(astConditional.predicate()),
      raise(astConditional.pass()),
      fail,
      null, null
    );
  }
}
