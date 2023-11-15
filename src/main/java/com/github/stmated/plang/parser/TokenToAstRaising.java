package com.github.stmated.plang.parser;

import com.github.stmated.plang.ast.model.AstAssignment;
import com.github.stmated.plang.ast.model.AstBecome;
import com.github.stmated.plang.ast.model.AstBinaryOperation;
import com.github.stmated.plang.ast.model.AstBinaryOperationKind;
import com.github.stmated.plang.ast.model.AstBlock;
import com.github.stmated.plang.ast.model.AstBracket;
import com.github.stmated.plang.ast.model.AstCall;
import com.github.stmated.plang.ast.model.AstCallable;
import com.github.stmated.plang.ast.model.AstComment;
import com.github.stmated.plang.ast.model.AstCompTime;
import com.github.stmated.plang.ast.model.AstConditional;
import com.github.stmated.plang.ast.model.AstDotAccess;
import com.github.stmated.plang.ast.model.AstExport;
import com.github.stmated.plang.ast.model.AstExpression;
import com.github.stmated.plang.ast.model.AstExpressions;
import com.github.stmated.plang.ast.model.AstIdentifier;
import com.github.stmated.plang.ast.model.AstImpl;
import com.github.stmated.plang.ast.model.AstImport;
import com.github.stmated.plang.ast.model.AstImportCapable;
import com.github.stmated.plang.ast.model.AstImportPath;
import com.github.stmated.plang.ast.model.AstImportPathAlias;
import com.github.stmated.plang.ast.model.AstImportPathGroup;
import com.github.stmated.plang.ast.model.AstImportPathIdentifier;
import com.github.stmated.plang.ast.model.AstImportPathWildcard;
import com.github.stmated.plang.ast.model.AstIn;
import com.github.stmated.plang.ast.model.AstInfer;
import com.github.stmated.plang.ast.model.AstLabeling;
import com.github.stmated.plang.ast.model.AstLiteral;
import com.github.stmated.plang.ast.model.AstLoopDoWhile;
import com.github.stmated.plang.ast.model.AstLoopFor;
import com.github.stmated.plang.ast.model.AstLoopWhile;
import com.github.stmated.plang.ast.model.AstMatch;
import com.github.stmated.plang.ast.model.AstMutabilityKind;
import com.github.stmated.plang.ast.model.AstNegate;
import com.github.stmated.plang.ast.model.AstNew;
import com.github.stmated.plang.ast.model.AstNoOp;
import com.github.stmated.plang.ast.model.AstNot;
import com.github.stmated.plang.ast.model.AstParen;
import com.github.stmated.plang.ast.model.AstProgram;
import com.github.stmated.plang.ast.model.AstRange;
import com.github.stmated.plang.ast.model.AstReturn;
import com.github.stmated.plang.ast.model.AstSpread;
import com.github.stmated.plang.ast.model.AstStaticAccess;
import com.github.stmated.plang.ast.model.AstStruct;
import com.github.stmated.plang.ast.model.AstThen;
import com.github.stmated.plang.ast.model.AstTrait;
import com.github.stmated.plang.ast.model.AstTypePlaceholder;
import com.github.stmated.plang.ast.model.AstVariableDeclaration;
import com.github.stmated.plang.ast.model.AstVariableSink;
import com.github.stmated.plang.ast.model.AstWhere;
import com.github.stmated.plang.ast.model.AstWith;
import com.github.stmated.plang.ast.model.AstYield;
import com.github.stmated.plang.lexer.Token;
import com.github.stmated.plang.lexer.TokenType;
import com.github.stmated.plang.ty.Ty;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class TokenToAstRaising {

  private final Iterator<Token> iterator;

  private Token current;

  private final Deque<Token> queuedTokens = new ArrayDeque<>();

  public TokenToAstRaising(Iterator<Token> iterator) {
    this.iterator = iterator;
  }

  public AstProgram parse() {

    final var children = new ArrayList<AstExpression>();

    try {
      while (hasNext()) {
        children.add(parseLevel0());
      }
    } catch (Exception ex) {

      final var expressionStrings = String.join("\n", children.stream().map(Object::toString).toList());
      throw new IllegalArgumentException("Exception '%s' after parsed:\n%s".formatted(ex.getMessage(), expressionStrings), ex);
    }

    return new AstProgram(new AstExpressions(children.toArray(new AstExpression[0])));
  }

  /**
   * ASSIGN
   */
  private AstExpression parseLevel0() {

    final var lhs = parseLevel1();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ASSIGN) {
        final var rhs = parseLevel0();
        if (rhs != null) {
          return new AstAssignment(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for assignment");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * ARROW_DOUBLE
   */
  private AstExpression parseLevel1() {

    final var lhs = parseLevel2();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ARROW_DOUBLE) {
        final var rhs = parseLevel2();
        if (rhs != null) {
          return new AstCallable(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for Double Arrow");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * WHERE
   */
  private AstExpression parseLevel2_rhs_level0() {

    final var lhs = parseLevel2_rhs_level1();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.WHERE) {
        final var rhs = parseLevel2_rhs_level0();
        return new AstWhere(lhs, rhs);
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * ASSIGN
   */
  private AstExpression parseLevel2_rhs_level1() {

    final var lhs = parseLevel2_rhs_level2();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ASSIGN) {
        final var rhs = parseLevel2_rhs_level1();
        if (rhs != null) {
          return new AstAssignment(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for assignment");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * COLON
   */
  private AstExpression parseLevel2_rhs_level2() {

    final var lhs = parseLevel6();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.COLON) {
        final var rhs = parseLevel6();
        if (rhs != null) {
          return new AstLabeling(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for labeling");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * WHERE
   */
  private AstExpression parseLevel2() {

    final var lhs = parseLevel3();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.WHERE) {
        final var rhs = parseLevel2_rhs_level0(); // RHS goes back up to higher level
        return new AstWhere(lhs, rhs);
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * COLON
   */
  private AstExpression parseLevel3() {

    final var lhs = parseLevel4();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.COLON) {
        final var rhs = parseLevel3();
        if (rhs != null) {
          return new AstLabeling(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for labeling");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * AND | OR
   */
  private AstExpression parseLevel4() {

    final var lhs = parseLevel5();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.AND || t == TokenType.OR) {
        final var rhs = parseLevel4();
        if (rhs != null) {
          return new AstBinaryOperation(lhs, AstBinaryOperationKind.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for binary boolean logical operator");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * EQUALS | LTE | GTE | LT | GT | IS
   */
  private AstExpression parseLevel5() {

    final var lhs = parseLevel6();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.EQUALS
          || t == TokenType.LTE || t == TokenType.GTE || t == TokenType.LT || t == TokenType.GT
          || t == TokenType.IS
          || t == TokenType.ADDITION_ASSIGNMENT || t == TokenType.SUBTRACTION_ASSIGNMENT || t == TokenType.MULTIPLY_ASSIGNMENT
          || t == TokenType.DIVIDE_ASSIGNMENT) {
        final var rhs = parseLevel6();
        if (rhs != null) {
          return new AstBinaryOperation(lhs, AstBinaryOperationKind.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for binary boolean comparison operator");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * PLUS | MINUS
   */
  private AstExpression parseLevel6() {

    final var lhs = parseLevel7();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ADD || t == TokenType.SUBTRACT) {
        final var rhs = parseLevel6(); // Recursive to same level
        if (rhs != null) {
          return new AstBinaryOperation(lhs, AstBinaryOperationKind.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for binary boolean logical operator");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * MULTIPLY | DIVIDE
   */
  private AstExpression parseLevel7() {

    final var lhs = parseLevel8();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.MULTIPLY || t == TokenType.DIVIDE) {
        final var rhs = parseLevel8();
        if (rhs != null) {
          return new AstBinaryOperation(lhs, AstBinaryOperationKind.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for binary boolean logical operator");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * Pow | Modulus | Remainder | BitShiftLeft | BitShiftRight | BitAnd | BitOr
   */
  private AstExpression parseLevel8() {

    final var lhs = parseLevel9();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.POW
          || t == TokenType.MODULUS
          || t == TokenType.REMAINDER
          || t == TokenType.BIT_SHIFT_LEFT
          || t == TokenType.BIT_SHIFT_RIGHT
          || t == TokenType.BIT_AND
          || t == TokenType.BIT_OR) {
        final var rhs = parseLevel8(); // Recursive
        if (rhs != null) {
          return new AstBinaryOperation(lhs, AstBinaryOperationKind.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for math operator");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  private AstExpression parseLevel9() {

    final var lhs = parseLevel10();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOUBLE_DOT) {
        final var rhs = parseLevel10();
        if (rhs != null) {
          return new AstRange(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for range expression");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * IN
   */
  private AstExpression parseLevel10() {

    final var lhs = parseLevel11();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.IN) {
        final var rhs = parseLevel11();
        if (rhs != null) {
          return new AstIn(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for range expression");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  private AstExpression parseLevel11() {

    final var lhs = parseLevel12();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.COLON_DOUBLE) {
        final var rhs = parseLevel11(); // Recursive
        if (rhs != null) {
          return new AstStaticAccess(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for static access");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  private AstExpression parseLevel12() {

    final var lhs = parseExpression();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOT) {
        final var rhs = parseLevel12(); // Recursive
        if (rhs != null) {
          return new AstDotAccess(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for dot access");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  private AstExpression parseExpression() {

    final var token = next();
    if (token == null) {
      return null;
    }

    final var callableAst = switch (token.type()) {
      case LITERAL_INTEGER -> new AstLiteral(token.content(), Ty.INTEGER);
      // TODO: Need to add all the other precision number types, like double, float, etc. Especially with variable width...
      //        Right now the default decimal number is FLOAT, to make things easier in LLVM. But DECIMAL should be DECIMAL ^^;
      case LITERAL_DECIMAL -> new AstLiteral(token.content(), Ty.DECIMAL);
      case LITERAL_FLOAT -> new AstLiteral(token.content(), Ty.FLOAT);
      case LITERAL_DOUBLE -> new AstLiteral(token.content(), Ty.DOUBLE);
      case LITERAL_BOOLEAN -> new AstLiteral(token.content(), Ty.BOOLEAN);
      case LITERAL_STRING -> new AstLiteral(token.content().substring(1, token.content().length() - 1), Ty.STRING);
      case LITERAL_STRING_TEMPLATE -> new AstLiteral(token.content(), Ty.STRING); // TODO: Where and how to parse this?
      case LITERAL_INTEGER_BINARY -> new AstLiteral(token.content(), Ty.INTEGER_BINARY);
      case LITERAL_INTEGER_HEX -> new AstLiteral(token.content(), Ty.INTEGER_HEX);
      case LITERAL_INTEGER_OCTAL -> new AstLiteral(token.content(), Ty.INTEGER_OCTAL);
      case LITERAL_INTEGER_LONG -> new AstLiteral(token.content(), Ty.LONG);
      case UNDERSCORE -> new AstVariableSink();
      case OPEN_BRACE -> parseBlock();
      case OPEN_PAREN -> parseParen();
      case OPEN_BRACKET -> parseBracket();
      case REF -> parseRef();
      case IF -> parseIf();
      case IDENTIFIER -> parseIdentifier();
      case FOR -> parseFor();
      case DO -> parseDo();
      case WHILE -> parseWhile();
      default -> null;
    };

    if (callableAst == null) {
      return switch (token.type()) {
        case ADD -> parsePotentialDeclaredPositiveLiteralNumber();
        case SUBTRACT -> parsePotentialDeclaredNegativeLiteralNumber();
        case BANG -> new AstNot(parseExpression());
        case MATCH -> parseMatch();
        case WITH -> parseWith();
        case TRIPLE_DOT -> parseSpread();
        case DOLLAR -> parseDollar();
        case INFER -> parseInfer();
        case VAL, VAR -> parseVarVal(token);
        case STRUCT -> parseStruct();
        case TRAIT -> parseTrait();
        case IMPL -> parseImpl();
        case THEN -> parseThen();
        case RETURN -> parseReturn();
        case NEW -> parseNew();
        case SEMI_COLON, END -> new AstNoOp();
        case EXPORT -> parseExport();
        case IMPORT -> parseImport();
        case BECOME -> parseBecome();
        case META -> parseCompTime();
        case COMMENT_SINGLE_LINE, COMMENT_MULTI_LINE -> new AstComment(token.content());
        case YIELD -> parseYield();
        default -> throw new IllegalArgumentException("Unknown token '%s'".formatted(token));
      };
    } else {
      return parsePotentialFnCall(callableAst);
    }
  }

  private AstExpression parseSpread() {
    return new AstSpread(parseExpression());
  }

  /**
   * `+(something)` is handled same as `(something)`.
   * <p>
   * If operator overloading is ever a thing, then a AstUnaryPlus might be needed.
   */
  private AstExpression parsePotentialDeclaredPositiveLiteralNumber() {
    final var potentialNumber = this.parseExpression();
    if (potentialNumber != null) {
      return potentialNumber;
    } else {
      throw new IllegalStateException("EOS");
    }
  }

  private AstExpression parsePotentialDeclaredNegativeLiteralNumber() {

    final var potentialNumber = this.parseExpression();
    if (potentialNumber != null) {

      if (potentialNumber instanceof AstLiteral literal && literal.ty().isNumber()) {
        return new AstLiteral(STR."-\{literal.content()}", literal.ty());
      } else {
        return new AstNegate(potentialNumber);
      }

    } else {
      throw new IllegalStateException("EOS");
    }
  }

  private AstExpression parseVarVal(final Token token) {

    final var identifier = this.parseIdentifier();
    final var mutabilityKind = (token.type() == TokenType.VAR) ? AstMutabilityKind.Mutable : AstMutabilityKind.Immutable;
    // TODO: If possible generalize this into an "InitialLabel"?

    AstExpression type;
    final var potentialColon = next();
    if (potentialColon != null && potentialColon.type() == TokenType.COLON) {
      next(); // TODO: Wrong? Or is this what we should do always? next() before?
      type = this.parseIdentifierLike();
    } else {
      queuedTokens.add(potentialColon);
      type = null;
    }

    final var declaration = new AstVariableDeclaration(
      identifier,
      mutabilityKind,
      type,
      false
    );

    final var potentialAssign = next();
    if (potentialAssign != null && potentialAssign.type() == TokenType.ASSIGN) {

      final var rhs = this.parseLevel0();
      return new AstAssignment(declaration, rhs);

    } else {
      queuedTokens.add(potentialAssign);
    }

    return declaration;
  }

  private boolean hasNext() {

    if (!queuedTokens.isEmpty()) {
      return true;
    }

    return iterator.hasNext();
  }

  private Token next() {

    if (!queuedTokens.isEmpty()) {
      return (current = queuedTokens.pop());
    }

    if (!iterator.hasNext()) {
      return null;
    }

    current = iterator.next();
    return current;
  }

  private AstExpression parseIdentifierLike() {

    final var identifier = this.parseIdentifier();
    return parsePotentialFnCall(identifier);
  }

  private AstExpression parsePotentialFnCall(AstExpression expr) {

    var n = next();
    if (n != null) {

      boolean partial = false;
      if (n.type() == TokenType.TILDE) {
        partial = true;
        n = next();
      }

      if (n != null && n.type() == TokenType.OPEN_PAREN) {

        final var paren = this.parseParen();

        boolean bubbleUp = false;
        final var t2 = next();
        if (t2 != null) {
          if (t2.type() == TokenType.QUESTION_MARK) {
            bubbleUp = true;
          } else {
            queuedTokens.add(t2);
          }
        }

        return new AstCall(expr, paren, bubbleUp, partial);

      } else if (partial) {
        throw new IllegalArgumentException("Tilde (partial call indicator) must be followed by an opening parenthesis");
      } else {
        queuedTokens.add(n);
      }
    }

    return expr;
  }

  private <T extends AstExpression> List<T> parseExpressionCollection(
    TokenType[] endedBy,
    Supplier<T> next
  ) {

    if (matchesOneOf(current, endedBy)) {

      // We are already on a closing token. It is an empty paren or bracket or similar.
      return new ArrayList<>();
    }

    final var expressions = new ArrayList<T>();

    try {
      Token token;
      while ((token = next()) != null) {

        if (matchesOneOf(token, endedBy)) {
          break;
        }

        if (token.type() == TokenType.COMMA) {

          // Skip this one, it is a valid delimiter. Go to the next item.
          continue;
        }

        // Let's stay on our current token.
        // TODO: This pattern seems strange. We should set HARD rules on when to read next or not.
        queuedTokens.add(token);

        final var expression = next.get();
        if (expression != null) {
          expressions.add(expression);
        } else {
          throw new IllegalArgumentException("Encountered EOF before %s".formatted(token.type()));
        }
      }
    } catch (Exception ex) {

      final var expressionStrings = expressions.stream().map(Object::toString).toList();
      final var expressionsString = "\n    " + String.join("\n    ", expressionStrings);
      throw new IllegalArgumentException("Failed parsing after found collection-expressions %s".formatted(expressionsString), ex);
    }

    return expressions;
  }

  private static boolean matchesOneOf(final Token token, final TokenType[] endedBy) {

    for (final var end : endedBy) {
      if (end == token.type()) {
        return true;
      }
    }

    // We have found our stop. Let's exit.
    return false;
  }

  private AstIdentifier parseIdentifier() {

    final var token = stayOrNext(TokenType.IDENTIFIER);
    if (token == null) {
      return null;
    }

    return new AstIdentifier(token.content());
  }

  private Token stayOrNext(TokenType t) {
    return this.stayOrNext(t, t);
  }

  private Token stayOrNext(TokenType stayIf, TokenType expectNext) {

    if (current != null && current.type() == stayIf) {

      if (!queuedTokens.isEmpty() && queuedTokens.peek() == current) {
        queuedTokens.pop();
      }

      return current;
    }

    if (hasNext()) {
      final var token = next();
      if (token != null) {
        if (expectNext == null || token.type() == expectNext) {

          // All is well.
          return token;
        } else {
          throw new IllegalArgumentException("Next token '%s' is not a '%s'".formatted(token, expectNext));
        }
      } else {
        throw new IllegalArgumentException("There is no '%s' after '%s'".formatted(expectNext, current));
      }
    } else {
      throw new IllegalArgumentException("There is no '%s' after '%s'".formatted(expectNext, current));
    }
  }

  private AstBlock parseBlock() {

    stayOrNext(TokenType.OPEN_BRACE);

    // TODO: This is wrong -- it will consume our token!
    final var children = new ArrayList<AstExpression>();

    while (hasNext()) {

      final var token = next();
      if (token == null || token.type() == TokenType.CLOSE_BRACE) {
        break;
      }

      queuedTokens.add(token);
      children.add(this.parseLevel0());
    }

    return new AstBlock(children.toArray(new AstExpression[0]));
  }

  private AstStruct parseStruct() {

    final var block = this.parseBlock();
    return new AstStruct(block);
  }

  private AstTrait parseTrait() {

    final var block = this.parseBlock();
    return new AstTrait(block);
  }

  private AstMatch parseMatch() {

    this.stayOrNext(TokenType.MATCH);

    final var target = this.parseLevel0();

    this.stayOrNext(TokenType.OPEN_BRACE);

    final var collection = this.parseExpressionCollection(
      new TokenType[]{
        TokenType.CLOSE_BRACE,
        TokenType.CLOSE_BRACKET,
        TokenType.CLOSE_PAREN
      },
      this::parseLevel0
    );

    this.stayOrNext(TokenType.CLOSE_BRACE);

    return new AstMatch(target, new AstExpressions(collection.toArray(new AstExpression[0])));
  }

  private boolean ifNextAndBacktrack(TokenType tt) {

    final var t = next();
    if (t != null && t.type() == tt) {

      queuedTokens.push(t);
      return true;
    } else {
      queuedTokens.push(t);
      return false;
    }
  }

  private AstWith parseWith() {

    this.stayOrNext(TokenType.WITH);

    final AstExpression argument;
    if (ifNextAndBacktrack(TokenType.OPEN_PAREN)) {
      argument = parseParen();
    } else {
      argument = parseLevel0();
    }

    final var block = this.parseBlock();

    return new AstWith(argument, block);
  }

  private AstTypePlaceholder parseDollar() {

    this.stayOrNext(TokenType.DOLLAR);

    final var identifier = this.parseIdentifier();

    return new AstTypePlaceholder(identifier);
  }

  private AstInfer parseInfer() {

    this.stayOrNext(TokenType.INFER);

    final var identifier = this.parseExpression();

    return new AstInfer(identifier);
  }

  private AstParen parseParen() {

    this.stayOrNext(TokenType.OPEN_PAREN);

    // End on closing paren, but also on all other things since it probably means malformed code.
    final var collection = this.parseExpressionCollection(
      new TokenType[]{
        TokenType.CLOSE_PAREN,
        TokenType.CLOSE_BRACE,
        TokenType.CLOSE_BRACKET
      },
      this::parseLevel0
    );

    if (collection.size() == 1) {
      return new AstParen(collection.getFirst());
    } else if (collection.isEmpty()) {
      return new AstParen(null);
    } else {
      return new AstParen(new AstExpressions(collection.toArray(new AstExpression[0])));
    }
  }

  private AstBracket parseBracket() {

    this.stayOrNext(TokenType.OPEN_BRACKET);

    // End on closing bracket, but also on all other things since it probably means malformed code.
    final var collection = this.parseExpressionCollection(
      new TokenType[]{
        TokenType.CLOSE_BRACKET,
        TokenType.CLOSE_PAREN,
        TokenType.CLOSE_BRACE,
      },
      this::parseLevel0
    );

    return new AstBracket(collection.toArray(new AstExpression[0]));
  }

  private AstVariableDeclaration parseRef() {

    final var token = this.next();
    if (token != null) {

      if (token.type() == TokenType.VAL || token.type() == TokenType.VAR) {

        var varval = this.parseVarVal(current);

        if (varval instanceof AstAssignment ia) {
          varval = ia.lhs();
        }

        AstVariableDeclaration varDec;
        if (varval instanceof AstVariableDeclaration ivd) {
          varDec = ivd;
        } else {
          throw new RuntimeException("Not implemented");
        }

        return new AstVariableDeclaration(
          varDec.identifier(),
          varDec.mutabilityKind(),
          varDec.type(),
          true
        );

      } else if (token.type() == TokenType.IDENTIFIER) {

        final var identifier = this.parseIdentifier();

        return new AstVariableDeclaration(
          identifier,
          AstMutabilityKind.Immutable,
          null,
          true
        );
      }
    }

    throw new IllegalArgumentException("ref must be followed by var/val/identifier");
  }

  private AstExport parseExport() {

    stayOrNext(TokenType.EXPORT);

    var isDefault = false;
    final var maybeDefault = next();
    if (maybeDefault != null) {
      if (maybeDefault.type() == TokenType.DEFAULT) {
        isDefault = true;
      } else {
        queuedTokens.push(maybeDefault);
      }
    }

    final var exported = this.parseLevel0();
    if (exported == null) {
      throw new IllegalArgumentException("The export must export something");
    }

    return new AstExport(exported, isDefault);
  }

  private AstImportCapable parseImportPath_level0() {

    final var lhs = parseImportPath_level1();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOT) {
        final var rhs = parseImportPath_level0();
        return new AstImportPath(lhs, rhs);
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  private AstImportCapable parseImportPath_level1() {

    var t = next();
    if (t != null) {
      if (t.type() == TokenType.OPEN_BRACE) {

        final var paths = new ArrayList<AstImportCapable>();
        while ((t = next()) != null && t.type() != TokenType.CLOSE_BRACE) {

          if (t.type() != TokenType.COMMA) {

            // Simply skip commas, any incorrect import path member will throw exception.
            queuedTokens.push(t);

            final var alias = parseImportAliasOrBacktrack();
            if (alias != null) {
              paths.add(alias);
            } else {
              paths.add(parseImportPath_level0());
            }
          }
        }

        return new AstImportPathGroup(paths.toArray(new AstImportCapable[0]));

      } else {
        queuedTokens.push(t);
        return this.parseImportPath_level2();
      }
    }

    throw new IllegalArgumentException("Invalid import path, no token after import");
  }

  private AstImportCapable parseImportPath_level2() {

    var t = next();
    if (t != null) {
      if (t.type() == TokenType.MULTIPLY) {
        return new AstImportPathWildcard();
      } else if (t.type() == TokenType.IDENTIFIER) {

        final var id = parseIdentifier();
        return new AstImportPathIdentifier(id);
      }
    }

    throw new IllegalArgumentException("Invalid import path %s".formatted(t));
  }

  private AstImportPathAlias parseImportAliasOrBacktrack() {

    final var maybeIdentifier = next();
    if (maybeIdentifier != null) {
      if (maybeIdentifier.type() == TokenType.IDENTIFIER) {
        final var ii = parseIdentifier();
        final var maybeColon = next();
        if (maybeColon != null) {
          if (maybeColon.type() == TokenType.COLON) {

            final var importPath = this.parseImportPath_level0();
            return new AstImportPathAlias(ii, importPath);
          } else {
            queuedTokens.push(maybeColon);
            queuedTokens.push(maybeIdentifier);
          }
        }
      } else {
        queuedTokens.add(maybeIdentifier);
      }
    }

    return null;
  }

  private AstImport parseImport() {

    stayOrNext(TokenType.IMPORT);

    final var alias = parseImportAliasOrBacktrack();
    if (alias != null) {
      return new AstImport(alias);
    }

    final var importPath = this.parseImportPath_level0();
    return new AstImport(importPath);
  }

  private AstBecome parseBecome() {

    stayOrNext(TokenType.BECOME);

    next();
    final var identifierLike = parseIdentifierLike();
    if (identifierLike instanceof AstCall ic) {
      return new AstBecome(ic);
    }

    throw new IllegalArgumentException("Become can only become another function by a regular call");
  }

  private AstCompTime parseCompTime() {

    final var target = this.parseLevel11();
    if (target == null) {
      throw new IllegalArgumentException("The meta must target something");
    }

    return new AstCompTime(target);
  }

  private AstImpl parseImpl() {

    this.stayOrNext(TokenType.IMPL);

    final var token_id1 = this.next();
    if (token_id1 == null || token_id1.type() != TokenType.IDENTIFIER) {
      throw new IllegalArgumentException("Impl must have a for-target or identifier not a %s".formatted(token_id1));
    }

    final var id1 = this.parseIdentifier();

    final var token2 = this.next();
    if (token2 == null) {
      throw new IllegalArgumentException("There must be further tokens for the impl");
    }

    AstIdentifier traitIdentifier;
    AstExpression forExpression;

    if (token2.type() == TokenType.FOR) {

      final var target = this.parseLevel0(); // Maybe be more restrictive

      traitIdentifier = id1;
      forExpression = target;
    } else if (token2.type() == TokenType.OPEN_BRACE) {
      traitIdentifier = null;
      forExpression = id1;
      queuedTokens.push(token2);
    } else {
      throw new IllegalArgumentException("Impl must have a for-target or identifier, not %s".formatted(token_id1));
    }

    List<AstExpression> withArguments = new ArrayList<>();

    Token maybeWith;
    while ((maybeWith = next()) != null && maybeWith.type() == TokenType.WITH) {

      withArguments.add(parseLevel0());
    }

    if (maybeWith != null) {

      // Add back the last token
      queuedTokens.push(maybeWith);
    }

    final var block = this.parseBlock();
    return new AstImpl(
      traitIdentifier, forExpression, block,
      (withArguments.isEmpty()) ? null : withArguments.toArray(new AstExpression[0])
    );
  }

  private AstConditional parseIf() {

    final var predicate = this.parseLevel0();
    final var pass = this.parseLevel0();
    if (pass == null) {
      throw new IllegalArgumentException("No pass nor fail");
    }

    AstExpression fail;
    final var potentialElse = this.next();
    if (potentialElse != null) {
      if (potentialElse.type() == TokenType.ELSE) {
        fail = this.parseLevel0();
      } else {
        queuedTokens.add(potentialElse);
        fail = null;
      }
    } else {
      fail = null;
    }

    return new AstConditional(predicate, pass, fail);
  }

  private AstThen parseThen() {

    this.stayOrNext(TokenType.THEN);
    return new AstThen(this.parseLevel0());
  }

  private AstReturn parseReturn() {

    this.stayOrNext(TokenType.RETURN);
    return new AstReturn(this.parseLevel0());
  }

  private AstNew parseNew() {

    this.stayOrNext(TokenType.NEW);

    final var target = parseLevel0();

    final var t1 = next();
    if (t1 != null && t1.type() == TokenType.OPEN_BRACE) {

      final var collection = parseExpressionCollection(
        new TokenType[]{
          TokenType.CLOSE_BRACE,
          TokenType.CLOSE_PAREN,
          TokenType.CLOSE_BRACKET,
        },
        this::parseLevel0
      );

      return new AstNew(target, new AstExpressions(collection.toArray(new AstExpression[0])));
    }

    return new AstNew(target, null);
  }

  private AstYield parseYield() {

    this.stayOrNext(TokenType.YIELD);
    return new AstYield(this.parseLevel0());
  }

  private AstLoopDoWhile parseDo() {

    final var body = parseLevel0();
    final var predicate = parseLevel0();

    return new AstLoopDoWhile(body, predicate);
  }

  private AstLoopWhile parseWhile() {

    final var predicate = parseLevel0();
    final var body = parseLevel0();

    return new AstLoopWhile(predicate, body);
  }

  private AstLoopFor parseFor() {

    final var head = this.parseLevel0();
    final var block = this.parseLevel0();

    return new AstLoopFor(head, block);
  }
}
