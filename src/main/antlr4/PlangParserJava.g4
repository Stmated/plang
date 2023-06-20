parser grammar PlangParserJava;

@header {
package com.github.stmated.plang;
}

options {
    tokenVocab=PlangLexerJava;
}

import PlangParser;

templateStringLiteral
    : BackTick templateStringAtom* BackTick
    ;

templateStringAtom
    : TemplateStringAtom
    | TemplateStringStartExpression expression TemplateCloseBrace
    | TemplateStringEscapeAtom
    ;
