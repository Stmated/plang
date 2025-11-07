package org.inf.ty;

import lombok.Builder;

import java.util.Objects;

@Builder(toBuilder = true)
public record BitWidth(int value, boolean explicit) {

  public static BitWidth merge(BitWidth a, BitWidth b) {

    return new BitWidth(
      Math.max(a.value(), b.value()),
      a.explicit() || b.explicit()
    );
  }

  @Override
  public String toString() {
    return Objects.toString(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (!(obj instanceof BitWidth(int v, boolean e))) {
      return false;
    }

    return v == this.value && e == this.explicit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, explicit);
  }
}
