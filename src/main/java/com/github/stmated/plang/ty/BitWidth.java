package com.github.stmated.plang.ty;

import java.util.Objects;
import lombok.Builder;

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

    if (!(obj instanceof BitWidth other)) {
      return false;
    }

    return other.value == this.value && other.explicit == this.explicit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, explicit);
  }
}
