parser grammar PlangParser;

options {
    tokenVocab=PlangLexer;
}

program
    : expr* EOF
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
    : rtChain (Colon Identifier)?
    | newSpec
    ;

withScope
    : With OpenParen? withScopeEntry (Comma withScopeEntry)* Comma? CloseParen? expr
    ;

iterationSpec
    : Do expr While conditionalPredicate                                                                    # doIteration
    | While conditionalPredicate expr                                                                       # whileIteration
    | ForEach identifier In source=expr body=expr                                           # forEachIteration
    | ForEach source=expr body=expr                                                         # forEachIteration
    | For OpenParen expressionList? SemiColon conditionalPredicate? SemiColon expressionList? CloseParen expr # forIteration
    ;

keyword
    : Return
    | Type
    | It
    ;

metaIdentifierIndex
    : OpenBracket NumericLiteral CloseBracket
    ;

metaIdentifier
    // TODO: Not care while parsing, or including only allowed meta words?
    : (keyword | Identifier) metaIdentifierIndex?
    ;

metaSpec
    : Meta metaIdentifier (Dot metaIdentifier)*
    ;

matchTupleItem
    : Identifier Colon expr   # matchTupleDeclaration
    | expr                    # matchTupleItemExpression
    | DoubleDot                     # matchTupleSpacer
    ;

matchTuple
    : OpenParen matchTupleItem (Comma matchTupleItem)+ Comma? CloseParen
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
    | expr
    | Underscore
    ;

matchItem
    : matchCase ArrowDouble expr             # standardMatch
    ;

matchList
    : matchItem (Comma matchItem)* Comma?
    ;

matchSpec
    : Match expr OpenBrace matchList CloseBrace
    ;

varDeclareList
    : variablePrefix variableDeclarationBody (Comma variablePrefix? variableDeclarationBody)* Comma?
    ;

conditionalPredicate
    : expr
    ;

conditionalElse
    : Else expr
    ;

conditional
    //: If varDeclareList expr (Else expr)?
    : If expr expr conditionalElse?
    ;

bracketExpressionSpec
    : OpenBracket accessor=expr CloseBracket
    ;

collectionItem
    : expr (Assign expr)?
    ;

collectionItemList
    : collectionItem (Comma collectionItem)* Comma?
    ;

parenExprList
    : OpenParen expr (Comma expr)* Comma? CloseParen
    ;

word
    : identifier
    | literal
    | It
    ;

newSpec
    : New expr OpenBrace constructionArgList? CloseBrace
    ;

funcBody
    : expr
    ;

funcImpl
    : ArrowDouble funcBody
    ;

expr
    : OpenBrace expr* CloseBrace                        # blockExpr
    | Export Default? expr                              # exportExpression
    | Import importPath                                 # importExpression
    | Bang expr                                         # notExpression
    | Become callSpec                                   # becomeExpression
    | Yield expr                                        # yieldExpression
    | Type genericSignature? expr                       # typeSpecExpression
    | Nominal expr                                      # nominalTypeExpression
    | Symbol                                            # symbolTypeExpression
    | typeSpec                                          # typeExpr
    | Return expr                                       # returnExpression
    | Then expr                                         # thenExpression
    | funcImpl                                          # shorthandFuncExpr
    | newSpec                                           # newExpr
    | matchSpec                                         # matchExpression
    | structSpec                                        # structExpression
    | traitSpec                                         # traitExpression
    | implSpec                                          # implExpression
    | conditional                                       # conditionalExpr
    | withScope                                         # withExpr
    | iterationSpec                                     # iterationExpr
    | varDeclareList                                    # varDeclareExpr

    | functionality                                     # functionalityExpr

    | eos                                               # eosExpr
    ;

typeTarget
    : expr
    ;

// TODO: "Path" is too generic. Need to versions of it
//          One path that is for specifying compile-time constants, and one for runtime and one for both
compPath
    : word
    | metaSpec
    | owner=compPath Dot child=compPath
    | owner=compPath genericArgumentSupplier
    ;

rtChain
    : word
    | owner=rtChain (Dot member=thing | bracketExpressionSpec | genericArgumentSupplier)
    ;

scopeVariable
    : Identifier
    | It
    ;

scopeOwner
    : Identifier
    ;

scopeTarget
    : scopeVariable Meta scopeOwner
    ;

thing
    : callSpec
    | scopeTarget
    | rtChain
    ;

thingOrParen
    : thing
    | OpenParen thingOrParen CloseParen
    ;

functionality
    : lhs=binaryAssignOrLogicalOrMath (Assign rhs=binaryAssignOrLogicalOrMath)*
    ;

binaryAssignOrLogicalOrMath
    : lhs=binaryOpLogicalOrMath ((Equals | LTE | GTE | LT | GT | Is) rhs=binaryOpLogicalOrMath)*
    ;

binaryOpLogicalOrMath
    : lhs=binaryOpMath ((And | Or) rhs=binaryOpMath)*
    ;

binaryOpMath
    : lhs=binaryOpMulDiv ((Plus | Minus) Tilde? rhs=binaryOpMulDiv)*
    ;

binaryOpMulDiv
    : lhs=binaryOpOther ((Multiply | Divide) Tilde? rhs=binaryOpOther)*
    ;

binaryOpOther
    : lhs=binaryRange ((Pow | Modulus | Remainder | BitShiftLeft | BitShiftRight | BitAnd | BitOr) Tilde? rhs=binaryDot)*
    ;

binaryRange
    : lhs=binaryDot (DoubleDot rhs=binaryDot)*
    ;

binaryDot
    : lhs=binaryCall (Dot QuestionMark? rhs=binaryCall)*
    ;

binaryCall
    : lhs=binaryBracketAccessor (callBody QuestionMark?)*
    ;

binaryBracketAccessor
    : lhs=primaryExpr collectionExpr*
    ;

primaryExpr
    : literal
    | metaSpec
    | identifierExpr
    | primaryFuncDeclaration
    | parenExprList
    | escapedKeyword
    | collectionExpr
    ;

primaryFuncDeclaration
    : funcSignature funcImpl?
    | funcSignatureParameter funcImpl
    ;

identifierScopeSpecifierExpr
    : Meta Identifier
    ;

identifierExpr
    : (It | GenericIdentifier | Identifier | metaSpec) identifierScopeSpecifierExpr? genericArgumentSupplier?
    ;

collectionExpr
    : OpenBracket collectionItemList? CloseBracket
    ;

funcSignatureParameter
    : Ref? (Val | Var)? identifierExpr typeSpecifier?
    ;

 funcSignatureParameters
    : funcSignatureParameter (Comma funcSignatureParameter)*
    ;

funcGenericRequirements
    : Where genericSignatureTypeList
    ;

funcSignature
    : OpenParen funcSignatureParameters? CloseParen typeSpecifier? funcGenericRequirements?
    ;

escapedKeyword
    : OpenBracket keyword CloseBracket
    ;

argumentItem
    : (identifierExpr Assign)? expr
    ;

argumentList
    : argumentItem (Comma argumentItem)* Comma?
    ;

typeSpecifier
    : Colon Super? typeTarget
    ;

initializer
    : Assign expr
    ;

whereSpec
    : Where conditionalPredicate
    ;

variablePrefix
    : Val
    | Var
    ;

// TODO: Move the "whereSpec" to some expression that signifies "creation"
//          That way it can be abstracted away from this specific place and used
//          anywhere for anything that creates or return something, to use as a filter of sorts.
variableDeclarationBody
    : identifier typeSpecifier? (initializer whereSpec?)?
    ;

varDeclareSpec
    : variablePrefix variableDeclarationBody
    ;

expressionList
    : expr (Comma expr)* Comma?
    ;

delimitedExpressionList
    : expr (SemiColon expr)* SemiColon?
    ;

// TODO: This should be so much more, like "extends" or "super" or other conditions
genericSignatureType
    : (In | Out)? GenericIdentifier hktGenericSignature? typeSpecifier? initializer?
    ;

genericSignatureTypeList
    : genericSignatureType (Comma genericSignatureType)* Comma?
    ;

genericSignature
    : LT genericSignatureTypeList GT
    ;

hktGenericSignatureType
    : (Infer | Derive) GenericIdentifier hktGenericSignature? typeSpecifier? initializer?
    ;

hktGenericSignatureTypeList
    : hktGenericSignatureType (Comma hktGenericSignatureType)* Comma?
    ;

hktGenericSignature
    : LT hktGenericSignatureTypeList GT
    ;

genericArgumentSupplier
    : LT argumentItem (Comma argumentItem)* Comma? GT
    ;

callBody
    : genericArgumentSupplier? Tilde? OpenParen argumentList? CloseParen
    ;

callSpec
    : identifierExpr callBody
    ;

assignSpec
    : identifier Assign expr
    ;

constructionArg
    : assignSpec
    | expr
    ;

constructionArgList
    : constructionArg (delimiter constructionArg)* delimiter?
    ;

// NOTE: This is overriden by PlangParserJava to support ${placeholders}
stringLiteralSpec
    : StringLiteral
    ;

literal
    : stringLiteralSpec     # stringLiteral
    | NumericLiteral    # numericLiteral
    | BooleanLiteral    # booleanLiteral
    ;

delimiter
    : (Comma | SemiColon)
    ;

structSpec
    : Struct OpenBrace delimitedExpressionList? CloseBrace
    ;

traitChild
    : varDeclareSpec
    ;

traitSpec
    : Trait OpenBrace traitChild (SemiColon traitChild)* SemiColon? CloseBrace
    ;

// TODO: This seems really bad! It does not "blend" well with the rest of the language
//          Need some way of representing enums that is just another type, and some way of declaring types
typeChild
    : identifierExpr typeSpecifier?
    ;
typeChildren
    : typeChild (BitOr typeChild)* BitOr?
    ;
typeSpec
    : Type OpenBrace typeChildren? CloseBrace funcGenericRequirements?
    ;

implContextParameter
    : Identifier Colon Identifier
    ;

implContextParameterList
    : implContextParameter (Comma implContextParameter)* Comma?
    ;

implFor
    : expr
    ;

implSpec
    : Impl identifier? For implFor (With implContextParameterList)? OpenBrace expr* CloseBrace
    ;

identifier
    : GenericIdentifier
    | Identifier
    | metaSpec
    ;
