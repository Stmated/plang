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

//matchTupleItem
//    : Identifier Colon expr   # matchTupleDeclaration
//    | expr                    # matchTupleItemExpression
//    | DoubleDot                     # matchTupleSpacer
//    ;

//matchTuple
//    : OpenParen matchTupleItem (Comma matchTupleItem)+ Comma? CloseParen
//    ;

//namedMatchArrayItemSpec
//    : identifier Colon matchArrayItem
//    ;

//matchArrayItem
//    : matchCase                             # matchCaseArrayItemEntry
//    | DoubleDot                             # matchCaseSpacer
//    | namedMatchArrayItemSpec               # namedMatchArrayItem
//    ;

//matchArrayItemList
//    : matchArrayItem (delimiter matchArrayItem)* delimiter?
//    ;

//matchArrayItemsSpec
//    : OpenBracket matchArrayItemList? CloseBracket
//    ;

//matchCase
//    : matchTuple
//    | matchArrayItemsSpec
//    // Parsing might be a well-formed expression but not-valid.
//    | expr
//    | Underscore
//    ;

matchItem
    : expr ArrowDouble expr
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
    : (Identifier Colon)? expr (Assign expr)?
    ;

collectionItemList
    : collectionItem (Comma collectionItem)* Comma?
    ;

//parenExprList
//    : OpenParen collectionItemList CloseParen
//    ;

word
    : identifier
    | literal
    | It
    ;

newSpec
    : New expr OpenBrace constructionArgList? CloseBrace
    ;

funcImpl
    : ArrowDouble expr
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
    | typeSpec                                          # explicitTypeExpr
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
    : typeExpr
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
    | DoubleDot
    | Underscore
    | metaSpec
    | identifierExpr
    | tupleOrFunc
    //| parenExprList
    | escapedKeyword
    | collectionExpr
    ;

// Start of type main expression tree
// - This should mimic and share as much as possible with regular 'expr'
// - But there is a need to limit what can be written in type specifiers, or ambiguity arises

typeExpr
    : conditional // TODO: This needs to be a typeConditional that only allow type stuff inside it
    | typeFunctionality
    ;

//typeFunctionality
//    : lhs=typeBinaryAssignOrLogicalOrMath (Assign rhs=typeBinaryAssignOrLogicalOrMath)*
//    ;

typeFunctionality
    : lhs=typeBinaryOpMath ((Equals | Is) rhs=typeBinaryOpMath)*
    ;

typeBinaryOpMath
    : lhs=typeBinaryOpOther ((Plus | Minus) rhs=typeBinaryOpOther)*
    ;

typeBinaryOpOther
    : lhs=typeBinaryRange ((BitAnd | BitOr) rhs=typeBinaryDot)*
    ;

typeBinaryRange
    : lhs=typeBinaryDot (DoubleDot rhs=typeBinaryDot)*
    ;

typeBinaryDot
    : lhs=typeBinaryBracketAccessor (Dot rhs=typeBinaryBracketAccessor)*
    ;

typeBinaryBracketAccessor
    : lhs=typePrimaryExpr typeCollectionExpr*
    ;

typeCollectionItem
    : (Identifier Colon)? expr (Assign expr)?
    ;

typeCollectionItemList
    : typeCollectionItem (Comma typeCollectionItem)* Comma?
    ;

//parenTypeExprList
//    : OpenParen typeCollectionItemList CloseParen
//    ;

typeCollectionExpr
    : OpenBracket typeCollectionItemList? CloseBracket
    ;

typePrimaryExpr
    : literal
    | DoubleDot
    | Underscore
    | metaSpec
    | identifierExpr
    | tupleOrFuncSignature
    | escapedKeyword
    | typeCollectionExpr
    ;


// End of type main expression tree

// TODO: Work on this! Need to abstract the parsing so that the paranthesized part is just that
//          Then it becomes a func if it has ArrowDouble, or a signature if items inside have type specifiers.
//          Will make it easier to create syntax where we can pattern match lhs to rhs

// TODO: Remove this? And just have the "parenthesized collection" or whatever it is parse the content.
//          We're trying to parse fast here, and give meaning to it later.

// TODO: A "func" should just be a tuple with a typeSpecifier and/or ArrowDouble
// TODO: And a tuple is just a parenthesized expr collection where the entries are all types or tagged expressions

funcParameter
    : Ref? (Val | Var)? identifierExpr typeSpecifier? initializer?
    ;

tupleItem
    : funcParameter
    | literal
    | conditional
    //| expr
    ;

 tupleItemList
    : tupleItem (Comma tupleItem)* Comma?
    ;

tupleOrFuncSignature
    : OpenParen tupleItemList? CloseParen typeSpecifier? funcGenericRequirements?
    ;

tupleOrFunc
    : tupleOrFuncSignature funcImpl?
    //| funcParameter funcImpl
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

funcGenericRequirements
    : Where genericSignatureTypeList
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
