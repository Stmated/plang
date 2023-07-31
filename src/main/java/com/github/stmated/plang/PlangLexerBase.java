package com.github.stmated.plang;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

public abstract class PlangLexerBase extends Lexer {

  /**
   * Keeps track of the current depth of nested template string backticks.
   * E.g. after the X in:
   * <p>
   * `${a ? `${X
   * <p>
   * templateDepth will be 2. This variable is needed to determine if a `}` is a
   * plain CloseBrace, or one that closes an expression inside a template string.
   */
  private int templateDepth = 0;

  /**
   * Keeps track of the depth of open- and close-braces. Used for expressions like:
   * <p>
   * `${[1, 2, 3].map(x => { return x * 2;}).join("")}`
   * <p>
   * where the '}' from `return x * 2;}` should not become a `TemplateCloseBrace`
   * token but rather a `CloseBrace` token.
   */
  private int bracesDepth = 0;

  protected PlangLexerBase(CharStream input) {
    super(input);
  }

  public void StartTemplateString() {
    this.bracesDepth = 0;
  }

  public boolean IsInTemplateString() {
    return this.templateDepth > 0 && this.bracesDepth == 0;
  }

  protected void ProcessOpenBrace() {
    bracesDepth++;
  }

  protected void ProcessCloseBrace() {
    bracesDepth--;
  }

  protected void IncreaseTemplateDepth() {
    this.templateDepth++;
  }

  protected void DecreaseTemplateDepth() {
    this.templateDepth--;

  }
}
