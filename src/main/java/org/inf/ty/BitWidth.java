package org.inf.ty;

import lombok.Builder;

import java.util.Objects;

@Builder(toBuilder = true)
public record BitWidth(int value, boolean explicit) {

  @Override
  public String toString() {
    return Objects.toString(value) + (explicit ? "" : "?");
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof BitWidth(int v, boolean e)) {
      return v == this.value && e == this.explicit;

    }

    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, explicit);
  }
}
