package org.inf.ast.passes.implicit_calls;

/// Classifies whether an AST expression's syntactic start can introduce an implicit-call argument.
/// Used by [Cursor] to distinguish the first argument from comma-separated arguments,
/// without consulting types or function signatures.
enum ArgumentStart {

  /// Cannot start an implicit argument, even after a comma.
  /// For example, a bare block is not accepted as the argument in `f x, { y }`.
  NONE,

  /// Can start the first implicit argument or an argument after a comma.
  /// For example, `x` starts an argument in both `f x` and `f 1, x`.
  FIRST,

  /// Can start an implicit argument only after a comma, such as `-2` in `f x, -2`.
  /// Parentheses and brackets also belong here: without a comma, `f (x)`, `f [x]`,
  /// and `f - 2` retain explicit-call, indexing, and subtraction syntax, respectively.
  AFTER_COMMA
}
