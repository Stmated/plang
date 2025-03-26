package org.inf.ty;
/**
 *
 * @param ty The resulting kind, can be null if no resulting kind possible
 * @param diffs List if diffs from the original
 */
public record TyOpaque() implements Ty {

  @Override
  public String toString() {
    return "Opaque";
  }
}
