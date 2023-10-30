package com.github.stmated.plang.ty;

/**
 *
 * @param type The resulting type, can be null if no resulting type possible
 * @param diffs List if diffs from the original
 */
public record TyResult<T extends Ty>(T type, TyDiffKind[] diffs) {
}
