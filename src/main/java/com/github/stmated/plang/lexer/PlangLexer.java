package com.github.stmated.plang.lexer;

import com.github.stmated.plang.exceptions.InvalidDecimalsException;
import com.github.stmated.plang.parser.NoSyncBufferedReader;
import com.github.stmated.plang.util.TextLocation;
import com.github.stmated.plang.util.TextRange;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

public class PlangLexer implements AutoCloseable, Iterator<Token> {

  private static final TrieNode keywordNode;

  private static class TrieNode {

    final Map<Integer, TrieNode> children = new HashMap<>();

    TokenType tokenType;

    public void insert(String word, TokenType tokenType) {
      TrieNode current = this;

      for (final char ch : word.toCharArray()) {
        current = current.children.computeIfAbsent((int) ch, c -> new TrieNode());
      }

      current.tokenType = tokenType;
    }
  }

  static {
    keywordNode = new TrieNode();
    for (final var keyword : TokenType.keywords()) {
      final var keywordString = keyword.name().toLowerCase();
      keywordNode.insert(keywordString, keyword);
    }
  }

  private final NoSyncBufferedReader reader;
//  private final ArrayBlockingQueue<Token> queue = new ArrayBlockingQueue<>(100, true);

  private Token nextToken;

  //private boolean skip;
  private int index_start;
  private int index = -1;
//  private int c;

  private final Deque<Integer> queue = new ArrayDeque<>();

  private final StringBuilder contentBuffer = new StringBuilder(128);

  public PlangLexer(InputStream is) {

    this.reader = new NoSyncBufferedReader(new InputStreamReader(is));
    this.advance();
  }

  @Override
  public void close() throws Exception {
    reader.close();
  }

  private void advance() {

    try {

      Token future = null;

      int c;
      while ((c = this.read()) != -1) {

        if (Character.isWhitespace(c)) {
          contentBuffer.setLength(0);
          continue;
        }

        markStart();
        future = switch (c) {
          case ',' -> newToken(TokenType.COMMA);
          case ';' -> newToken(TokenType.SEMI_COLON);
          case '^' -> newToken(TokenType.POW);
          case '!' -> advanceAsNotEqualsOtherwiseBang();
          case '(' -> newToken(TokenType.OPEN_PAREN);
          case ')' -> newToken(TokenType.CLOSE_PAREN);
          case '{' -> newToken(TokenType.OPEN_BRACE);
          case '}' -> newToken(TokenType.CLOSE_BRACE);
          case '[' -> newToken(TokenType.OPEN_BRACKET);
          case ']' -> newToken(TokenType.CLOSE_BRACKET);
          case '?' -> newToken(TokenType.QUESTION_MARK);
          case '$' -> newToken(TokenType.DOLLAR);
          case '@' -> newToken(TokenType.META);
          case '~' -> newToken(TokenType.TILDE);
          case '.' -> newToken(TokenType.DOT);
          case ':' -> advanceAsDoubleColonOtherwiseColon();
          case '*' -> advanceAsMulAssignmentOtherwiseMul();
          case '/' -> advanceCommentOtherwiseDivAssignmentOrDiv();
          case '"' -> advanceUntil('"');
          case '\'' -> advanceUntil('\'');
          case '`' -> advanceIntoTemplateString();
          case '+' -> advanceAsAddAssignmentOrOtherwiseAdd();
          case '-' -> advanceAsArrowSingleOrSubAssOtherwiseSub();
          case '%' -> advanceAsModulusOtherwiseRemainder();
          case '=' -> advanceAsEqualsOrDoubleArrowOtherwiseAssign();
          case '<' -> advanceAsLteOrBslOtherwiseLt();
          case '>' -> advanceAsGteOrBslOtherwiseGt();
          case '|' -> advanceAsBitOrOtherwiseOr();
          case '&' -> advanceAsBitAndOtherwiseAnd();
          default -> {
            final var t = Character.getType(c);
            if (isLetter(t)) {

              // This is where we either end up with a keyword, or an identifier
              if (c == '_') {

                // This is for sure an identifier, advance the whole word.
                yield this.advanceIdentifier();
              } else {
                backtrack(c);
                yield this.advanceKeywordOtherwiseIdentifier();
              }

            } else if (isNumber(t)) {
              backtrack(c);
              yield this.advanceNumber();
            }

            throw new IllegalArgumentException("Unexpected character '%s' (%s)".formatted((char) c, c));
          }
        };

        break;
      }

      nextToken = future;

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Token advanceAsNotEqualsOtherwiseBang() throws IOException {

    var c = this.read();
    if (c == '=') {
      return newToken(TokenType.NOT_EQUALS);
    } else {
      backtrack(c);
      return newToken(TokenType.BANG);
    }
  }

  private Token advanceAsMulAssignmentOtherwiseMul() throws IOException {

    var c = this.read();
    if (c == '=') {
      return newToken(TokenType.MULTIPLY_ASSIGNMENT);
    } else {
      backtrack(c);
      return newToken(TokenType.MULTIPLY);
    }
  }

  private Token advanceAsAddAssignmentOrOtherwiseAdd() throws IOException {

    var c = this.read();
    if (c == '=') {
      return newToken(TokenType.ADDITION_ASSIGNMENT);
    } else {
      backtrack(c);
      return newToken(TokenType.ADD);
    }
  }

  private Token advanceIntoTemplateString() throws IOException {

    // TODO: Obviously completely wrong. Needs to be fixed.
    return this.advanceUntil('`');
  }

  private Token advanceUntil(char endChar) throws IOException {

    var escaped = false;
    int c;
    while ((c = this.read()) != -1) {

      if (c == endChar) {
        if (!escaped) {
          break;
        }
      } else if (c == '\\') {
        escaped = true;
      } else if (escaped) {
        escaped = false;
      }
    }

    return newToken(TokenType.LITERAL_STRING);
  }

  private Token advanceUntil(String str) throws IOException {

    var idx = 0;
    final var idx_stop = (str.length() - 1);
    var escaped = false;
    int c;
    while ((c = this.read()) != -1) {

      final var endChar = str.charAt(idx);
      if (c == endChar && !escaped) {
        if (idx == idx_stop) {
          break;
        } else {
          idx++;
        }
      } else if (c == '\\') {
        escaped = true;
      } else if (escaped) {
        escaped = false;
      }
    }

    return newToken(TokenType.LITERAL_STRING);
  }

  private Token advanceKeywordOtherwiseIdentifier() throws IOException {

    var node = keywordNode;
    TokenType longestKeyword = null;

    int c;
    while ((c = this.read()) != -1) {

      final var t = Character.getType(c);
      if (isNotLetter(t) && isNotNumber(t)) {

        // The identifier can only be letters and number.
        // If anything else, then it is the end of the keyword.
        break;
      }

      node = node.children.get(c);
      if (node == null) {

        // Word not found. So it is an identifier. Advance forward as such.
        backtrack(c);
        return this.advanceIdentifier();
      } else if (node.tokenType != null) {
        longestKeyword = node.tokenType;
      } else if (longestKeyword != null) {

        // We've moved past a potential keyword hit. Back to being an identifier.
        longestKeyword = null;
      }
    }

    backtrack(c);

    if (longestKeyword == TokenType.TRUE) {
      return this.newToken(TokenType.LITERAL_BOOLEAN);
    } else if (longestKeyword == TokenType.FALSE) {
      return this.newToken(TokenType.LITERAL_BOOLEAN);
    }

    return this.newToken(Objects.requireNonNullElse(longestKeyword, TokenType.IDENTIFIER));
  }

  private Token advanceIdentifier() throws IOException {

    int c;
    while ((c = this.read()) != -1) {

      final var t = Character.getType(c);
      if (isNotLetter(t) && isNotNumber(t)) {
        break;
      }
    }

    backtrack(c);
    final var token = this.newToken(TokenType.IDENTIFIER);
    if ("_".equals(token.content())) {
      return new Token(TokenType.UNDERSCORE, token.start(), token.end(), token.content());
    }

    return token;
  }

  private static boolean isNotLetter(final int t) {
    return (t < Character.UPPERCASE_LETTER || t > Character.OTHER_LETTER)
        && t != Character.CONNECTOR_PUNCTUATION;
  }

  private static boolean isLetter(final int t) {
    return (t >= Character.UPPERCASE_LETTER && t <= Character.OTHER_LETTER)
        || t == Character.CONNECTOR_PUNCTUATION;
  }

  private static boolean isNotNumber(final int t) {
    return t < Character.DECIMAL_DIGIT_NUMBER || t > Character.OTHER_NUMBER;
  }

  private static boolean isNumber(final int t) {
    return t >= Character.DECIMAL_DIGIT_NUMBER && t <= Character.OTHER_NUMBER;
  }

  private Token advanceCommentOtherwiseDivAssignmentOrDiv() throws IOException {

    var c = this.read();
    if (c == '/') {

      // TODO: Add multiline support
      // TODO: Change advanceUntil to only advance, return boolean, no token

      final var contentToken = this.advanceUntil('\n');
      final var content = contentToken.content();
      final var stripped = content.replaceAll("[\r\n]", "");
      final var sub = stripped.substring(2); // Remove start slashes

      return new Token(
          TokenType.COMMENT_SINGLE_LINE,
          contentToken.start(),
          contentToken.end(),
          sub
      );
    } else if (c == '*') {

      final var contentToken = this.advanceUntil("*/");
      final var content = contentToken.content();
      final var sub = content.substring(2, content.length() - 2); // Remove start & end

      // TODO: There is probably a lot of work that needs to be done here
      //        to make the multiline comment strip its start of each line uniformly
      //        Also need to consider how to handle empty first and last lines

      return new Token(
          TokenType.COMMENT_MULTI_LINE,
          contentToken.start(),
          contentToken.end(),
          sub
      );
    } else if (c == '=') {
      return newToken(TokenType.DIVIDE_ASSIGNMENT);
    } else {
      backtrack(c);
      return this.newToken(TokenType.DIVIDE);
    }
  }

  private Token advanceAsDoubleColonOtherwiseColon() throws IOException {

    var c = this.read();
    if (c == ':') {
      return this.newToken(TokenType.COLON_DOUBLE);
    } else {
      backtrack(c);
      return this.newToken(TokenType.COLON);
    }
  }

  private Token advanceAsArrowSingleOrSubAssOtherwiseSub() throws IOException {
    var c = this.read();
    if (c == '>') {
      return this.newToken(TokenType.ARROW_SINGLE);
    } else if (c == '=') {
      return newToken(TokenType.SUBTRACTION_ASSIGNMENT);
    } else {
      backtrack(c);
      return this.newToken(TokenType.SUBTRACT);
    }
  }

  private Token advanceAsBitOrOtherwiseOr() throws IOException {
    var c = this.read();
    if (c == '|') {
      return this.newToken(TokenType.OR);
    } else {
      backtrack(c);
      return this.newToken(TokenType.BIT_OR);
    }
  }

  private Token advanceAsBitAndOtherwiseAnd() throws IOException {
    var c = this.read();
    if (c == '&') {
      return this.newToken(TokenType.AND);
    } else {
      backtrack(c);
      return this.newToken(TokenType.BIT_AND);
    }
  }

  private Token advanceAsGteOrBslOtherwiseGt() throws IOException {
    var c = this.read();
    if (c == '>') {
      return this.newToken(TokenType.BIT_SHIFT_RIGHT);
    } else if (c == '=') {
      return this.newToken(TokenType.GTE);
    } else {
      backtrack(c);
      return this.newToken(TokenType.GT);
    }
  }

  private Token advanceAsLteOrBslOtherwiseLt() throws IOException {
    var c = this.read();
    if (c == '<') {
      return this.newToken(TokenType.BIT_SHIFT_LEFT);
    } else if (c == '=') {
      return this.newToken(TokenType.LTE);
    } else {
      backtrack(c);
      return this.newToken(TokenType.LT);
    }
  }

  private Token advanceAsEqualsOrDoubleArrowOtherwiseAssign() throws IOException {
    var c = this.read();
    if (c == '=') {
      return this.newToken(TokenType.EQUALS);
    } else if (c == '>') {
      return this.newToken(TokenType.ARROW_DOUBLE);
    } else {
      backtrack(c);
      return this.newToken(TokenType.ASSIGN);
    }
  }

  private Token advanceAsModulusOtherwiseRemainder() throws IOException {

    var c = this.read();
    if (c == '%') {
      return this.newToken(TokenType.MODULUS);
    } else {
      backtrack(c);
      return this.newToken(TokenType.REMAINDER);
    }
  }

  private TextRange createCurrentTextRange() {
    return new TextRange(
      new TextLocation(-1, this.index_start),
      new TextLocation(-1, this.index)
    );
  }

  private void backtrack(int ch) {
    if (ch != -1) {
      queue.push(ch);
      index--;
      contentBuffer.setLength(contentBuffer.length() - 1);
    }
  }

  private Token advanceNumber() throws IOException {

    var numberType = TokenType.LITERAL_INTEGER;
    var hasPrefix = false;
    var dotIndex = -1;
    var offsetLeft = 0;

    var c = this.read();
    if (c == '0') {

      final var c2 = this.read();
      final var t2 = Character.getType(c2);

      if (c2 == 'x' || c2 == 'X') {
        hasPrefix = true;
        numberType = TokenType.LITERAL_INTEGER_HEX;
        offsetLeft = 2;
      } else if (c2 == 'b' || c2 == 'B') {
        hasPrefix = true;
        numberType = TokenType.LITERAL_INTEGER_BINARY;
        offsetLeft = 2;
      } else if (isNumber(t2)) {
        hasPrefix = true;
        numberType = TokenType.LITERAL_INTEGER_OCTAL;
        offsetLeft = 2;
      } else {
        backtrack(c2);
        backtrack(c);
      }
    }

    var probableNumberType = numberType;
    var i = -1;
    while ((c = this.read()) != -1) {

      i++;
      if (c == '.') {

        var c2 = read();
        if (c2 == '.') {

          // There were 2 consecutive dots, which means this is a likely DOUBLE_DOT (range expression)
          // So return from here as such as continue lexing with that.

          backtrack(c2);
          backtrack(c);

          return this.newToken(numberType);
        } else {
          backtrack(c2);
        }

        if (dotIndex != -1) {
          throw new InvalidDecimalsException("Number is not allowed to have multiple decimal markers", createCurrentTextRange());
        }

        probableNumberType = TokenType.LITERAL_DOUBLE;
        dotIndex = i;

      } else if (c == '_' && i > 0) {
        // Underscore can be used as a delimiter for readability.
      } else {

        final var t = Character.getType(c);
        if (isNotNumber(t)) {

          if (isLetter(t)) {

            if (hasPrefix) throw new IllegalArgumentException("Cannot mix number prefix and suffix");

            if (dotIndex != -1 && dotIndex == (i - 1)) {

              // If letter and previous was dot, then this is likely something as "1.toString()"
              backtrack(c);
              backtrack('.');
              return this.newToken(numberType);
            }

            if (c == 'f' || c == 'F') {
              return this.newToken(TokenType.LITERAL_FLOAT, 0, 1);
            } else if (c == 'd' || c == 'D') {
              return this.newToken(TokenType.LITERAL_DOUBLE, 0, 1);
            } else if (c == 'm' || c == 'M') {
              return this.newToken(TokenType.LITERAL_DECIMAL, 0, 1);
            } else if (c == 'l' || c == 'L') {
              if (dotIndex != -1) {
                throw new IllegalArgumentException("Longs cannot contain decimal marks");
              }

              return this.newToken(TokenType.LITERAL_INTEGER_LONG, 0, 1);
            } else {

              throw new IllegalArgumentException(STR."Unknown number suffix '\{c}'");
            }
          } else {

            backtrack(c);
            return this.newToken(numberType, offsetLeft, 0);
          }
        } else {

          // This is a regular number.
          // We will move the probable number kind to the number kind, since we've confirmed there are numbers remaining.
          numberType = probableNumberType;
        }
      }
    }

    return this.newToken(numberType, offsetLeft, 0);
  }

  private int read() throws IOException {

    final var ch = queue.isEmpty() ? reader.read() : queue.pop();

    if (ch != -1) {
      contentBuffer.append((char) ch);
      index++;
    }

    return ch;
  }

  private Token newToken(TokenType type) {
    return newToken(type, 0, 0);
  }

  private Token newToken(TokenType type, int offsetLeft, int offsetRight) {

    // Do some changes to account for skipping chars.
    final var str = contentBuffer.substring(offsetLeft, contentBuffer.length() - offsetRight);
    contentBuffer.setLength(0);

    return new Token(type, index_start, index + 1, str);
  }

  private void markStart() {
    index_start = index;
  }

  @Override
  public boolean hasNext() {
    return this.nextToken != null;
  }

  @Override
  public Token next() {

    if (this.nextToken == null) {
      throw new NoSuchElementException();
    }

    final var current = this.nextToken;
    this.advance();

    return current;
  }
}
