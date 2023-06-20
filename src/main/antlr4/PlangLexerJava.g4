lexer grammar PlangLexerJava;

import PlangLexer;

@header {
package com.github.stmated.plang;
}

options {
    superClass=PlangLexerBase;
}

OpenBrace: '{' {this.ProcessOpenBrace();};
TemplateCloseBrace:  '}' {this.IsInTemplateString()}? -> popMode;
CloseBrace: '}' {this.ProcessCloseBrace();};

BackTick
    : '`' {this.IncreaseTemplateDepth();} -> pushMode(TEMPLATE);

StringLiteral
    : ('"' DoubleStringCharacter* '"'
    | '\'' SingleStringCharacter* '\'')
    ;

mode TEMPLATE;

TemplateStringEscapeAtom:       '\\' .;
BackTickInside:                 '`' {this.DecreaseTemplateDepth();} -> type(BackTick), popMode;
TemplateStringStartExpression:  '${' {this.StartTemplateString();} -> pushMode(DEFAULT_MODE);
TemplateStringAtom:             ~[`\\];
