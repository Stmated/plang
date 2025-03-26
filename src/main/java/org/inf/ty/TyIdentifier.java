package org.inf.ty;

/**
 * This is a placeholder kind that should be resolved by the THIR step of compilation.
 * <p>
 * It refers to a name of a kind that might be accessible somewhere in the scope.
 *
 * @param name
 */
public record TyIdentifier(String name) implements Ty {

}
