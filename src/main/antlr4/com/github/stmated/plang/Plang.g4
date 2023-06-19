grammar Plang;

options {
    superClass=PlangParserBase;
}

LineTerminator
    : [\r\n\u2028\u2029] -> channel(HIDDEN)
    ;

WhiteSpaces
    : [\t\u000B\u000C\u0020\u00A0]+ -> channel(HIDDEN)
    ;

MultiLineComment
    : '/*' .*? '*/' -> channel(2)
    ;
SingleLineComment
    : '//' ~[\r\n\u2028\u2029]* -> channel(2)
    ;

DoubleDot: '..';
Dot: '.';

OpenBrace: '{';
CloseBrace: '}';
OpenPara: '(';
ClosePara: ')';
OpenBracket: '[';
CloseBracket: ']';
Equals: '=';
SemiColon: ';';
Then: 'then';
End: 'end';

BitOr: '|';
BitAnd: '&';

Plus: '+';
Minus: '-';
Multiply: '*';
Divide: '/';
Modulus: '%';

Struct: 'struct';
Trait: 'trait';
Val: 'val';
Var: 'var';

With: 'with';

For: 'for';
Impl: 'impl';

Ref: 'ref';
Return: 'return';

Qualifier: 'qualifier';
As: 'as';

Nominal: 'nominal';

Bang: '!';

Comma: ',';
Colon: ':';

New: 'new';

LTE: '<=';
GTE: '>=';

LT: '<';
GT: '>';

If: 'if';
Else: 'else';

ArrowDouble: '=>';
ArrowSingle: '->';

Identifier
    : IdentifierStart IdentifierPart*
    ;

// Number literals

NumericLiteral
    : DecimalLiteral
    | HexIntegerLiteral
    | OctalIntegerLiteral
    | OctalIntegerLiteral2
    | BinaryIntegerLiteral
    ;

IntegerLiteral
    : DecimalIntegerLiteral
    ;

DecimalLiteral
    : DecimalIntegerLiteral '.' [0-9]+ ExponentPart?
    //| '.' [0-9]+ ExponentPart?
    | DecimalIntegerLiteral ExponentPart?
    ;

HexIntegerLiteral
    : '0' [xX] HexDigit+
    ;
OctalIntegerLiteral
    : '0' [0-7]+
    ;
OctalIntegerLiteral2
    : '0' [oO] [0-7]+
    ;
BinaryIntegerLiteral
    : '0' [bB] [01]+
    ;

fragment ExponentPart
    : [eE] [+-]? [0-9]+
    ;

fragment DecimalIntegerLiteral
    : '0'
    | [1-9] [0-9]*
    ;

// String literals

StringLiteral
    : ('"' DoubleStringCharacter* '"'
    | '\'' SingleStringCharacter* '\'')
    ;

fragment DoubleStringCharacter
    : ~["\\\r\n]
    | '\\' EscapeSequence
    | LineContinuation
    ;

fragment SingleStringCharacter
    : ~['\\\r\n]
    | '\\' EscapeSequence
    | LineContinuation
    ;

fragment EscapeSequence
    : CharacterEscapeSequence
    | '0' // no digit ahead! TODO
    | HexEscapeSequence
    | UnicodeEscapeSequence
    | ExtendedUnicodeEscapeSequence
    ;

fragment CharacterEscapeSequence
    : SingleEscapeCharacter
    | NonEscapeCharacter
    ;

fragment HexEscapeSequence
    : 'x' HexDigit HexDigit
    ;

fragment UnicodeEscapeSequence
    : 'u' HexDigit HexDigit HexDigit HexDigit
    ;

fragment ExtendedUnicodeEscapeSequence
    : 'u' '{' HexDigit+ '}'
    ;

fragment SingleEscapeCharacter
    : ['"\\bfnrtv]
    ;

fragment NonEscapeCharacter
    : ~['"\\bfnrtv0-9xu\r\n]
    ;

fragment EscapeCharacter
    : SingleEscapeCharacter
    | [0-9]
    | [xu]
    ;

fragment LineContinuation
    : '\\' [\r\n\u2028\u2029]
    ;

fragment HexDigit
    : [0-9a-fA-F]
    ;

fragment IdentifierPart
    : IdentifierStart
    | [\p{Mn}]
    | [\p{Nd}]
    | [\p{Pc}]
    | '\u200C'
    | '\u200D'
    ;

fragment IdentifierStart
    : [\p{L}]
    | [$_]
    | '\\' UnicodeEscapeSequence
    ;

root
    : statement*
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
    : expression
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
    : Identifier genericSignature? OpenPara argumentList? ClosePara
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
    : NumericLiteral    # numericLiteral
    | StringLiteral     # stringLiteral
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


