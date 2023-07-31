package com.github.stmated.plang;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

/**
 * All parser methods that used in grammar (p, prev, notLineTerminator, etc.)
 * should start with lower case char similar to parser rules.
 */
public abstract class PlangParserBase extends Parser {

  protected PlangParserBase(TokenStream input) {
    super(input);
  }
}
