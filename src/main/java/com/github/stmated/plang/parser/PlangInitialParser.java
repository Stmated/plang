package com.github.stmated.plang.parser;

import com.github.stmated.plang.hir.MutabilityKind;
import com.github.stmated.plang.ipr.InitialAssignment;
import com.github.stmated.plang.ipr.InitialBinaryOperation;
import com.github.stmated.plang.ipr.InitialBinaryOperationType;
import com.github.stmated.plang.ipr.InitialBlock;
import com.github.stmated.plang.ipr.InitialBracket;
import com.github.stmated.plang.ipr.InitialCall;
import com.github.stmated.plang.ipr.InitialCallable;
import com.github.stmated.plang.ipr.InitialCollectionDelimiter;
import com.github.stmated.plang.ipr.InitialComment;
import com.github.stmated.plang.ipr.InitialConditional;
import com.github.stmated.plang.ipr.InitialDiamond;
import com.github.stmated.plang.ipr.InitialDotAccess;
import com.github.stmated.plang.ipr.InitialExport;
import com.github.stmated.plang.ipr.InitialExpression;
import com.github.stmated.plang.ipr.InitialForEach;
import com.github.stmated.plang.ipr.InitialIdentifier;
import com.github.stmated.plang.ipr.InitialImpl;
import com.github.stmated.plang.ipr.InitialLabeling;
import com.github.stmated.plang.ipr.InitialLiteral;
import com.github.stmated.plang.ipr.InitialMatch;
import com.github.stmated.plang.ipr.InitialMeta;
import com.github.stmated.plang.ipr.InitialMetaScope;
import com.github.stmated.plang.ipr.InitialNew;
import com.github.stmated.plang.ipr.InitialNoOp;
import com.github.stmated.plang.ipr.InitialNot;
import com.github.stmated.plang.ipr.InitialParen;
import com.github.stmated.plang.ipr.InitialProgram;
import com.github.stmated.plang.ipr.InitialRange;
import com.github.stmated.plang.ipr.InitialReturn;
import com.github.stmated.plang.ipr.InitialStruct;
import com.github.stmated.plang.ipr.InitialThen;
import com.github.stmated.plang.ipr.InitialTrait;
import com.github.stmated.plang.ipr.InitialType;
import com.github.stmated.plang.ipr.InitialVariableDeclaration;
import com.github.stmated.plang.ipr.InitialVariableSink;
import com.github.stmated.plang.ipr.InitialWhere;
import com.github.stmated.plang.ipr.InitialYield;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

public class PlangInitialParser {

  private final Iterator<Token> iterator;

  private Token current;

  public PlangInitialParser(Iterator<Token> iterator) {
    this.iterator = iterator;
  }

  public InitialProgram parse() {

    final var children = new ArrayList<InitialExpression>();

    while (hasNext()) {
      children.add(parseLevel0());
    }

    return new InitialProgram(children.toArray(new InitialExpression[0]));
  }

  /**
   * ASSIGN
   */
  private InitialExpression parseLevel0() {

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
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  /**
   * ARROW_DOUBLE
   */
  private InitialExpression parseLevel1() {

    final var lhs = parseLevel1_2();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.ARROW_DOUBLE) {
        final var rhs = parseLevel1_2();
        if (rhs != null) {
          return new InitialCallable(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for Double Arrow");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  /**
   * WHERE
   */
  private InitialExpression parseLevel1_2() {

    final var lhs = parseLevel2();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.WHERE) {
        final var rhs = parseLevel2();
        if (rhs != null) {
          return new InitialWhere(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for where");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  /**
   * COLON
   */
  private InitialExpression parseLevel2() {

    final var lhs = parseLevel3();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.COLON) {
        final var rhs = parseLevel2Rhs();
        if (rhs != null) {
          return new InitialLabeling(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for labeling");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  /**
   * RHS of level 1 (Colon labeling) is more restrictive.
   * Identifier or conditional.
   */
  private InitialExpression parseLevel2Rhs() {

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
        queuedTokens.add(token);
        final var expression = parseLevel5();
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
  private InitialExpression parseLevel3() {

    final var lhs = parseLevel4();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.AND || t == TokenType.OR) {
        final var rhs = parseLevel3();
        if (rhs != null) {
          return new InitialBinaryOperation(lhs, InitialBinaryOperationType.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for binary boolean logical operator");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  /**
   * EQUALS | LTE | GTE | LT | GT | IS
   */
  private InitialExpression parseLevel4() {

    final var lhs = parseLevel5();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.EQUALS || t == TokenType.LTE || t == TokenType.GTE || t == TokenType.LT || t == TokenType.GT || t == TokenType.IS) {
        final var rhs = parseLevel5();
        if (rhs != null) {
          return new InitialBinaryOperation(lhs, InitialBinaryOperationType.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for binary boolean comparison operator");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }



  /**
   * PLUS | MINUS
   */
  private InitialExpression parseLevel5() {

    final var lhs = parseLevel6();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.PLUS || t == TokenType.MINUS) {
        final var rhs = parseLevel5(); // Recursive to same level
        if (rhs != null) {
          return new InitialBinaryOperation(lhs, InitialBinaryOperationType.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for binary boolean logical operator");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  /**
   * MULTIPLY | DIVIDE
   */
  private InitialExpression parseLevel6() {

    final var lhs = parseLevel7();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.MULTIPLY || t == TokenType.DIVIDE) {
        final var rhs = parseLevel7();
        if (rhs != null) {
          return new InitialBinaryOperation(lhs, InitialBinaryOperationType.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for binary boolean logical operator");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  /**
   * Pow | Modulus | Remainder | BitShiftLeft | BitShiftRight | BitAnd | BitOr
   */
  private InitialExpression parseLevel7() {

    final var lhs = parseLevel8();
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
        final var rhs = parseLevel7(); // Recursive
        if (rhs != null) {
          return new InitialBinaryOperation(lhs, InitialBinaryOperationType.fromTokenType(t), rhs);
        } else {
          throw new IllegalArgumentException("No RHS for math operator");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  /**
   * DoubleDot
   */
  private InitialExpression parseLevel8() {

    final var lhs = parseLevel9();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOUBLE_DOT) {
        final var rhs = parseLevel9();
        if (rhs != null) {
          return new InitialRange(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for range expression");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  /**
   * Dot
   */
  private InitialExpression parseLevel9() {

    final var lhs = parseLevel10();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.DOT) {
        final var rhs = parseLevel9(); // Recursive
        if (rhs != null) {
          return new InitialDotAccess(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for range expression");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  /**
   * Meta
   */
  private InitialExpression parseLevel10() {

    final var lhs = parseExpression();
    final var token = next();

    if (token != null) {

      final var t = token.type();
      if (t == TokenType.META) {
        final var rhs = parseLevel10(); // Recursive
        if (rhs != null) {
          return new InitialMetaScope(lhs, rhs);
        } else {
          throw new IllegalArgumentException("No RHS for meta");
        }
      } else {
        queuedTokens.add(token);
      }
    }

    return lhs;
  }

  private InitialExpression parseExpression() {
    return this.parseExpression(null);
  }

  private InitialExpression parseExpression(TokenType stayIf) {

    final var token = (stayIf == null) ? next() : stayOrNext(stayIf);
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
      case SEMI_COLON -> new InitialNoOp();
      case COMMA -> new InitialCollectionDelimiter();
      case IDENTIFIER -> parseIdentifierLike();
      case EXPORT -> parseExport();
      case META -> parseMeta();
      case COMMENT_SINGLE_LINE,
          COMMENT_MULTI_LINE -> new InitialComment(token.content());
      case YIELD -> parseYield();
      case FOREACH -> parseForEach();
      case MATCH -> parseMatch();
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

  private final Queue<Token> queuedTokens = new ArrayDeque<>();

  private Token next() {

    if (!queuedTokens.isEmpty()) {
      return queuedTokens.poll();
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

    return expressions;
  }

  private InitialExpression parseIdentifierLike() {

    //final var prefix = next();
    // TODO: Goes against other code -- usually calls next()
    //        Need to always work the same way. Always assume read?
    //        Maybe have different methods "readXyz" and "parseXyz"?
    if (current != null) {
      if (current.type() == TokenType.META) {

        final var target = parseLevel9();
        if (target == null) {
          throw new IllegalArgumentException("There must be a meta target");
        }

        return new InitialMeta(target);

      }
    }

    final var identifier = this.parseIdentifier();

    final var n = next();
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

        return new InitialCall(identifier, paren, bubbleUp);
      } else {
        queuedTokens.add(n);
      }
    }

    return identifier;
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

    if (current != null && current.type() == t) {

//      if (skip) {
//        skip = false;
//      }

      if (queuedTokens.peek() == current) {
        queuedTokens.poll();
      }

      return current;
    }

    if (hasNext()) {

      final var token = next();
      if (token != null) {
        if (token.type() == t) {

          // All is well.
          return token;
        } else {
          throw new IllegalArgumentException("Next token '%s' is not a '%s'".formatted(token, t));
        }
      } else {
        throw new IllegalArgumentException("There is no '%s' after '%s'".formatted(t, current));
      }

    } else {
      throw new IllegalArgumentException("There is no '%s' after '%s'".formatted(t, current));
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

    var t = next();
    if (t != null && t.type() != TokenType.OPEN_PAREN) {
      queuedTokens.add(t);
    }

    final var target = this.parseLevel0();

    if (t != null && t.type() == TokenType.OPEN_PAREN) {
      t = next();
      if (t != null && t.type() != TokenType.CLOSE_PAREN) {
        queuedTokens.add(t);
      }
    }

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

      queuedTokens.add(token);
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

    final var exported = this.parseLevel0();
    if (exported == null) {
      throw new IllegalArgumentException("The export must export something");
    }

    return new InitialExport(exported);
  }

  private InitialMeta parseMeta() {

    final var target = this.parseLevel9();
    if (target == null) {
      throw new IllegalArgumentException("The meta must target something");
    }

    return new InitialMeta(target);
  }

  private InitialImpl parseImpl() {

    final var traitIdentifier = this.parseIdentifier();
    final var forToken = this.next();
    if (forToken == null || forToken.type() != TokenType.FOR) {
      throw new IllegalArgumentException("Impl must have a for-target");
    }

    final var forExpression = this.parseLevel0();
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

  private InitialForEach parseForEach() {

    var token1 = next();
    if (token1 != null && token1.type() == TokenType.OPEN_PAREN) {
      next();
    }

    final var varVal = this.parseVarOrValOrIdentifier();
    InitialVariableDeclaration varDec;
    if (varVal instanceof InitialVariableDeclaration ivd) {
      varDec = ivd;
    } else {
      throw new IllegalArgumentException("Not supported var dec");
    }

    this.stayOrNext(TokenType.IN);
    final var source = this.parseLevel0();

    // TODO: The source should be a method call!
    //          Need a good way of figure out when it is one!
    //          Lexer or parser?

    var token2 = next();
    if (token2 != null && token2.type() != TokenType.CLOSE_PAREN) {
      queuedTokens.add(token2);
    }

    final var block = this.parseLevel0();

    return new InitialForEach(source, varDec, block);
  }
}
