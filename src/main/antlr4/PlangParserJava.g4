parser grammar PlangParserJava;

@header {
package com.github.stmated.plang;
}

options {
    tokenVocab=PlangLexerJava;
}

import PlangParser;

literal
    : StringLiteral     # stringLiteral
    | templateStringLiteral # stringLiteralTemplate
    | NumericLiteral    # numericLiteral
    ;

templateStringLiteral
    : BackTick templateStringAtom* BackTick
    ;

templateStringAtom
    : TemplateStringAtom
    | TemplateStringStartExpression expression TemplateCloseBrace
    | TemplateStringEscapeAtom
    ;
