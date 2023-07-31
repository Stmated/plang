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

iterationBody
    : expr
    ;

iterationDoWhileSpec
    : Do iterationBody While conditionalPredicate
    ;

iterationWhileSpec
    : While conditionalPredicate iterationBody
    ;

iterationInHeader
    : identifier In source=expr
    ;

iterationForEachSpec
    : ForEach OpenParen iterationInHeader CloseParen iterationBody
    | ForEach iterationInHeader iterationBody
    ;

iterationForEachItSpec
    : ForEach source=expr iterationBody
    ;

iterationForSpec
    : For OpenParen expressionList? SemiColon conditionalPredicate? SemiColon expressionList? CloseParen expr
    ;

iterationSpec
    : iterationDoWhileSpec
    | iterationWhileSpec
    | iterationForEachSpec
    | iterationForEachItSpec
    | iterationForSpec
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

matchSpec
    : Match expr OpenBrace expressionList CloseBrace
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

parenExprList
    : OpenParen expressionList? CloseParen
    ;

word
    : identifier
    | literal
    | It
    ;

newSpec
    : New expr OpenBrace constructionArgList? CloseBrace
    ;

notExpr
    : Bang expr
    ;

expr
    : blockSpec                                         # blockExpr
    | Export Default? expr                              # exportExpression
    | Import importPath                                 # importExpression
    | Become callSpec                                   # becomeExpression
    | Yield expr                                        # yieldExpression
    | Type genericSignature? expr                       # typeSpecExpression
    | Nominal expr                                      # nominalTypeExpression
    | Symbol                                            # symbolTypeExpression
    | typeSpec                                          # explicitTypeExpr
    | Return expr                                       # returnExpression
    | Then expr                                         # thenExpression
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
    : lhs=functionalityDefineAs (Assign rhs=functionalityDefineAs)*
    ;

functionalityDefineAs
    : lhs=functionalityRunnable (Colon Super? rhs=functionalityRunnable)*
    ;

functionalityRunnable
    // TODO: Figure out how to allow a shorthand func, => without any parameter
    // TODO: Also figure out how to make "return" and "then" and iterators as possible RHS (without disambiguity)
    : lhs=binaryAssignOrLogicalOrMath (ArrowDouble rhs=binaryAssignOrLogicalOrMath)*
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
    //| shorthandFunc
    | notExpr
    | metaSpec
    | identifierExpr
    | escapedKeyword
    | collectionExpr
    | blockSpec
    | parenExprList
    ;

blockSpec
    : OpenBrace expr* CloseBrace
    ;

// Start of type main expression tree
// - This should mimic and share as much as possible with regular 'expr'
// - But there is a need to limit what can be written in type specifiers, or ambiguity arises

typeExpr
    : conditional // TODO: This needs to be a typeConditional that only allow type stuff inside it
    | typeFunctionality
    ;

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

typeCollectionExpr
    : OpenBracket typeCollectionItemList? CloseBracket
    ;

typePrimaryExpr
    : literal
    | DoubleDot
    | Underscore
    | Symbol
    | metaSpec
    | identifierExpr
    //| tupleOrFuncSignature
    | escapedKeyword
    | typeCollectionExpr
    ;


// End of type main expression tree

identifierScopeSpecifierExpr
    : Meta Identifier
    ;

identifierExpr
    : (It | GenericIdentifier | Identifier | metaSpec) identifierScopeSpecifierExpr? genericArgumentSupplier?
    ;

collectionExpr
    : OpenBracket expressionList? CloseBracket
    ;

funcGenericRequirements
    : Where genericSignatureTypeList
    ;

escapedKeyword
    : OpenBracket keyword CloseBracket
    ;

typeSpecifier
    : Colon Super? typeExpr
    ;

initializer
    : Assign expr
    ;

genericPredicate
    : expr
    ;

whereSpec
    : Where genericPredicate (Comma genericPredicate)* Comma?
    ;

variablePrefix
    : Ref? (Val | Var)
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
    : LT expressionList GT
    ;

callBody
    : genericArgumentSupplier? Tilde? OpenParen expressionList? CloseParen
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
