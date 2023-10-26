package com.github.stmated.plang.lexer;

import com.github.stmated.plang.parser.NoSyncBufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;

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
  private final ArrayBlockingQueue<Token> queue = new ArrayBlockingQueue<>(100, true);

  private Token nextToken;

  private boolean skip;
  private int index_start;
  private int index = -1;
  private int c;

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
          case '!' -> newToken(TokenType.BANG);
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
                skip = true;
                yield this.advanceKeywordOtherwiseIdentifier();
              }

            } else if (isNumber(t)) {
              skip = true;
              yield this.advanceNumber(TokenType.LITERAL_INTEGER);
            }

            throw new IllegalArgumentException("Unexpected character '%s' (%s)".formatted((char) this.c, this.c));
          }
        };

        break;
      }

      nextToken = future;

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Token advanceAsMulAssignmentOtherwiseMul() throws IOException {

    c = this.read();
    if (c == '=') {
      return newToken(TokenType.MULTIPLY_ASSIGNMENT);
    } else {
      skip = true;
      return newToken(TokenType.MULTIPLY);
    }
  }

  private Token advanceAsAddAssignmentOrOtherwiseAdd() throws IOException {

    c = this.read();
    if (c == '=') {
      return newToken(TokenType.ADDITION_ASSIGNMENT);
    } else {
      skip = true;
      return newToken(TokenType.ADD);
    }
  }

  private Token advanceIntoTemplateString() throws IOException {

    // TODO: Obviously completely wrong. Needs to be fixed.
    return this.advanceUntil('`');
  }

  private Token advanceUntil(char endChar) throws IOException {

    var escaped = false;
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

    while ((c = this.read()) != -1) {

      final var t = Character.getType(c);
      if (isNotLetter(t) && isNotNumber(t)) {

        // The identifier can only be letters and number.
        // If anything else, then it is the end of the keyword.
        // TODO: THIS IS WRONG!
        skip = true;
        break;
      }

      node = node.children.get(c);
      if (node == null) {

        // Word not found. So it is an identifier. Advance forward as such.
        skip = true;
        return this.advanceIdentifier();
      } else if (node.tokenType != null) {
        longestKeyword = node.tokenType;
      } else if (longestKeyword != null) {

        // We've moved past a potential keyword hit. Back to being an identifier.
        longestKeyword = null;
      }
    }

    if (longestKeyword != null) {
      return this.newToken(longestKeyword);
    }

    skip = true;
    return this.newToken(TokenType.IDENTIFIER);
  }

  private Token advanceIdentifier() throws IOException {

    while ((c = this.read()) != -1) {

      final var t = Character.getType(c);
      if (isNotLetter(t) && isNotNumber(t)) {
        break;
      }
    }

    skip = true;
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

    c = this.read();
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
      skip = true;
      return this.newToken(TokenType.DIVIDE);
    }
  }

  private Token advanceAsDoubleColonOtherwiseColon() throws IOException {

    c = this.read();
    if (c == ':') {
      return this.newToken(TokenType.COLON_DOUBLE);
    } else {
      skip = true;
      return this.newToken(TokenType.COLON);
    }
  }

  private Token advanceAsArrowSingleOrSubAssOtherwiseSub() throws IOException {
    c = this.read();
    if (c == '>') {
      return this.newToken(TokenType.ARROW_SINGLE);
    } else if (c == '=') {
      return newToken(TokenType.SUBTRACTION_ASSIGNMENT);
    } else {
      skip = true;
      return this.newToken(TokenType.SUBTRACT);
    }
  }

  private Token advanceAsBitOrOtherwiseOr() throws IOException {
    c = this.read();
    if (c == '|') {
      return this.newToken(TokenType.OR);
    } else {
      skip = true;
      return this.newToken(TokenType.BIT_OR);
    }
  }

  private Token advanceAsBitAndOtherwiseAnd() throws IOException {
    c = this.read();
    if (c == '&') {
      return this.newToken(TokenType.AND);
    } else {
      skip = true;
      return this.newToken(TokenType.BIT_AND);
    }
  }

  private Token advanceAsGteOrBslOtherwiseGt() throws IOException {
    c = this.read();
    if (c == '>') {
      return this.newToken(TokenType.BIT_SHIFT_RIGHT);
    } else if (c == '=') {
      return this.newToken(TokenType.GTE);
    } else {
      skip = true;
      return this.newToken(TokenType.GT);
    }
  }

  private Token advanceAsLteOrBslOtherwiseLt() throws IOException {
    c = this.read();
    if (c == '<') {
      return this.newToken(TokenType.BIT_SHIFT_LEFT);
    } else if (c == '=') {
      return this.newToken(TokenType.LTE);
    } else {
      skip = true;
      return this.newToken(TokenType.LT);
    }
  }

  private Token advanceAsEqualsOrDoubleArrowOtherwiseAssign() throws IOException {
    c = this.read();
    if (c == '=') {
      return this.newToken(TokenType.EQUALS);
    } else if (c == '>') {
      return this.newToken(TokenType.ARROW_DOUBLE);
    } else {
      skip = true;
      return this.newToken(TokenType.ASSIGN);
    }
  }

  private Token advanceAsModulusOtherwiseRemainder() throws IOException {

    c = this.read();
    if (c == '%') {
      return this.newToken(TokenType.MODULUS);
    } else {
      skip = true;
      return this.newToken(TokenType.REMAINDER);
    }
  }

  private Token advanceNumber(TokenType numberType) throws IOException {

    while ((c = this.read()) != -1) {

      if (c == '_') {

        // Underscore can be used as a delimiter for readability.

      } else {
        final var t = Character.getType(c);
        if (isNotNumber(t)) {
          skip = true;
          return this.newToken(numberType);
        }
      }
    }

    return this.newToken(numberType);
  }

  private int read() throws IOException {

    if (skip) {
      skip = false;
      return c;
    }

    final var ch = reader.read();
    if (ch != -1) {
      contentBuffer.append((char) ch);
    }

    if (c != -1) {
      index++;
    }

    return ch;
  }

  private Token newToken(TokenType type) {

    // Do some changes to account for skipping chars.
    var actualEnd = ((skip || c == -1) ? index : index + 1);
    final var charLength = (actualEnd - index_start);

    final var str = contentBuffer.substring(0, charLength);
    contentBuffer.setLength(0);
    if (skip && c != -1) {
      contentBuffer.append((char) c);
    }

    return new Token(type, index_start, actualEnd, str);
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
