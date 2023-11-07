package com.github.stmated.plang.ty;

/**
 *
 * @param ty The resulting type, can be null if no resulting type possible
 * @param diffs List if diffs from the original
 */
public record TyResult<T extends Ty>(T ty, TyDiffKind... diffs) {
}
