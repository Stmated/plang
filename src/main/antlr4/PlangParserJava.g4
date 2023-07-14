parser grammar PlangParserJava;

@header {
package com.github.stmated.plang;
}

options {
    tokenVocab=PlangLexerJava;
}

import PlangParser;

stringLiteralSpec
    : StringLiteral
    | templateStringLiteral
    ;

templateStringLiteral
    : BackTick templateStringAtom* BackTick
    ;

templateStringAtom
    : TemplateStringAtom
    | TemplateStringStartExpression expr TemplateCloseBrace
    | TemplateStringEscapeAtom
    ;
