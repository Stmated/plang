package org.inf.ast;

import org.inf.exceptions.UnexpectedTokenException;
import org.inf.lexer.Token;
import org.inf.lexer.TokenType;
import org.inf.ty.BitWidth;
import org.inf.ty.Ty;
import org.inf.ty.TyFlags;
import org.inf.ty.TyValueNumberInteger;
import org.inf.ty.util.Tys;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class TokenToAstRaising {

  private final Iterator<Token> iterator;

  private Token current;

  private final Deque<Token> queuedTokens = new ArrayDeque<>();

  public TokenToAstRaising(Iterator<Token> iterator) {
    this.iterator = iterator;
  }

  public Ast.Program parse() {

    final var children = new ArrayList<Ast.Expression>();

    try {
      while (hasNext()) {
        children.add(parseLevel0());
      }
    } catch (Exception ex) {

      final var expressionStrings = String.join("\n", children.stream().map(Object::toString).toList());
      throw new IllegalArgumentException("Exception '%s' after parsed:\n%s".formatted(ex.getMessage(), expressionStrings), ex);
    }

    return new Ast.Program(new Ast.Expressions(children.toArray(new Ast.Expression[0])));
  }

  /**
   * ASSIGN
   */
  private Ast.Expression parseLevel0() {

    final var lhs = parseLevel1();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ASSIGN) {
        final var rhs = parseLevel0();
        if (rhs != null) {
          return new Ast.Assignment(lhs, rhs);
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
  private Ast.Expression parseLevel1() {

    final var lhs = parseLevel2();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ARROW_DOUBLE) {
        final var rhs = parseLevel2();
        if (rhs != null) {
          return new Ast.Callable(lhs, rhs);
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
  private Ast.Expression parseLevel2_rhs_level0() {

    final var lhs = parseLevel2_rhs_level1();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.WHERE) {
        final var rhs = parseLevel2_rhs_level0();
        return new Ast.Where(lhs, rhs);
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * ASSIGN
   */
  private Ast.Expression parseLevel2_rhs_level1() {

    final var lhs = parseLevel2_rhs_level2();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ASSIGN) {
        final var rhs = parseLevel2_rhs_level1();
        if (rhs != null) {
          return new Ast.Assignment(lhs, rhs);
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
  private Ast.Expression parseLevel2_rhs_level2() {

    final var lhs = parseLevel6();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.COLON) {
        final var rhs = parseLevel6();
        if (rhs != null) {
          return new Ast.Labeling(lhs, rhs);
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
  private Ast.Expression parseLevel2() {

    final var lhs = parseLevel3();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.WHERE) {
        final var rhs = parseLevel2_rhs_level0(); // RHS goes back up to higher level
        return new Ast.Where(lhs, rhs);
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * COLON
   */
  private Ast.Expression parseLevel3() {

    final var lhs = parseLevel4();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.COLON) {
        final var rhs = parseLevel3();
        if (rhs != null) {
          return new Ast.Labeling(lhs, rhs);
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
  private Ast.Expression parseLevel4() {

    final var lhs = parseLevel5();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.AND || t == TokenType.OR) {
        final var rhs = parseLevel4();
        if (rhs != null) {
          return new Ast.BinaryOperation(lhs, Ast.BinaryOperationKind.fromTokenType(t), rhs);
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
  private Ast.Expression parseLevel5() {

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
          return new Ast.BinaryOperation(lhs, Ast.BinaryOperationKind.fromTokenType(t), rhs);
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
  private Ast.Expression parseLevel6() {

    final var lhs = parseLevel7();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ADD || t == TokenType.SUBTRACT) {
        final var rhs = parseLevel6(); // Recursive to same level
        if (rhs != null) {
          return new Ast.BinaryOperation(lhs, Ast.BinaryOperationKind.fromTokenType(t), rhs);
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
  private Ast.Expression parseLevel7() {

    final var lhs = parseLevel8();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.MULTIPLY || t == TokenType.DIVIDE) {
        final var rhs = parseLevel8();
        if (rhs != null) {
          return new Ast.BinaryOperation(lhs, Ast.BinaryOperationKind.fromTokenType(t), rhs);
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
  private Ast.Expression parseLevel8() {

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
          return new Ast.BinaryOperation(lhs, Ast.BinaryOperationKind.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for math operator");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  private Ast.Expression parseLevel9() {

    final var lhs = parseLevel10();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOUBLE_DOT) {
        final var rhs = parseLevel10();
        if (rhs != null) {
          return new Ast.Range(lhs, rhs);
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
  private Ast.Expression parseLevel10() {

    final var lhs = parseLevel11();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.IN) {
        final var rhs = parseLevel11();
        if (rhs != null) {
          return new Ast.In(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for range expression");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  private Ast.Expression parseLevel11() {

    final var lhs = parseLevel12();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.COLON_DOUBLE) {
        final var rhs = parseLevel11(); // Recursive
        if (rhs != null) {
          return new Ast.StaticAccess(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for static access");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  private Ast.Expression parseLevel12() {

    final var lhs = parseExpression();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOT) {
        final var rhs = parseLevel12(); // Recursive
        if (rhs != null) {
          return new Ast.DotAccess(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for dot access");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  private Ast.Expression parseExpression() {

    final var token = next();
    if (token == null) {
      return null;
    }

    final var callableAst = switch (token.type()) {
      case LITERAL_INTEGER -> parseLiteralInteger(token);
      // TODO: Need to add all the other precision number types, like double, float, etc. Especially with variable width...
      //        Right now the default decimal number is FLOAT, to make things easier in LLVM. But DECIMAL should be DECIMAL ^^;
      case LITERAL_DECIMAL -> new Ast.Literal(token.content(), Ty.DECIMAL);
      case LITERAL_FLOAT -> new Ast.Literal(token.content(), Ty.FLOAT);
      case LITERAL_DOUBLE -> new Ast.Literal(token.content(), Ty.DOUBLE);
      case LITERAL_BOOLEAN -> new Ast.Literal(token.content(), Ty.BOOLEAN);
      case LITERAL_STRING -> new Ast.Literal(token.content().substring(1, token.content().length() - 1), Ty.STRING);
      case LITERAL_STRING_TEMPLATE -> new Ast.Literal(token.content(), Ty.STRING); // TODO: Where and how to parse this?
      case LITERAL_INTEGER_BINARY -> new Ast.Literal(token.content(), Ty.INTEGER_BINARY);
      case LITERAL_INTEGER_HEX -> new Ast.Literal(token.content(), Ty.INTEGER_HEX);
      case LITERAL_INTEGER_OCTAL -> new Ast.Literal(token.content(), Ty.INTEGER_OCTAL);
      case LITERAL_INTEGER_LONG -> new Ast.Literal(token.content(), Ty.LONG);
      case UNDERSCORE -> new Ast.VariableSink();
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
        case MULTIPLY -> parsePotentialPointer();
        case BANG -> new Ast.Not(parseExpression());
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
        case SEMI_COLON, END -> new Ast.NoOp();
        case EXPORT -> parseExport();
        case IMPORT -> parseImport();
        case BECOME -> parseBecome();
        case META -> parseCompTime();
        case COMMENT_SINGLE_LINE, COMMENT_MULTI_LINE -> new Ast.Comment(token.content());
        case YIELD -> parseYield();
        default -> throw new IllegalArgumentException("Unknown token '%s'".formatted(token));
      };
    } else {
      var prefixed = parsePotentialFnCall(callableAst);
      if (prefixed == callableAst) {
        prefixed = parsePotentialBracketAccess(callableAst);
      }

      return prefixed;
    }
  }

  private Ast.Expression parsePotentialPointer() {

    // NOTE: Ugly, but we might not want to support anything else
    final var identifier = parseIdentifier();

    return new Ast.Lexeme("*" + identifier.name());
  }

  private final Pattern PATTERN_INTEGER_SUFFIX = Pattern.compile("\\d+([iu])(\\d+)");

  private Ast.Literal parseLiteralInteger(Token token) {

    final var matcher = PATTERN_INTEGER_SUFFIX.matcher(token.content());
    if (matcher.find()) {

      final var radix = Ty.INTEGER.radix();
      final var width = Integer.parseInt(matcher.group(2));

      final var ty = switch (matcher.group(1)) {
        case "i" -> new TyValueNumberInteger(radix, new BitWidth(width, true), true, EnumSet.noneOf(TyFlags.class));
        case "u" -> new TyValueNumberInteger(radix, new BitWidth(width, true), false, EnumSet.noneOf(TyFlags.class));
        default -> throw new UnexpectedTokenException(token);
      };

      return new Ast.Literal(token.content(), Tys.intern(ty));
    }

    return new Ast.Literal(token.content(), Ty.INTEGER);
  }

  private Ast.Expression parsePotentialBracketAccess(Ast.Expression expr) {

    var n = next();
    if (n != null) {

      if (n.type() == TokenType.OPEN_BRACKET) {
        final var bracket = this.parseBracket();
        return new Ast.BracketAccess(expr, bracket);
      } else {
        queuedTokens.add(n);
      }
    }

    return expr;
  }

  private Ast.Expression parseSpread() {

    final var next = next();
    if (next == null) {
      return null;
    }

    final var t = next.type();
    if (t == TokenType.IDENTIFIER || t == TokenType.OPEN_PAREN || t == TokenType.OPEN_BRACKET || t == TokenType.OPEN_BRACE) {
      queuedTokens.push(next);
      return new Ast.Spread(parseExpression());
    } else {
      queuedTokens.push(next);
      return new Ast.Rest();
    }
  }

  /**
   * `+(something)` is handled same as `(something)`.
   * <p>
   * If operator overloading is ever a thing, then a AstUnaryPlus might be needed.
   */
  private Ast.Expression parsePotentialDeclaredPositiveLiteralNumber() {
    final var potentialNumber = this.parseExpression();
    if (potentialNumber != null) {
      return potentialNumber;
    } else {
      throw new IllegalStateException("EOS");
    }
  }

  private Ast.Expression parsePotentialDeclaredNegativeLiteralNumber() {

    final var potentialNumber = this.parseExpression();
    if (potentialNumber != null) {

      if (potentialNumber instanceof Ast.Literal literal && literal.ty().isNumber()) {
        return new Ast.Literal("-" + literal.content(), literal.ty());
      } else {
        return new Ast.Negate(potentialNumber);
      }

    } else {
      throw new IllegalStateException("EOS");
    }
  }

  private Ast.Expression parseVarVal(final Token token) {

    final var identifier = this.parseIdentifier();
    final var mutabilityKind = (token.type() == TokenType.VAR) ? Ast.MutabilityKind.Mutable : Ast.MutabilityKind.Immutable;
    // TODO: If possible generalize this into an "InitialLabel"?

    Ast.Expression type;
    final var potentialColon = next();
    if (potentialColon != null && potentialColon.type() == TokenType.COLON) {
//      next(); // TODO: Wrong? Or is this what we should do always? next() before?
      //type = this.parseIdentifierLike();
      type = this.parseExpression();
    } else {
      queuedTokens.add(potentialColon);
      type = null;
    }

    final var declaration = new Ast.VariableDeclaration(
      identifier,
      mutabilityKind,
      type,
      false
    );

    final var potentialAssign = next();
    if (potentialAssign != null && potentialAssign.type() == TokenType.ASSIGN) {

      final var rhs = this.parseLevel0();
      return new Ast.Assignment(declaration, rhs);

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

  private Ast.Expression parseIdentifierLike() {

    final var identifier = this.parseIdentifier();
    return parsePotentialFnCall(identifier);
  }

  private Ast.Expression parsePotentialFnCall(Ast.Expression expr) {

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

        return new Ast.Call(expr, paren, bubbleUp, partial);

      } else if (partial) {
        throw new IllegalArgumentException("Tilde (partial call indicator) must be followed by an opening parenthesis");
      } else {
        queuedTokens.add(n);
      }
    }

    return expr;
  }

  private <T extends Ast.Expression> List<T> parseExpressionCollection(
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

  private Ast.Lexeme parseIdentifier() {

    final var token = stayOrNext(TokenType.IDENTIFIER);
    if (token == null) {
      return null;
    }

    return new Ast.Lexeme(token.content());
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

  private Ast.Block parseBlock() {

    stayOrNext(TokenType.OPEN_BRACE);

    // TODO: This is wrong -- it will consume our token!
    final var children = new ArrayList<Ast.Expression>();

    while (hasNext()) {

      final var token = next();
      if (token == null || token.type() == TokenType.CLOSE_BRACE) {
        break;
      }

      queuedTokens.add(token);
      children.add(this.parseLevel0());
    }

    return new Ast.Block(Ast.Expressions.from(children));
  }

  private Ast.Struct parseStruct() {

    final var block = this.parseBlock();
    return new Ast.Struct(block);
  }

  private Ast.Trait parseTrait() {

    final var block = this.parseBlock();
    return new Ast.Trait(block);
  }

  private Ast.Match parseMatch() {

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

    return new Ast.Match(target, new Ast.Expressions(collection.toArray(new Ast.Expression[0])));
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

  private Ast.With parseWith() {

    this.stayOrNext(TokenType.WITH);

    final Ast.Expression argument;
    if (ifNextAndBacktrack(TokenType.OPEN_PAREN)) {
      argument = parseParen();
    } else {
      argument = parseLevel0();
    }

    final var block = this.parseBlock();

    return new Ast.With(argument, block);
  }

  private Ast.TypePlaceholder parseDollar() {

    this.stayOrNext(TokenType.DOLLAR);

    final var identifier = this.parseIdentifier();

    return new Ast.TypePlaceholder(identifier);
  }

  private Ast.Infer parseInfer() {

    this.stayOrNext(TokenType.INFER);

    final var identifier = this.parseExpression();

    return new Ast.Infer(identifier);
  }

  private Ast.Paren parseParen() {

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
      return new Ast.Paren(collection.getFirst());
    } else if (collection.isEmpty()) {
      return new Ast.Paren(null);
    } else {
      return new Ast.Paren(new Ast.Expressions(collection.toArray(new Ast.Expression[0])));
    }
  }

  private Ast.Bracket parseBracket() {

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

    return new Ast.Bracket(collection.toArray(new Ast.Expression[0]));
  }

  private Ast.VariableDeclaration parseRef() {

    final var token = this.next();
    if (token != null) {

      if (token.type() == TokenType.VAL || token.type() == TokenType.VAR) {

        var varval = this.parseVarVal(current);

        if (varval instanceof Ast.Assignment ia) {
          varval = ia.lhs();
        }

        Ast.VariableDeclaration varDec;
        if (varval instanceof Ast.VariableDeclaration ivd) {
          varDec = ivd;
        } else {
          throw new RuntimeException("Not implemented");
        }

        return new Ast.VariableDeclaration(
          varDec.lexeme(),
          varDec.mutabilityKind(),
          varDec.type(),
          true
        );

      } else if (token.type() == TokenType.IDENTIFIER) {

        final var identifier = this.parseIdentifier();

        return new Ast.VariableDeclaration(
          identifier,
          Ast.MutabilityKind.Immutable,
          null,
          true
        );
      }
    }

    throw new IllegalArgumentException("ref must be followed by var/val/lexeme");
  }

  private Ast.Export parseExport() {

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

    return new Ast.Export(exported, isDefault);
  }

  private Ast.ImportCapable parseImportPath_level0() {

    final var lhs = parseImportPath_level1();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOT) {
        final var rhs = parseImportPath_level0();
        return new Ast.ImportPath(lhs, rhs);
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  private Ast.ImportCapable parseImportPath_level1() {

    var t = next();
    if (t != null) {
      if (t.type() == TokenType.OPEN_BRACE) {

        final var paths = new ArrayList<Ast.ImportCapable>();
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

        return new Ast.ImportPathGroup(paths.toArray(new Ast.ImportCapable[0]));

      } else {
        queuedTokens.push(t);
        return this.parseImportPath_level2();
      }
    }

    throw new IllegalArgumentException("Invalid import path, no token after import");
  }

  private Ast.ImportCapable parseImportPath_level2() {

    var t = next();
    if (t != null) {
      if (t.type() == TokenType.MULTIPLY) {
        return new Ast.ImportPathWildcard();
      } else if (t.type() == TokenType.IDENTIFIER) {

        final var id = parseIdentifier();
        return new Ast.ImportPathIdentifier(id);
      }
    }

    throw new IllegalArgumentException("Invalid import path %s".formatted(t));
  }

  private Ast.ImportPathAlias parseImportAliasOrBacktrack() {

    final var maybeIdentifier = next();
    if (maybeIdentifier != null) {
      if (maybeIdentifier.type() == TokenType.IDENTIFIER) {
        final var ii = parseIdentifier();
        final var maybeColon = next();
        if (maybeColon != null) {
          if (maybeColon.type() == TokenType.COLON) {

            final var importPath = this.parseImportPath_level0();
            return new Ast.ImportPathAlias(ii, importPath);
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

  private Ast.Import parseImport() {

    stayOrNext(TokenType.IMPORT);

    final var alias = parseImportAliasOrBacktrack();
    if (alias != null) {
      return new Ast.Import(alias);
    }

    final var importPath = this.parseImportPath_level0();
    return new Ast.Import(importPath);
  }

  private Ast.Become parseBecome() {

    stayOrNext(TokenType.BECOME);

    next();
    final var identifierLike = parseIdentifierLike();
    if (identifierLike instanceof Ast.Call ic) {
      return new Ast.Become(ic);
    }

    throw new IllegalArgumentException("Become can only become another function by a regular call");
  }

  private Ast.CompTime parseCompTime() {

    final var target = this.parseLevel11();
    if (target == null) {
      throw new IllegalArgumentException("The meta must target something");
    }

    return new Ast.CompTime(target);
  }

  private Ast.Impl parseImpl() {

    this.stayOrNext(TokenType.IMPL);

    final var token_id1 = this.next();
    if (token_id1 == null || token_id1.type() != TokenType.IDENTIFIER) {
      throw new IllegalArgumentException("Impl must have a for-target or lexeme not a %s".formatted(token_id1));
    }

    final var id1 = this.parseIdentifier();

    final var token2 = this.next();
    if (token2 == null) {
      throw new IllegalArgumentException("There must be further tokens for the impl");
    }

    Ast.Lexeme traitLexeme;
    Ast.Expression forExpression;

    if (token2.type() == TokenType.FOR) {

      final var target = this.parseLevel0(); // Maybe be more restrictive

      traitLexeme = id1;
      forExpression = target;
    } else if (token2.type() == TokenType.OPEN_BRACE) {
      traitLexeme = null;
      forExpression = id1;
      queuedTokens.push(token2);
    } else {
      throw new IllegalArgumentException("Impl must have a for-target or lexeme, not %s".formatted(token_id1));
    }

    List<Ast.Expression> withArguments = new ArrayList<>();

    Token maybeWith;
    while ((maybeWith = next()) != null && maybeWith.type() == TokenType.WITH) {

      withArguments.add(parseLevel0());
    }

    if (maybeWith != null) {

      // Add back the last token
      queuedTokens.push(maybeWith);
    }

    final var block = this.parseBlock();
    return new Ast.Impl(
      traitLexeme, forExpression, block,
      (withArguments.isEmpty()) ? null : withArguments.toArray(new Ast.Expression[0])
    );
  }

  private Ast.Conditional parseIf() {

    final var predicate = this.parseLevel0();
    final var pass = this.parseLevel0();
    if (pass == null) {
      throw new IllegalArgumentException("No pass nor fail");
    }

    Ast.Expression fail;
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

    return new Ast.Conditional(predicate, pass, fail);
  }

  private Ast.Then parseThen() {

    this.stayOrNext(TokenType.THEN);
    return new Ast.Then(this.parseLevel0());
  }

  private Ast.Return parseReturn() {

    this.stayOrNext(TokenType.RETURN);
    return new Ast.Return(this.parseLevel0());
  }

  private Ast.New parseNew() {

    this.stayOrNext(TokenType.NEW);

    final var allocator = parseIdentifier();
    final var target = parseLevel0();

    final var t1 = next();
    if (t1 != null) {

      if (t1.type() == TokenType.OPEN_BRACE || t1.type() == TokenType.OPEN_PAREN) {

        final var collection = parseExpressionCollection(
          new TokenType[]{TokenType.CLOSE_BRACE, TokenType.CLOSE_PAREN, TokenType.CLOSE_BRACKET},
          this::parseLevel0
        );

        final var exprs = Ast.Expressions.from(collection);
        final var arguments = (t1.type() == TokenType.OPEN_BRACE)
          ? new Ast.Block(exprs)
          : new Ast.Paren(exprs);

        return new Ast.New(target, allocator, arguments);
      }
    }

    return new Ast.New(target, allocator, null);
  }

  private Ast.Yield parseYield() {

    this.stayOrNext(TokenType.YIELD);
    return new Ast.Yield(this.parseLevel0());
  }

  private Ast.LoopDoWhile parseDo() {

    final var body = parseLevel0();
    final var predicate = parseLevel0();

    return new Ast.LoopDoWhile(body, predicate);
  }

  private Ast.LoopWhile parseWhile() {

    final var predicate = parseLevel0();
    final var body = parseLevel0();

    return new Ast.LoopWhile(predicate, body);
  }

  private Ast.LoopFor parseFor() {

    final var head = this.parseLevel0();
    final var block = this.parseLevel0();

    return new Ast.LoopFor(head, block);
  }
}
