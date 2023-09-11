package com.github.stmated.plang.parser;

import com.github.stmated.plang.hir.MutabilityKind;
import com.github.stmated.plang.ipr.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public class PlangInitialParser {

  private final Iterator<Token> iterator;

  private Token current;

  public PlangInitialParser(Iterator<Token> iterator) {
    this.iterator = iterator;
  }

  public InitialProgram parse() {

    final var children = new ArrayList<InitialExpression>();

    try {
      while (hasNext()) {
        children.add(parseLevel0());
      }
    } catch (Exception ex) {

      final var expressionStrings = String.join("\n", children.stream().map(Object::toString).toList());
      throw new IllegalArgumentException("Exception '%s' after parsed:\n%s".formatted(ex.getMessage(), expressionStrings), ex);
    }

    return new InitialProgram(children.toArray(new InitialExpression[0]));
  }

  /**
   * COMMA
   */
  private InitialExpression parseLevel0() {

    final var lhs = parseLevel0_1();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.COMMA) {
        final var rhs = parseLevel0();
        if (rhs != null) {
          return new InitialCollectionItem(lhs, rhs);
          //return new InitialAssignment(lhs, rhs);
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
   * ASSIGN
   */
  private InitialExpression parseLevel0_1() {

    final var lhs = parseLevel1();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ASSIGN) {
        final var rhs = parseLevel1();
        if (rhs != null) {
          return new InitialAssignment(lhs, rhs);
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
  private InitialExpression parseLevel1() {

    final var lhs = parseLevel2();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ARROW_DOUBLE) {
        final var rhs = parseLevel2();
        if (rhs != null) {
          return new InitialCallable(lhs, rhs);
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
  private InitialExpression parseLevel2() {

    final var lhs = parseLevel3();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.WHERE) {
        final var rhs = parseLevel3();
        if (rhs != null) {
          return new InitialWhere(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for where");
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
  private InitialExpression parseLevel3() {

    final var lhs = parseLevel4();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.COLON) {
        final var rhs = parseLevel3Rhs();
        if (rhs != null) {
          return new InitialLabeling(lhs, rhs);
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
   * RHS of level 1 (Colon labeling) is more restrictive.
   * Identifier or conditional.
   */
  private InitialExpression parseLevel3Rhs() {

    final var token = next();
    if (token != null) {

      if (token.type() == TokenType.IDENTIFIER) {

        // For label RHS, we will also check if it is a generic type.
        final var identifierOrType = this.parseIdentifierLikeOrType();
        if (identifierOrType != null) {
          return identifierOrType;
        }
      } else {

        // Go past LT, GT.
        // Many options as invalid as label RHS. But that is for later stages to decide.
        // But first add token back for next step to find.
        queuedTokens.push(token);
        final var expression = parseLevel6();
        if (expression != null) {
          return expression;
        }
      }
    }

    throw new IllegalArgumentException("There is no valid RHS for the labeling");
  }

  /**
   * AND | OR
   */
  private InitialExpression parseLevel4() {

    final var lhs = parseLevel5();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.AND || t == TokenType.OR) {
        final var rhs = parseLevel4();
        if (rhs != null) {
          return new InitialBinaryOperation(lhs, InitialBinaryOperationType.fromTokenType(t), rhs);
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
  private InitialExpression parseLevel5() {

    final var lhs = parseLevel6();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.EQUALS || t == TokenType.LTE || t == TokenType.GTE || t == TokenType.LT || t == TokenType.GT || t == TokenType.IS) {
        final var rhs = parseLevel6();
        if (rhs != null) {
          return new InitialBinaryOperation(lhs, InitialBinaryOperationType.fromTokenType(t), rhs);
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
  private InitialExpression parseLevel6() {

    final var lhs = parseLevel7();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.PLUS || t == TokenType.MINUS) {
        final var rhs = parseLevel6(); // Recursive to same level
        if (rhs != null) {
          return new InitialBinaryOperation(lhs, InitialBinaryOperationType.fromTokenType(t), rhs);
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
  private InitialExpression parseLevel7() {

    final var lhs = parseLevel8();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.MULTIPLY || t == TokenType.DIVIDE) {
        final var rhs = parseLevel8();
        if (rhs != null) {
          return new InitialBinaryOperation(lhs, InitialBinaryOperationType.fromTokenType(t), rhs);
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
  private InitialExpression parseLevel8() {

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
          return new InitialBinaryOperation(lhs, InitialBinaryOperationType.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for math operator");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  /**
   * DoubleDot
   */
  private InitialExpression parseLevel9() {

    final var lhs = parseLevel10();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOUBLE_DOT) {
        final var rhs = parseLevel10();
        if (rhs != null) {
          return new InitialRange(lhs, rhs);
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
   * Dot
   */
  private InitialExpression parseLevel10() {

    final var lhs = parseLevel11();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOT) {
        final var rhs = parseLevel10(); // Recursive
        if (rhs != null) {
          return new InitialDotAccess(lhs, rhs);
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
   * Meta
   */
  private InitialExpression parseLevel11() {

    final var lhs = parseExpression();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.META) {
        final var rhs = parseLevel11(); // Recursive
        if (rhs != null) {
          return new InitialMetaScope(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for meta");
        }
      } else {
        queuedTokens.push(token);
      }
    }

    return lhs;
  }

  private InitialExpression parseExpression() {

    final var token = next();
    if (token == null) {
      return null;
    }

    return switch (token.type()) {
      case LITERAL_INTEGER -> new InitialLiteral(Integer.parseInt(token.content()));
      case LITERAL_DECIMAL -> new InitialLiteral(Double.parseDouble(token.content()));
      case LITERAL_BOOLEAN_FALSE -> new InitialLiteral(false);
      case LITERAL_BOOLEAN_TRUE -> new InitialLiteral(true);
      case LITERAL_STRING -> new InitialLiteral(token.content());
      case LITERAL_STRING_TEMPLATE -> new InitialLiteral(token.content()); // TODO: Where and how to parse this?
      case LITERAL_INTEGER_BINARY -> new InitialLiteral(Integer.parseInt(token.content(), 2));
      case LITERAL_INTEGER_HEX -> new InitialLiteral(Integer.parseInt(token.content(), 16));
      case LITERAL_INTEGER_OCTAL -> new InitialLiteral(Integer.parseInt(token.content(), 8));
      case UNDERSCORE -> new InitialVariableSink();
      case BANG -> new InitialNot(parseExpression());
      case OPEN_BRACE -> parseBlock();
      case OPEN_PAREN -> parseParen();
      case OPEN_BRACKET -> parseBracket();
      case REF -> parseRef();
      case VAL, VAR -> parseVarVal(token);
      case STRUCT -> parseStruct();
      case TRAIT -> parseTrait();
      case IMPL -> parseImpl();
      case IF -> parseIf();
      case THEN -> parseThen();
      case RETURN -> parseReturn();
      case NEW -> parseNew();
      case SEMI_COLON, END -> new InitialNoOp();
//      case COMMA -> new InitialCollectionDelimiter();
      case IDENTIFIER -> parseIdentifierLike();
      case IDENTIFIER_GENERIC -> new InitialIdentifierGeneric(token.content());
      case EXPORT -> parseExport();
      case IMPORT -> parseImport();
      case BECOME -> parseBecome();
      case META -> parseMeta();
      case COMMENT_SINGLE_LINE, COMMENT_MULTI_LINE -> new InitialComment(token.content());
      case YIELD -> parseYield();
      case FOREACH -> parseForEach();
      case FOR -> parseFor();
      case DO -> parseDo();
      case WHILE -> parseWhile();
      case MATCH -> parseMatch();
      case TRIPLE_DOT -> new InitialVarargs();
      default -> {
        throw new IllegalArgumentException("Unknown token '%s'".formatted(token));
      }
    };
  }

  private InitialExpression parseVarVal(final Token token) {

    final var identifier = this.parseIdentifier();
    final var mutabilityKind = (token.type() == TokenType.VAR) ? MutabilityKind.Mutable : MutabilityKind.Immutable;
    // TODO: If possible generalize this into an "InitialLabel"?

    InitialExpression type;
    final var potentialColon = next();
    if (potentialColon != null && potentialColon.type() == TokenType.COLON) {
      next(); // TODO: Wrong? Or is this what we should do always? next() before?
      type = this.parseIdentifierLikeOrType();
    } else {
      queuedTokens.add(potentialColon);
      type = null;
    }

    final var declaration = new InitialVariableDeclaration(
      identifier,
      mutabilityKind,
      type,
      false
    );

    final var potentialAssign = next();
    if (potentialAssign != null && potentialAssign.type() == TokenType.ASSIGN) {

      final var rhs = this.parseLevel0();
      return new InitialAssignment(declaration, rhs);

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

  private final Deque<Token> queuedTokens = new ArrayDeque<>();

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

  private InitialExpression parseIdentifierLikeOrType() {

    final var identifierLike = this.parseIdentifierLike();

    if (identifierLike instanceof InitialIdentifier identifier) {

      final var n = next();
      if (n != null) {

        if (n.type() == TokenType.LT) {

          // This could be a generic type. Get all the children as such.
          // NOTE: This is probably too naive and will require something smarter.
          final var diamondChildren = parseUntil(TokenType.GT);
          final var diamondChildrenArray = diamondChildren.toArray(new InitialExpression[0]);

          return new InitialDiamond(identifier, diamondChildrenArray);
        } else {
          this.queuedTokens.add(n);
        }
      }
    }

    return identifierLike;
  }

  private List<InitialExpression> parseUntil(TokenType tt) {

    final var expressions = new ArrayList<InitialExpression>();

    try {
      Token token;
      while ((token = next()) != null) {

        if (token.type() == tt) {

          // We have found out stop. Let's exit.
          break;
        }

        // Let's stay on our current token.
        // TODO: This pattern seems strange. We should set HARD rules on when to read next or not.
        queuedTokens.add(token);

        final var expression = this.parseExpression();
        if (expression != null) {
          expressions.add(expression);
        } else {
          throw new IllegalArgumentException("Encountered EOF before %s".formatted(tt));
        }
      }
    } catch (Exception ex) {

      final var expressionStrings = expressions.stream().map(Object::toString).toList();
      final var expressionsString = "\n    " + String.join("\n    ", expressionStrings);
      throw new IllegalArgumentException("Failed parsing after found 'until'-expressions %s".formatted(expressionsString), ex);
    }

    return expressions;
  }

  private InitialExpression parseIdentifierLike() {

    // TODO: Goes against other code -- usually calls next()
    //        Need to always work the same way. Always assume read?
    //        Maybe have different methods "readXyz" and "parseXyz"?
    if (current != null) {
      if (current.type() == TokenType.META) {

        final var target = parseLevel10();
        if (target == null) {
          throw new IllegalArgumentException("There must be a meta target");
        }

        return new InitialMeta(target);

      }
    }

    final var identifierToken = current;
    final var identifier = this.parseIdentifier();

    var n = next();
    if (n != null) {

      List<InitialExpression> generics = null;
      if (n.type() == TokenType.LT) {

        generics = parseGenericArguments(identifierToken);
        n = next();
      }

      boolean partial = false;
      if (n != null && n.type() == TokenType.TILDE) {
        partial = true;
        n = next();
      }

      if (n != null) {
        if (n.type() == TokenType.OPEN_PAREN) {

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

          final var genericArray = (generics == null) ? null : generics.toArray(new InitialExpression[0]);
          return new InitialCall(identifier, paren, genericArray, bubbleUp, partial);

        } else if (partial) {
          throw new IllegalArgumentException("Tilde (partial call indicator) must be followed by an opening parenthesis");
        } else {
          queuedTokens.add(n);
        }
      }
    }

    return identifier;
  }

  private InitialExpression parseGenericType_level0() {

    final var lhs = parseGenericType_expression();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ASSIGN) {
        final var rhs = parseGenericType_expression();
        if (rhs != null) {
          return new InitialAssignment(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for type assignment");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

//  private InitialExpression parseGenericType_level1() {
//
//  }

  private InitialExpression parseGenericType_expression() {

    final var token = next();
    if (token == null) {
      return null;
    }

    return switch (token.type()) {
      case LITERAL_INTEGER -> new InitialLiteral(Integer.parseInt(token.content()));
      case LITERAL_DECIMAL -> new InitialLiteral(Double.parseDouble(token.content()));
      case LITERAL_BOOLEAN_FALSE -> new InitialLiteral(false);
      case LITERAL_BOOLEAN_TRUE -> new InitialLiteral(true);
      case LITERAL_STRING -> new InitialLiteral(token.content());
      case LITERAL_STRING_TEMPLATE -> new InitialLiteral(token.content());
      case LITERAL_INTEGER_BINARY -> new InitialLiteral(Integer.parseInt(token.content(), 2));
      case LITERAL_INTEGER_HEX -> new InitialLiteral(Integer.parseInt(token.content(), 16));
      case LITERAL_INTEGER_OCTAL -> new InitialLiteral(Integer.parseInt(token.content(), 8));
      case UNDERSCORE -> new InitialVariableSink();
      case BANG -> new InitialNot(parseExpression());
      case OPEN_PAREN -> parseParen();
      case IF -> parseIf();
      case COMMA -> new InitialCollectionDelimiter();
      case IDENTIFIER -> parseIdentifierLike();
      case IDENTIFIER_GENERIC -> new InitialIdentifierGeneric(token.content());
      case META -> parseMeta();
      case COMMENT_SINGLE_LINE, COMMENT_MULTI_LINE -> new InitialComment(token.content());
      default -> {
        throw new IllegalArgumentException("Unknown token '%s'".formatted(token));
      }
    };
  }

  private List<InitialExpression> parseGenericArguments(Token previousToken) {

    final var expressions = new ArrayList<InitialExpression>();

    stayOrNext(TokenType.LT);

    // TODO: Would be nice if this could be abstracted somehow; reuse code from other places more.

    if (current.start() != previousToken.end()) {

      // Generic arguments are whitespace-sensitive. They must be followed right after the previous token.
      queuedTokens.push(current);
      return null;
    }

    Token t;
    while ((t = next()) != null) {
      if (t.type() == TokenType.GT) {

        // We have found our stop. Let's exit.
        break;
      } else {

        // All other expressions should just be added to the list of generic items.
        // All kinds of garbage could be placed here, but that is for later stages to handle.
        queuedTokens.push(t);
        final var expression = parseGenericType_level0();
        expressions.add(expression);
      }
    }

    return expressions;
  }

  private InitialIdentifier parseIdentifier() {

    final var token = stayOrNext(TokenType.IDENTIFIER);
    if (token == null) {
      return null;
    }

    return new InitialIdentifier(token.content());
  }

  private InitialType parseType() {

    final var identifier = this.parseIdentifier();
    return new InitialType(identifier);
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

  private InitialBlock parseBlock() {

    stayOrNext(TokenType.OPEN_BRACE);

    // TODO: This is wrong -- it will consume our token!
    final var children = new ArrayList<InitialExpression>();

    while (hasNext()) {

      final var token = next();
      if (token == null || token.type() == TokenType.CLOSE_BRACE) {
        break;
      }

      queuedTokens.add(token);
      children.add(this.parseLevel0());
    }

    return new InitialBlock(children.toArray(new InitialExpression[0]));
  }

  private InitialStruct parseStruct() {

    final var block = this.parseBlock();
    return new InitialStruct(block);
  }

  private InitialTrait parseTrait() {

    final var block = this.parseBlock();
    return new InitialTrait(block);
  }

  private InitialMatch parseMatch() {

    this.stayOrNext(TokenType.MATCH);

    final var target = this.parseLevel0();
    final var block = this.parseBlock();

    return new InitialMatch(target, block);
  }

  private InitialParen parseParen() {

    this.stayOrNext(TokenType.OPEN_PAREN);

    final var children = new ArrayList<InitialExpression>();

    while (hasNext()) {

      final var token = next();
      if (token == null || token.type() == TokenType.CLOSE_PAREN) {
        break;
      }

      queuedTokens.push(token);
      children.add(this.parseLevel0());
    }

    return new InitialParen(children.toArray(new InitialExpression[0]));
  }

  private InitialBracket parseBracket() {

    this.stayOrNext(TokenType.OPEN_BRACKET);

    final var children = new ArrayList<InitialExpression>();

    while (hasNext()) {

      final var token = next();
      if (token == null || token.type() == TokenType.CLOSE_BRACKET) {
        break;
      }

      queuedTokens.add(token);
      children.add(this.parseLevel0());
    }

    return new InitialBracket(children.toArray(new InitialExpression[0]));
  }

  private InitialVariableDeclaration parseRef() {

    final var token = this.next();
    if (token != null) {

      if (token.type() == TokenType.VAL || token.type() == TokenType.VAR) {

        var varval = this.parseVarVal(current);

        if (varval instanceof InitialAssignment ia) {
          varval = ia.lhs();
        }

        InitialVariableDeclaration varDec;
        if (varval instanceof InitialVariableDeclaration ivd) {
          varDec = ivd;
        } else {
          throw new RuntimeException("Not implemented");
        }

        return new InitialVariableDeclaration(
          varDec.identifier(),
          varDec.mutabilityKind(),
          varDec.type(),
          true
        );

      } else if (token.type() == TokenType.IDENTIFIER) {

        final var identifier = this.parseIdentifier();

        return new InitialVariableDeclaration(
          identifier,
          MutabilityKind.Immutable,
          null,
          true
        );
      }
    }

    throw new IllegalArgumentException("ref must be followed by var/val/identifier");
  }

  private InitialExport parseExport() {

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

    return new InitialExport(exported, isDefault);
  }

  private InitialImportCapable parseImportPath_level0() {

    final var lhs = parseImportPath_level1();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOT) {
        final var rhs = parseImportPath_level0();
        return new InitialImportPath(lhs, rhs);
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  private InitialImportCapable parseImportPath_level1() {

    var t = next();
    if (t != null) {
      if (t.type() == TokenType.OPEN_BRACE) {

        final var paths = new ArrayList<InitialImportCapable>();
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

        return new InitialImportPathGroup(paths.toArray(new InitialImportCapable[0]));

      } else {
        queuedTokens.push(t);
        return this.parseImportPath_level2();
      }
    }

    throw new IllegalArgumentException("Invalid import path, no token after import");
  }

  private InitialImportCapable parseImportPath_level2() {

    var t = next();
    if (t != null) {
      if (t.type() == TokenType.MULTIPLY) {
        return new InitialImportPathWildcard();
      } else if (t.type() == TokenType.IDENTIFIER) {

        final var id = parseIdentifier();
        return new InitialImportPathIdentifier(id);
      }
    }

    throw new IllegalArgumentException("Invalid import path %s".formatted(t));
  }

  private InitialImportPathAlias parseImportAliasOrBacktrack() {

    final var maybeIdentifier = next();
    if (maybeIdentifier != null) {
      if (maybeIdentifier.type() == TokenType.IDENTIFIER) {
        final var ii = parseIdentifier();
        final var maybeColon = next();
        if (maybeColon != null) {
          if (maybeColon.type() == TokenType.COLON) {

            final var importPath = this.parseImportPath_level0();
            return new InitialImportPathAlias(ii, importPath);
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

  private InitialImport parseImport() {

    stayOrNext(TokenType.IMPORT);

    final var alias = parseImportAliasOrBacktrack();
    if (alias != null) {
      return new InitialImport(alias);
    }

    final var importPath = this.parseImportPath_level0();
    return new InitialImport(importPath);
  }

  private InitialBecome parseBecome() {

    stayOrNext(TokenType.BECOME);

    next();
    final var identifierLike = parseIdentifierLike();
    if (identifierLike instanceof InitialCall ic) {
      return new InitialBecome(ic);
    }

    throw new IllegalArgumentException("Become can only become another function by a regular call");
  }

  private InitialMeta parseMeta() {

    final var target = this.parseLevel10();
    if (target == null) {
      throw new IllegalArgumentException("The meta must target something");
    }

    return new InitialMeta(target);
  }

  private InitialImpl parseImpl() {

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

    InitialIdentifier traitIdentifier;
    InitialExpression forExpression;

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

    final var maybeWith = next();
    if (maybeWith != null) {
      if (maybeWith.type() == TokenType.WITH) {

        // TODO: Implement 'with' for impl
        throw new IllegalStateException("Not yet implemented to have 'with' on impl!");

      } else {
        queuedTokens.push(maybeWith);
      }
    }

    final var block = this.parseBlock();
    return new InitialImpl(traitIdentifier, forExpression, block);
  }

  private InitialConditional parseIf() {

    final var predicate = this.parseLevel0();
    final var pass = this.parseLevel0();
    if (pass == null) {
      throw new IllegalArgumentException("No pass nor fail");
    }

    InitialExpression fail;
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

    return new InitialConditional(predicate, pass, fail);
  }

  private InitialThen parseThen() {

    this.stayOrNext(TokenType.THEN);
    return new InitialThen(this.parseLevel0());
  }

  private InitialReturn parseReturn() {

    this.stayOrNext(TokenType.RETURN);
    return new InitialReturn(this.parseLevel0());
  }

  private InitialNew parseNew() {

    this.stayOrNext(TokenType.NEW);

    final var target = parseLevel0();
    InitialBlock block = null;

    final var t1 = next();
    if (t1 != null && t1.type() == TokenType.OPEN_BRACE) {
      block = parseBlock();
    }

    return new InitialNew(target, block);
  }

  private InitialYield parseYield() {

    this.stayOrNext(TokenType.YIELD);
    return new InitialYield(this.parseLevel0());
  }

  private InitialExpression parseVarOrValOrIdentifier() {

    // TODO: FIX! Is wrong!
    return this.parseVarVal(current);
  }

  private InitialLoopForEach parseForEach() {

    var parenthesized = false;
    var token1 = next();
    if (token1 != null) {
      if (token1.type() == TokenType.OPEN_PAREN) {
        next();
        parenthesized = true;
      } else {
        queuedTokens.push(token1);
      }
    }

    var target = parseLevel0();

    InitialExpression source = null;

    final var maybeIn = next();
    if (maybeIn != null) {
      if (maybeIn.type() == TokenType.IN) {
        source = this.parseLevel0();
      } else {

        source = target;
        target = null;
      }
    }

    if (parenthesized) {

      var token2 = next();
      if (token2 == null || token2.type() != TokenType.CLOSE_PAREN) {
        throw new IllegalArgumentException("If foreach loop opens with parenthesis, it must close with one not %s".formatted(token2));
      }

      queuedTokens.add(token2);
    }

    if (target instanceof InitialIdentifier ii) {
      target = new InitialVariableDeclaration(ii, MutabilityKind.Immutable, null, false);
    }

    final var block = this.parseLevel0();

    return new InitialLoopForEach(source, target, block);
  }

  private InitialLoopDoWhile parseDo() {

    final var body = parseLevel0();
    final var predicate = parseLevel0();

    return new InitialLoopDoWhile(body, predicate);
  }

  private InitialLoopWhile parseWhile() {

    final var predicate = parseLevel0();
    final var body = parseLevel0();

    return new InitialLoopWhile(predicate, body);
  }

  private InitialBlock parseOptionallyEnclosedBlock() {

    if (current.type() != TokenType.OPEN_BRACE) {
      queuedTokens.add(current);
      return new InitialBlock(new InitialExpression[] { this.parseLevel0() });
    }

    return this.parseBlock();
  }

  private InitialLoopFor parseFor() {

    var t = next();
    if (t != null && t.type() == TokenType.OPEN_PAREN) {
      t = next();
    } else {
      throw new IllegalArgumentException("A for-loop must have parenthesis");
    }

    final var assignments = new ArrayList<InitialAssignment>();

    while (t != null && t.type() != TokenType.SEMI_COLON) {

      if (t.type() == TokenType.COMMA) {
        t = next();
        continue;
      }

      final var dec = parseVarVal(t);
      if (dec instanceof InitialAssignment ia) {
        assignments.add(ia);
      } else {
        throw new IllegalArgumentException("Only assignments are allowed in first for-loop part");
      }

      t = next();
    }

    final var predicate = this.parseLevel0();

    t = next();
    if (t == null || t.type() != TokenType.SEMI_COLON) {
      throw new IllegalArgumentException("There must be a semi-colon after the predicate");
    }

    final var steppers = new ArrayList<InitialExpression>();
    do {

      if (t.type() == TokenType.COMMA) {
        continue;
      }

      final var step = parseLevel0();
      if (step != null) {
        steppers.add(step);
      }

      // TODO: This is probably bad -- since it could end up eating up all the rest of the file.
    } while ((t = next()) != null && t.type() != TokenType.CLOSE_PAREN);

    stayOrNext(TokenType.OPEN_BRACE, null);
    final var block = this.parseOptionallyEnclosedBlock();

    return new InitialLoopFor(
      assignments.toArray(new InitialAssignment[0]),
      predicate,
      steppers.toArray(new InitialExpression[0]),
      block
    );
  }
}
