package com.github.stmated.plang.lexer;

public enum TokenType {

  COMMENT_MULTI_LINE,
  COMMENT_SINGLE_LINE,

  OPEN_PAREN,
  CLOSE_PAREN,

  OPEN_BRACKET,
  CLOSE_BRACKET,

  OPEN_BRACE,
  CLOSE_BRACE,

  SEMI_COLON,

  TILDE, // ~

  TRIPLE_DOT, // ...f
  DOUBLE_DOT, // ..
  UNDERSCORE, // _
  DOT, // .

  LITERAL_INTEGER,
  LITERAL_INTEGER_HEX,
  LITERAL_INTEGER_OCTAL,
  LITERAL_INTEGER_BINARY,
  LITERAL_INTEGER_LONG,

  LITERAL_DECIMAL,
  LITERAL_FLOAT,
  LITERAL_DOUBLE,

  LITERAL_STRING,
  LITERAL_STRING_TEMPLATE,

  TRUE,
  FALSE,
  LITERAL_BOOLEAN,

  IDENTIFIER,

  DOLLAR,
  META,

  COLON,
  COLON_DOUBLE,
  COMMA,
  BANG,
  ASSIGN,
  QUESTION_MARK,

  BIT_AND,
  BIT_OR,

  BIT_SHIFT_LEFT,
  BIT_SHIFT_RIGHT,

  POW,
  REMAINDER, // %
  MODULUS, // %%

  DIVIDE,
  MULTIPLY,
  SUBTRACT,
  ADD,

  DIVIDE_ASSIGNMENT,
  MULTIPLY_ASSIGNMENT,
  ADDITION_ASSIGNMENT,
  SUBTRACTION_ASSIGNMENT,

  ARROW_DOUBLE, // =>
  ARROW_SINGLE, // ->

  OR,
  AND,

  LT,
  GT,
  EQUALS,
  GTE,
  LTE,

  // Now go with keywords

  IF,
  ELSE,
  MATCH,
  NEW,
  IMPORT,
  EXPORT,
  DEFAULT,

  THEN,
  END,

  IS,
  AS,
  QUALIFIER,

  BECOME,
  YIELD,
  RETURN,
  REF,

  STRUCT,
  IMPL,
  SUPER,
  DERIVE,
  INFER,

  OF,
  OUT,
  IN,

  DO,
  WHILE,
  FOREACH,
  FOR,

  WITH,
  WHERE,
  GIVEN,

  TRAIT,
  VAL,
  VAR;

  @Override
  public String toString() {
    return this.name();
  }

  private static final TokenType[] KEYWORDS = new TokenType[]{
    IF,
    ELSE,
    MATCH,
    NEW,
    IMPORT,
    EXPORT,
    DEFAULT,

    THEN,
    END,

    IS,
    AS,
    QUALIFIER,

    BECOME,
    YIELD,
    RETURN,
    REF,

    STRUCT,
    IMPL,
    SUPER,
    DERIVE,
    INFER,

    OF,
    OUT,
    IN,

    DO,
    WHILE,
    FOREACH,
    FOR,

    WITH,
    WHERE,
    GIVEN,

    TRAIT,
    VAL,
    VAR,

    TRUE,
    FALSE
  };

  public static TokenType[] keywords() {
    return KEYWORDS;
  }

  public boolean isKeyword() {
    for (final var keyword : KEYWORDS) {
      if (keyword == this) {
        return true;
      }
    }

    return false;
  }

  public boolean isLiteralNumber() {
    return this == LITERAL_INTEGER
           || this == LITERAL_DECIMAL || this == LITERAL_DOUBLE || this == LITERAL_FLOAT
           || this == LITERAL_INTEGER_BINARY || this == LITERAL_INTEGER_HEX || this == LITERAL_INTEGER_OCTAL || this == LITERAL_INTEGER_LONG;
  }
}
