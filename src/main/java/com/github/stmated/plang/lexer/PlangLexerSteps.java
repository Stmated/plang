package com.github.stmated.plang.lexer;

import java.util.Iterator;

/**
 * Will convert things like [LITERAL_INTEGER, DOT, LITERAL_INTEGER] into LITERAL_DECIMAL
 * But will still make [LITERAL_INTEGER, DOT, DOT, LITERAL_INTEGER] into [LITERAL_INTEGER, DOUBLE_DOT, LITERAL_INTEGER]
 */
public class PlangLexerSteps {

  public Iterator<Token> transform(Iterator<Token> iterator) {

//    return new DecimalIterator(
    return new DotIterator(iterator);
//    );
  }
}
