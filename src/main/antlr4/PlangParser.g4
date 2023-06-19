parser grammar PlangParser;

@header {
package com.github.stmated.plang;
}

options {
    tokenVocab=PlangLexer;
    superClass=PlangParserBase;
}

root
    : statement* EOF
    ;

statement
    : assignment SemiColon
    | implDeclaration
    | expression SemiColon
    | fieldDeclaration SemiColon
    | block
    | withScope
    ;

withScopeEntry
    : expression (Colon Identifier)?
    ;

withScope
    : With OpenPara? withScopeEntry (Comma withScopeEntry)* ClosePara? block
    ;

parameter
    : accessLevel? valVar? identifier typeSpecifier?
    ;

parameterList
    : parameter (Comma parameter)*
    ;

functionSignature
    : OpenPara parameterList? ClosePara typeSpecifier
    ;

// NOTE: Goal is to make EVERYTHING an expression -- everything should return a value
//          Should make it easier to create common patterns for things
expression
    : owner=expression Dot member=expression                            # dotExpression
    | lhs=expression operator rhs=expression                            # binaryExpression
    | owner=expression OpenBracket accessor=expression CloseBracket     # expressionAccessor
    | from=expression DoubleDot to=expression                           # rangeExpression
    | OpenBracket expressionList? CloseBracket                          # arrayCreationExpression
    | construction                                                      # constructionExression
    | call                                                              # callExpression
    | function                                                          # functionExpression
    | functionSignature                                                 # functionSignatureExpression
    | OpenPara expression ClosePara                                     # groupedExpression
    | Bang expression                                                   # notExpression
    | Return expression                                                 # returnExpression
    | Then expression                                                   # thenExpression
    | ifStatement                                                       # ifStatementExpression
    | struct                                                            # structExpression
    | trait                                                             # traitExpression
    | literal                                                           # literalExpression
    | Identifier                                                        # identifierExpression
    | type                                                              # typeExpression
    ;


valVar
    : (Val | Var)
    ;

typeSpecifier
    : Colon type
    ;

accessLevel
    : Ref
    ;

argument
    : identifier Equals expression  # namedArgument
    | expression                    # indexedArgument
    ;

// TODO: Support named arguments
argumentList
    : argument (Comma argument)*
    ;

operator
    : Plus
    | Minus
    | Multiply
    | Divide
    | Modulus
    | LTE
    | GTE
    | LT
    | GT
    ;

assignment
    : valVar? identifier typeSpecifier? Equals expression
    ;

// Remove this and just make it optional in 'assignment'?
// The lexing/parsing should be lenient, and up to next stage to validate
fieldDeclaration
    : valVar? identifier typeSpecifier
    ;

expressionList
    : expression (Comma expression)*
    ;

block
    // Faster parsing, but should be illegal to mix when validated
    : (OpenBrace | Then) statement* (End | CloseBrace)
    ;

ifStatement
    : If expression expression (Else expression)?
    ;

// TODO: This should be so much more, like "extends" or "super" or other conditions
genericListEntry
    : type
    ;

genericList
    : genericListEntry (Comma genericListEntry)*
    ;

genericSignature
    : LT genericList? GT
    ;

call
    : Tilde? Identifier genericSignature? OpenPara argumentList? ClosePara
    ;

keyValuePair
    : Identifier Equals expression
    ;

quickConstructorEntry
    : keyValuePair
    | expression
    ;

quickConstructorEntryList
    : quickConstructorEntry (Comma quickConstructorEntry)*
    ;

construction
    : type OpenBrace quickConstructorEntryList? CloseBrace
    ;

functionBody
    : OpenBrace statement* CloseBrace
    | expression
    ;

function
    // If no signature, it is assumed no-args
    : OpenPara parameterList? ClosePara typeSpecifier? ArrowDouble functionBody
    | parameter? ArrowDouble functionBody
    ;

literal
    : templateStringLiteral # stringLiteralTemplate
    | StringLiteral     # stringLiteral
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

struct
    : Struct OpenBrace statement* CloseBrace
    ;

trait
    : Trait OpenBrace (statement)* CloseBrace
    ;

implContextParameter
    : Identifier Colon Identifier
    ;

implContextParameterList
    : implContextParameter (Comma implContextParameter)*
    ;

implContextDeclaration
    : With implContextParameterList
    ;

implDeclaration
    : Impl identifier For type implContextDeclaration? OpenBrace statement* CloseBrace
    ;

type
    : type Plus type                  # unionType
    | type '|' type                   # intersectionType
    | type Minus type                 # notType
    | OpenPara type ClosePara         # groupedType
    | ifStatement                     # conditionalType // Up to parser to decide if it is legal
    | type genericSignature           # genericType
    | Identifier                      # typeName
    | NumericLiteral                  # numericalType
    | Nominal type                    # nominalType
    ;

identifier
    : Identifier
    ;
