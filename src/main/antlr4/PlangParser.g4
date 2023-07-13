parser grammar PlangParser;

options {
    tokenVocab=PlangLexer;
}

root
    : expression* EOF
    ;

importSegment
    : Multiply
    | OpenBrace importPath (Comma importPath)* CloseBrace
    | Identifier (Dot importSegment)?
    ;

importAlias
    : Identifier Colon
    ;

importPath
    : importAlias? importSegment
    ;

eos
    // This should be possible to be EOF or close brace or other contextual to get rid of ";" eventually
    : (SemiColon | Comma | End)
    ;

withScopeEntry
    : expression (Colon Identifier)?
    ;

withScope
    : With OpenPara? withScopeEntry (Comma withScopeEntry)* Comma? ClosePara? expression
    ;

parameter
    : accessLevel? (Val | Var)? (metaSpec | identifier) typeSpecifier?
    ;

parameterList
    : parameter (Comma parameter)* Comma?
    ;

forSpec
    : For OpenPara expressionList? SemiColon expression? SemiColon expressionList? ClosePara expression
    ;

// TODO: Figure out if it's possible to abstract the foreach-lop and its variable declaration!
//          Like make the "in/of expr" into an "iterator creator" for values/keys
//          Then make foreach signature just an "expr" so could write things like:
//          foreach array print(it);
//iteratorSpec
//    : (In | Of) expression
//    ;

//forEachSignatureSpec
//    : (varDeclareSpec | metaSpec | identifier) (In | Of) expression
//    ;

// TODO: Remove the varDeclareSpec !! It must be liked any other expression!
//              It is a variable declaration whose type is inferred by external factors (this case from "in" source)
forEachSpec
    : ForEach (metaSpec | identifier) In source=expression body=expression
    | ForEach source=expression body=expression
//    : ForEach OpenPara forEachSignatureSpec ClosePara expression
//    | ForEach forEachSignatureSpec expression
    ;

iterationSpec
    : Do expression While expression       # doIteration
    | While expression expression          # whileIteration
    | forEachSpec                          # forEachIteration
    | forSpec                                   # forIteration
    ;

tupleSpec
    : OpenPara expression (Comma expression)+ Comma? ClosePara
    ;

mapEntryPair
    : expression Assign expression
    ;

mapEntryList
    : mapEntryPair (Comma mapEntryPair)* Comma?
    ;

groupedExpressionSpec
    : OpenPara expression ClosePara
    ;

keyword
    : Return
    | Type
    ;

metaIdentifierIndex
    : OpenBracket NumericLiteral CloseBracket
    ;

metaIdentifier
    // TODO: Not care while parsing, or including only allowed meta words?
    : (keyword | Identifier) metaIdentifierIndex?        //# indexedMetaIdentifier
    //| (keyword | Identifier)                                                # simpleMetaIdentifier
    ;

metaSpec
    : Meta metaIdentifier (Dot metaIdentifier)*
    ;

matchTupleItem
    : Identifier Colon expression   # matchTupleDeclaration
    | expression                    # matchTupleItemExpression
    | DoubleDot                     # matchTupleSpacer
    ;

matchTuple
    : OpenPara matchTupleItem (Comma matchTupleItem)+ Comma? ClosePara
    ;

namedMatchArrayItemSpec
    : identifier Colon matchArrayItem
    ;

matchArrayItem
    : matchCase                             # matchCaseArrayItemEntry
    | DoubleDot                             # matchCaseSpacer
    | namedMatchArrayItemSpec               # namedMatchArrayItem
    ;

matchArrayItemList
    : matchArrayItem (delimiter matchArrayItem)* delimiter?
    ;

matchArrayItemsSpec
    : OpenBracket matchArrayItemList? CloseBracket
    ;

matchCase
    : matchTuple
    | matchArrayItemsSpec
    // Parsing might be a well-formed expression but not-valid.
    | expression
    | Underscore
    ;

matchItem
    : matchCase ArrowDouble expression             # standardMatch
    ;

matchList
    : matchItem (Comma matchItem)* Comma?
    ;

matchSpec
    : Match expression OpenBrace matchList CloseBrace
    ;

ifSpec
    : If predicate=expression expression (Else expression)?
    ;

funcSpec
    : ArrowDouble expression
    | OpenPara parameter ClosePara ArrowDouble expression
    | parameter ArrowDouble expression
    | genericSignature? OpenPara parameterList? ClosePara typeSpecifier? (ArrowDouble expression)?
    ;

dotExpressionSpec
    : Dot expression
    ;

bracketExpressionSpec
    : OpenBracket accessor=expression CloseBracket
    ;

expression

    // First expressions with keywords
    : Export Default? expression eos?                                           # exportExpression
    | Import importPath eos?                                                    # importExpression

    | Bang expression                                                           # notExpression
    | Become callSpec                                                           # becomeExpression
    | Yield expression eos?                                                     # yieldExpression
    | Type genericSignature? expression                                         # typeSpecExpression
    | Nominal identifierPath                                                    # nominalTypeExpression
    | Symbol                                                                    # symbolTypeExpression
    | Type expression                                                           # explicitTypeExpression
    | Return expression eos?                                                    # returnExpression
    | Then expression                                                           # thenExpression
    | literal                                                                   # literalExpression
    | matchSpec                                                                 # matchExpression
    | structSpec                                                                # structExpression
    | traitSpec                                                                 # traitExpression
    | implSpec eos?                                                             # implExpression
    | ifSpec                                                                    # ifExpression
    | withScope                                                                 # withExpression
    | iterationSpec eos?                                                        # iterationExpression
    | varDeclareList eos?                                                       # variableDeclarationListExpression
//    | iteratorSpec                                                              # iteratorExpression
    | New expression OpenBrace constructionArgList? CloseBrace                  # constructionExression

    // Then expressions with special characters
    | OpenBrace expression* eos? CloseBrace                                     # blockExpression
    | expression genericArgumentSupplier                                        # genericSpecifierExpression
    | funcSpec eos?                                                             # funcExpression

    | tupleSpec                                                                 # tupleExpression
    | callSpec eos?                                                             # callExpression

    | TripleDot expression                                                      # destructureExpression

    | lhs=expression Assign rhs=expression eos?                                 # assignExpression
    | lhs=expression booleanOperator rhs=expression                             # binaryExpression
    | lhs=expression logicalOperator rhs=expression                             # binaryExpression
    | lhs=expression mathOperator Tilde? rhs=expression                         # binaryExpression

    | owner=expression (dotExpressionSpec | bracketExpressionSpec)              # accessorExpression

    | from=expression DoubleDot to=expression                                   # rangeExpression

    // Note: Will be a map if using AssignExpression inside
    | OpenBracket expressionList? CloseBracket                                  # collectionExpression
    | metaSpec                                                                  # metaExpression
    | Identifier Meta Identifier                                                # scopeSpecificIdentifierExpression
    | Identifier Colon expression                                               # taggedExpression

    | groupedExpressionSpec                                                     # groupedExpression
    | Identifier                                                                # identifierExpression
    ;

typeSpecifier
    // TODO: This is too forgiving, not any expression should be acceptable. It makes the parsing slow.
    // TODO: Up to later parsing to decide if the expression is legal or not (must be constant, etc)
    : Colon (metaSpec | expression)
    ;

accessLevel
    : Ref
    ;

// TODO: Support named arguments
argumentList
    : expression (Comma expression)* Comma?
    ;

logicalOperator
    : And
    | Or
    ;

mathOperator
    : Plus
    | Minus
    | Multiply
    | Divide
    | Modulus
    | Remainder
    | BitShiftLeft
    | BitShiftRight
    | BitAnd
    | BitOr
    ;

booleanOperator
    : Equals
    | LTE
    | GTE
    | LT
    | GT
    | Is
    ;

initializer
    : Assign expression
    ;

variableDeclarationFilter
    : Where expression
    ;

// TODO: Remove this! It should be a normal expression! It is a variable declaration with externally deciding type!
varDeclareSpec
    : (Val | Var) (metaSpec | identifier) typeSpecifier? initializer? variableDeclarationFilter?
    ;

varDeclareList
    : varDeclareSpec (Comma varDeclareSpec)* Comma?
    ;

expressionList
    : expression (Comma expression)* Comma?
    ;

delimitedExpressionList
    : expression (SemiColon expression)* SemiColon?
    ;

genericSignatureTypeNarrower
    // If super, then restrict incoming type to that supertype.
    // Argument of type Iterable<Number> does not allow an Iterable<uint32>
    : Colon Super? expression
    ;

// TODO: This should be so much more, like "extends" or "super" or other conditions
genericSignatureType
    : (In | Out)? Identifier hktGenericSignature? genericSignatureTypeNarrower? initializer?
    ;

genericSignatureTypeList
    : genericSignatureType (delimiter genericSignatureType)* delimiter?
    ;

genericSignature
    : LT genericSignatureTypeList GT
    ;

hktGenericSignatureType
    : (Infer | Derive) Identifier hktGenericSignature? genericSignatureTypeNarrower? initializer?
    ;

hktGenericSignatureTypeList
    : hktGenericSignatureType (Comma hktGenericSignatureType)* Comma?
    ;

hktGenericSignature
    : LT hktGenericSignatureTypeList GT
    ;

genericArgument
    : identifier Assign expression        # namedGenericArgument
    | expression                          # indexedGenericArgument
    ;

genericArguments
    : genericArgument (Comma genericArgument)* Comma?
    ;

genericArgumentSupplier
    : LT genericArguments GT
    ;

callSpec
    : Tilde Identifier genericArgumentSupplier? OpenPara argumentList? ClosePara QuestionMark?  # partialCall
    | Identifier genericArgumentSupplier? OpenPara argumentList? ClosePara QuestionMark?        # directCall
    ;

constructionArgList
    : expression (delimiter expression)* delimiter?
    ;

literal
    : StringLiteral     # stringLiteral
    | NumericLiteral    # numericLiteral
    | BooleanLiteral    # booleanLiteral
    ;

delimiter
    : (Comma | SemiColon)
    ;

structSpec
    : Struct OpenBrace delimitedExpressionList? CloseBrace
    ;

traitSpec
    : Trait OpenBrace delimitedExpressionList? CloseBrace
    ;

implContextParameter
    : Identifier Colon Identifier
    ;

implContextParameterList
    : implContextParameter (Comma implContextParameter)* Comma?
    ;

identifierPath
    : Identifier (Dot Identifier)*
    ;

implSpec
    : Impl identifier? For expression (With implContextParameterList)? OpenBrace expression* CloseBrace
    ;

identifier
    : Identifier
    ;
