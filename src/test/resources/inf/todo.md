
# PASSIVELY ACTIVE
* Hold off on the whole interpreter/Comptime stuff for now, until the whole compiler has been bootstrapped into its own language (so can reuse compiler code)

# TODO

## Must fix to get things working again!

* Use `PostfixExpression` for `Ast.New`
* Broaden/specify the definition of `PostfixExpression` to support things like `++` and `--`

* Create a visitor which prints AST a multi-line string in some standardized format for rendering code nodes
  * It should be used as snapshot files, to get an overview of AST and see if anything changes

* Perhaps instead of "Call" it is a ParenExpression that contains a target and a paren
  * Same thing as "Call" but a bit more generic; same thing can be used for things like `Ast.New` and others

* Remove "ref" from `Ast.VariableDeclaration` -- it should be handled by `Ast.Ref`

## Next
* Create rudimentary/stupid support for UFC (Universal Function Cal), where we lookup potential "this could be a call"-sites
  * This will require a lot of passes, iterating over the same source multiple times to figure out what is what.
    * (and also to find all times when we simply *cannot* know what is what, and throw "needs disambiguation syntax" errors)

## Other

* Redo the lexer and parser after the new ideas for the language:
  - Almost everything is a Tuple
  - Everything is an expression
  - As much as possible is "Label" (type declarations, function return types, tuple positional naming, etc)
  - Everything(?) is first-class
  - All keywords are user-declared (such as "var" and "const" is a Comptime function that alters the type)
  - Universal Function Call Syntax
  - Prefix, infix and postfix function call syntax (with all operators being a function (which in turn are probably inlined to native code))
  - Make "var" and "val" optional, and make "val" default

* Line and columns saved to the tokens/nodes, for syntax debugging

* TokenToAstRaisingTest (and others) should be using snapshot testing instead, and compare the AST output to a snapshot file
  * Right now the test itself becomes too brittle, and it's hard to reason about the depth of the structure and all that.

* Investigate some way of upgrading "groups" of types where something similar changes for all of them.
  * For example that for one step/context of the code, some properties are not required, but in later code they are
  * There needs to be a way of making them compatible with each other, so that later stages are allowed for earlier stages
  * ie. the changes to the types need/should be more restrictive for the changed types -- but perhaps not needed to be declared? Checked at callsites?

* 
* Create support for the comptime functions which can take an expression and refactor them

* Structures should be able to say "this type is optional, and if it is not specified then it is the same value as this other property"
  * So that for `BlogPost` if "id" is not specified then it is same as "title"
  * Would be nice if this could be a computed property, where if it is not specified then it is calculated each time.
  * Should still be possible to set a value, which then makes it behave like a regular property again.

## Test cases

* Create tests that checks exact result of:
  * Result<(String, String), Error>
  * something<unit8>(2)
