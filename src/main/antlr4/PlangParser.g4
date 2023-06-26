parser grammar PlangParser;

options {
    tokenVocab=PlangLexer;
}

root
    : statement* EOF
    ;

block
    : OpenBrace statement* CloseBrace
    ;

standaloneExecutableStatement
    : block
    | withScope
    | iterationStatement
    | ifStatement
    ;

statement
    : standaloneExecutableStatement
    | expression eos
    | variableDeclaration eos
    | Export Default? statement
    ;

eos
    // This should be possible to be EOF or close brace or other contextual to get rid of ";" eventually
    : SemiColon
    | End
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

ownerMember
    : (identifier | call);

iterationStatement
    : Do (standaloneExecutableStatement | expression) While OpenPara? expression ClosePara? eos                     # DoStatement
    | While OpenPara? expression ClosePara? (statement | expression)                                                # WhileStatement
    | For OpenPara variableDeclarationList? SemiColon expression? SemiColon expressionList? ClosePara statement     # ForEachStatement
    | ForEach OpenPara? (expression | variableDeclaration) (In | Of) expression ClosePara? statement                # ForInStatement
    ;

thener
    : Then expression
    ;

tuple
    : OpenPara expression (Comma expression)+ ClosePara
    ;

mapEntryPair
    : expression Assign expression
    ;

mapEntryList
    : mapEntryPair (Comma mapEntryPair)*
    ;

// NOTE: Goal is to make EVERYTHING an expression -- everything should return a value
//          Should make it easier to create common patterns for things
expression
    : function                                                          # functionExpression
    | tuple                                                             # tupleExpression
    | call                                                              # callExpression
    | OpenPara expression ClosePara                                     # groupedExpression
    | owner=expression Dot QuestionMark? member=ownerMember             # dotExpression
    | lhs=expression operator rhs=expression                            # binaryExpression
    | owner=expression OpenBracket accessor=expression CloseBracket     # expressionAccessor
    | from=expression DoubleDot to=expression                           # rangeExpression
    | OpenBracket mapEntryList CloseBracket                             # mapCreationExpression
    | OpenBracket expressionList? CloseBracket                          # arrayCreationExpression
    | construction                                                      # constructionExression
    | functionSignature                                                 # functionSignatureExpression
    | Bang expression                                                   # notExpression
    | Return expression                                                 # returnExpression
    | thener                                                            # thenExpression
    | ifStatement                                                       # ifStatementExpression
    | struct                                                            # structExpression
    | trait                                                             # traitExpression
    | impl                                                              # implExpression
    | literal                                                           # literalExpression
    | Identifier                                                        # identifierExpression
    ;


valVar
    : (Val | Var)
    ;

typeSpecifier
    // TODO: This is too forgiving, not any expression should be acceptable. It makes the parsing slow.
    // TODO: Up to later parsing to decide if the expression is legal or not (must be constant, etc)
    : Colon (type | expression)
    ;

accessLevel
    : Ref
    ;

argument
    : identifier Assign expression  # namedArgument
    | expression                    # indexedArgument
    ;

// TODO: Support named arguments
argumentList
    : argument (Comma argument)*
    ;

operator
    : Equals
    | Plus
    | Minus
    | Multiply
    | Divide
    | Modulus
    | LTE
    | GTE
    | LT
    | GT
    | QuestionMark QuestionMark
    ;

initializer
    : Assign (type | expression)
    ;

variableDeclaration
    : valVar? identifier typeSpecifier? initializer?
    ;

variableDeclarationList
    : variableDeclaration (Comma variableDeclaration)*
    ;

expressionList
    : expression (Comma expression)*
    ;

elseStatement
    : Else (expression | statement)
    ;

ifStatement
    : If predicate=expression (thener | statement)? elseStatement?
    ;

genericSignatureTypeNarrower
    : Colon type
    ;

genericSignatureTypeDefault
    : Assign type
    ;

// TODO: This should be so much more, like "extends" or "super" or other conditions
genericSignatureType
    : Identifier genericSignatureTypeNarrower? genericSignatureTypeDefault?
    ;

genericSignatureTypeList
    : genericSignatureType (Comma genericSignatureType)*
    ;

genericSignature
    : LT genericSignatureTypeList GT
    ;

genericArgument
    : identifier Assign type        # namedGenericArgument
    | type                          # indexedGenericArgument
    ;

genericArguments
    : genericArgument (Comma genericArgument)*
    ;

genericArgumentSupplier
    : LT genericArguments GT
    ;

call
    : Tilde? Identifier genericArgumentSupplier? OpenPara argumentList? ClosePara
    ;

keyValuePair
    : Identifier Assign expression
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
    : genericSignature? OpenPara parameterList? ClosePara typeSpecifier? ArrowDouble functionBody
    | parameter? ArrowDouble functionBody
    ;

literal
    : StringLiteral     # stringLiteral
    | NumericLiteral    # numericLiteral
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

impl
    : Impl identifier For type implContextDeclaration? OpenBrace statement* CloseBrace
    ;

type
    : type Plus type                  # unionType
    | type '|' type                   # intersectionType
    | type Minus type                 # notType
    | OpenPara type ClosePara         # groupedType
    | type genericSignature           # genericType
    | Identifier                      # typeName
    | NumericLiteral                  # numericalType
    | Nominal type                    # nominalType
    | Symbol identifier               # symbolType
    | tuple                           # tupleType
    ;

identifier
    : Identifier
    ;
