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

standaloneStatement
    : block
    | withScope
    | iterationStatement
    | ifStatement
    | Export Default? statement
    ;

simplePathSegment
    : identifier
    | '*'
    ;

simplePath
    : simplePathSegment (Dot simplePathSegment)*
    ;

importGroup
    : OpenBrace importTree (delimiter importTree)* delimiter? CloseBrace
    ;

groupedImport
    : simplePath Dot importGroup
    ;

aliasedImport
    : identifier Colon simplePath
    ;

importTree
    : simplePath
    | groupedImport
    | aliasedImport
    ;

importDeclaration
    : Import importTree
    ;

eosStatement
    : expression
    | variableDeclaration
    | importDeclaration
    ;

statement
    : standaloneStatement
    | eosStatement eos
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
    : With OpenPara? withScopeEntry (Comma withScopeEntry)* Comma? ClosePara? block
    ;

parameter
    : accessLevel? valVar? identifier typeSpecifier?
    ;

parameterList
    : parameter (Comma parameter)* Comma?
    ;

functionSignature
    : OpenPara parameterList? ClosePara typeSpecifier
    ;

iterationStatement
    : Do (standaloneStatement | expression) While OpenPara? expression ClosePara? eos                     # DoStatement
    | While OpenPara? expression ClosePara? (statement | expression)                                                # WhileStatement
    | For OpenPara variableDeclarationList? SemiColon expression? SemiColon expressionList? ClosePara statement     # ForEachStatement
    | ForEach OpenPara? (expression | variableDeclaration) (In | Of) expression ClosePara? statement                # ForInStatement
    ;

thener
    : Then expression
    ;

tuple
    : OpenPara expression (Comma expression)+ Comma? ClosePara
    ;

mapEntryPair
    : expression Assign expression
    ;

mapEntryList
    : mapEntryPair (Comma mapEntryPair)* Comma?
    ;

typeSpec
    : Type genericSignature? OpenBrace type CloseBrace
    | Type genericSignature? type
    ;

// NOTE: Goal is to make EVERYTHING an expression -- everything should return a value
//          Should make it easier to create common patterns for things
expression
    : function                                                          # functionExpression
    | tuple                                                             # tupleExpression
    | call                                                              # callExpression
    | OpenPara expression ClosePara                                     # groupedExpression
    | owner=expression Dot QuestionMark? member=expression              # dotExpression
    | lhs=expression operator rhs=expression                            # binaryExpression
    | owner=expression OpenBracket accessor=expression CloseBracket     # expressionAccessor
    | TripleDot expression                                              # destructureExpression
    | from=expression DoubleDot to=expression                           # rangeExpression
    | OpenBracket mapEntryList CloseBracket                             # mapCreationExpression
    | OpenBracket expressionList? CloseBracket                          # arrayCreationExpression
    | construction                                                      # constructionExression
    | functionSignature                                                 # functionSignatureExpression
    | Bang expression                                                   # notExpression
    | Return expression                                                 # returnExpression
    | Yield expression                                                  # yieldExpression
    | typeSpec                                                          # typeSpecExpression
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
    : argument (Comma argument)* Comma?
    ;

basicOperatorSpec
    : Equals
    | Plus
    | Minus
    | Multiply
    | Divide
    | Modulus
    | Remainder
    | BitShiftLeft
    | BitShiftRight
    | LTE
    | GTE
    | LT
    | GT
    | Is
    ;

operator
    : basicOperatorSpec Tilde       # safeOperator
    | basicOperatorSpec             # basicOperator
    ;

initializer
    : Assign (type | expression)
    ;

variableDeclaration
    : valVar? identifier typeSpecifier? initializer?
    ;

variableDeclarationList
    : variableDeclaration (Comma variableDeclaration)* Comma?
    ;

expressionList
    : expression (delimiter expression)* delimiter?
    ;

elseStatement
    : Else (expression | statement)
    ;

ifStatement
    : If predicate=expression (thener | statement)? elseStatement?
    ;

genericSignatureTypeNarrower
    // If super, then restrict incoming type to that supertype.
    // Argument of type Iterable<Number> does not allow an Iterable<uint32>
    : Colon Super? type
    ;

genericSignatureTypeDefault
    : Assign type
    ;

// TODO: This should be so much more, like "extends" or "super" or other conditions
genericSignatureType
    : (In | Out)? Identifier hktGenericSignature? genericSignatureTypeNarrower? genericSignatureTypeDefault?
    ;

genericSignatureTypeList
    : genericSignatureType (Comma genericSignatureType)* Comma?
    ;

genericSignature
    : LT genericSignatureTypeList GT
    ;

hktGenericSignatureType
    : (Infer | Derive) Identifier hktGenericSignature? genericSignatureTypeNarrower? genericSignatureTypeDefault?
    ;

hktGenericSignatureTypeList
    : hktGenericSignatureType (Comma hktGenericSignatureType)* Comma?
    ;

hktGenericSignature
    : LT hktGenericSignatureTypeList GT
    ;

genericArgument
    : identifier Assign type        # namedGenericArgument
    | type                          # indexedGenericArgument
    ;

genericArguments
    : genericArgument (Comma genericArgument)* Comma?
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
    : quickConstructorEntry (delimiter quickConstructorEntry)* delimiter?
//    | quickConstructorEntry (SemiColon quickConstructorEntry)* SemiColon?
    ;

construction
    : type? OpenBrace quickConstructorEntryList? CloseBrace
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

delimiter
    : (Comma | SemiColon)
    ;

eosStatementList
    : eosStatement (delimiter eosStatement)* delimiter?
    ;

struct
    : Struct OpenBrace eosStatementList? CloseBrace
    ;

trait
    : Trait OpenBrace eosStatementList? CloseBrace
    ;

implContextParameter
    : Identifier Colon Identifier
    ;

implContextParameterList
    : implContextParameter (Comma implContextParameter)* Comma?
    ;

implContextDeclaration
    : With implContextParameterList
    ;

impl
    : Impl identifier For type implContextDeclaration? OpenBrace statement* CloseBrace
    ;

singleTypeSpec
    : Identifier                      # typeName
    | NumericLiteral                  # numericalType
    | Nominal Identifier              # nominalType
    | Symbol                          # symbolType
    | Identifier genericSignature     # genericType
    ;

tupleTypeSpec
    : OpenPara type (Comma type)* Comma? ClosePara
    ;

type
    : lhs=type BitOr rhs=type           # unionType
    | lhs=type Plus rhs=type            # intersectionType
    | lhs=type Minus rhs=type           # excludedType
    | tupleTypeSpec                     # tupleType // or groupedType if only has one type
    | Identifier Colon type             # taggedType
    | Type type                         # explicitType
    | singleTypeSpec                    # singleType
    ;

identifier
    : Identifier
    ;
