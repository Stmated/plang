lexer grammar PlangLexer;

channels { ERROR, COMMENT }

MultiLineComment
    : '/*' .*? '*/' -> channel(COMMENT)
    ;
SingleLineComment
    : '//' ~[\r\n\u2028\u2029]* -> channel(COMMENT)
    ;

OpenBracket: '[';
CloseBracket: ']';

OpenPara: '(';
ClosePara: ')';

OpenBrace: '{';
CloseBrace: '}';
TemplateCloseBrace:  '}';

SemiColon: ';';

Tilde: '~';

TripleDot: '...';
DoubleDot: '..';
Underscore: '_';
Dot: '.';

Then: 'then';
End: 'end';

ArrowDouble: '=>';
ArrowSingle: '->';

Plus: '+';
Minus: '-';
Multiply: '*';
Divide: '/';
Modulus: '%%';
Remainder: '%';
BitShiftLeft: '<<';
BitShiftRight: '>>';
LTE: '<=';
GTE: '>=';
Equals: '==';
LT: '<';
GT: '>';

Or: '||';
And: '&&';

BitOr: '|';
BitAnd: '&';

QuestionMark: '?';

Assign: '=';

Bang: '!';

Comma: ',';
Colon: ':';

Struct: 'struct';
Trait: 'trait';
Val: 'val';
Var: 'var';

Where: 'where';
With: 'with';

For: 'for';
ForEach: 'foreach';
While: 'while';
Do: 'do';
In: 'in';
Out: 'out';
Of: 'of';

Infer: 'infer';
Derive: 'derive';

Super: 'super';

Impl: 'impl';

Ref: 'ref';
Return: 'return';
Yield: 'yield';
Become: 'become';

Meta: '@';

Qualifier: 'qualifier';
Is: 'is';
As: 'as';

Type: 'type';
Nominal: 'nominal';
Symbol: 'symbol';

BooleanLiteral
    : BooleanLiteralTrue
    | BooleanLiteralFalse
    ;

Import: 'import';
Export: 'export';
Default: 'default';

New: 'new';

Match: 'match';

If: 'if';
Else: 'else';

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

// String literals

StringLiteral
    : ('`' BackTickStringCharacter* '`'
    | '"' DoubleStringCharacter* '"'
    | '\'' SingleStringCharacter* '\'')
    ;

WhiteSpaces
    : [\t\u000B\u000C\u0020\u00A0]+ -> channel(HIDDEN)
    ;

LineTerminator
    : [\r\n\u2028\u2029] -> channel(HIDDEN)
    ;

UnexpectedCharacter
    : . -> channel(ERROR)
    ;

fragment TickStringCharacter
    : ~[`\\\r\n]
    | '\\' EscapeSequence
    | LineContinuation
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

fragment BackTickStringCharacter
    : ~[`\\\r\n]
    | '\\' EscapeSequence
    | LineContinuation
    ;

fragment ExponentPart
    : [eE] [+-]? [0-9]+
    ;

fragment DecimalIntegerLiteral
    : '0'
    | [1-9] [0-9]*
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

fragment BooleanLiteralTrue
    : 'true'
    ;

fragment BooleanLiteralFalse
    : 'false'
    ;
