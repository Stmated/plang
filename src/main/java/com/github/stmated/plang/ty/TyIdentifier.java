package com.github.stmated.plang.ty;

/**
 * This is a placeholder type that should be resolved by the THIR step of compilation.
 * <p>
 * It refers to a name of a type that might be accessible somewhere in the scope.
 *
 * @param name
 */
public record TyIdentifier(String name) implements Ty {

}
