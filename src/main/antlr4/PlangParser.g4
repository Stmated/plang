parser grammar PlangParser;

options {
    tokenVocab=PlangLexer;
}

root
    : expression* EOF
    ;

simplePathSegment
    : Identifier
    | Multiply
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

eos
    // This should be possible to be EOF or close brace or other contextual to get rid of ";" eventually
    : SemiColon
    | Comma
    | End
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

// TODO: Remove the varDeclareSpec !! It must be liked any other expression!
//              It is a variable declaration whose type is inferred by external factors (this case from "in" source)
forEachSpec
    : ForEach OpenPara (expression | varDeclareSpec) (In | Of) expression ClosePara expression
    | ForEach (expression | varDeclareSpec) (In | Of) expression expression
    ;

iterationSpec
    : Do expression While expression eos?        # doIteration
    | While expression expression eos?           # whileIteration
    | forEachSpec eos?                           # forEachIteration
    | forSpec                               # forIteration
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

metaIdentifier
    // TODO: Not care while parsing, or including only allowed meta words?
    : (keyword | Identifier) OpenBracket NumericLiteral CloseBracket        # indexedMetaIdentifier
    | (keyword | Identifier)                                                # simpleMetaIdentifier
    ;

metaSpec
    : Meta metaIdentifier (Dot metaIdentifier)*
    ;

matchTupleItem
    : identifier Colon expression   # matchTupleDeclaration
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
    // The standalone expression must be a constant. Parsing might be well-formed but not-valid.
    | expression
    | Underscore
    ;

matchItem
    //: Else ArrowDouble expression                                          # exhaustiveMatch
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

// NOTE: Goal is to make EVERYTHING an expression -- everything should return a value
//          Should make it easier to create common patterns for things
expression

    // First expressions with keywords
    : Export Default? expression eos?                                                # exportExpression
    | Import importTree eos?                                                        # importExpression

    | Bang expression                                                           # notExpression
    | Become callSpec                                                               # becomeExpression
    | Yield expression eos?                                                          # yieldExpression
    | Type genericSignature? expression                                         # typeSpecExpression
    | Nominal identifierPath                                                    # nominalTypeExpression
    | Symbol                                                                    # symbolTypeExpression
    | Type expression                                                           # explicitTypeExpression
    | Return expression eos?                                                         # returnExpression
    | Then expression                                                           # thenExpression
    | matchSpec                                                                 # matchExpression
    | structSpec                                                                # structExpression
    | traitSpec                                                                 # traitExpression
    | implSpec eos?                                                                  # implExpression
    | ifSpec                                                                    # ifExpression
    | withScope                                                                 # withExpression
    | iterationSpec                                                             # iterationExpression
    | explicitVariableDeclarationList                                           # variableDeclarationListExpression

    // Then expressions with special characters
    | OpenBrace expression* eos? CloseBrace                                          # blockExpression
    | expression genericArgumentSupplier                                        # genericSpecifierExpression
    | genericSignature OpenPara parameterList? ClosePara typeSpecifier? ArrowDouble expression      # genericLambda
    | ArrowDouble expression                                                                        # shortLambda
    | parameter ArrowDouble expression                                                              # oneArgLambda
    | OpenPara parameterList? ClosePara typeSpecifier? ArrowDouble expression                       # lambda
    | OpenPara parameterList? ClosePara typeSpecifier                           # functionSignature
    | tupleSpec                                                                 # tupleInstantiationExpression
    | callSpec eos?                                                                      # callExpression
    | expression OpenBrace constructionArgList? CloseBrace            # constructionExression

    | TripleDot expression                                                      # destructureExpression

    | lhs=expression Assign rhs=expression eos?                                      # assignExpression
    | lhs=expression booleanOperator rhs=expression                             # binaryExpression
    | lhs=expression logicalOperator rhs=expression                             # binaryExpression
    | lhs=expression mathOperator Tilde? rhs=expression                         # binaryExpression
    | owner=expression (Dot expression)+                                        # dotMemberPathExpression
    | owner=expression OpenBracket accessor=expression CloseBracket             # expressionAccessor
    | from=expression DoubleDot to=expression                                   # rangeExpression
    | OpenBracket mapEntryList CloseBracket                                     # mapCreationExpression
    | OpenBracket expressionList? CloseBracket                                  # arrayCreationExpression
    | metaSpec                                                                  # metaExpression
    | Identifier Meta Identifier                                                # scopeSpecificIdentifierExpression
    | Identifier Colon expression                                               # taggedExpression
    | literal                                                                   # literalExpression
    | groupedExpressionSpec                                                     # groupedExpression
    | identifier                                                                # identifierExpression
    //| eos                                                                       # eosExpression
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
    : Val (metaSpec | identifier) typeSpecifier? initializer? variableDeclarationFilter? eos?
    | Var (metaSpec | identifier) typeSpecifier? initializer? variableDeclarationFilter? eos?
//    | Var (metaSpec | identifier) typeSpecifier initializer? variableDeclarationFilter? eos?
//    | Var (metaSpec | identifier) typeSpecifier? initializer variableDeclarationFilter? eos?
//    | Val (metaSpec | identifier) eos?
//    | Var (metaSpec | identifier) eos?
    ;

explicitVariableDeclarationList
    : varDeclareSpec (Comma varDeclareSpec)* Comma?
    ;

expressionList
    : expression (delimiter expression)* delimiter?
    ;

genericSignatureTypeNarrower
    // If super, then restrict incoming type to that supertype.
    // Argument of type Iterable<Number> does not allow an Iterable<uint32>
    : Colon Super? expression
    ;

genericSignatureTypeDefault
    : Assign expression
    ;

// TODO: This should be so much more, like "extends" or "super" or other conditions
genericSignatureType
    : (In | Out)? Identifier hktGenericSignature? genericSignatureTypeNarrower? genericSignatureTypeDefault?
    ;

genericSignatureTypeList
    : genericSignatureType (delimiter genericSignatureType)* delimiter?
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

keyValuePair
    : Identifier Assign expression
    ;

constructionArg
    : keyValuePair
    | expression
    ;

constructionArgList
    : constructionArg (delimiter constructionArg)* delimiter?
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
    : Struct OpenBrace expressionList? CloseBrace
    ;

traitSpec
    : Trait OpenBrace expressionList? CloseBrace
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
