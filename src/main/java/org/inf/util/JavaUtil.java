package org.inf.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class JavaUtil {

  public static Object add(Object op1, Object op2) {

    if (op1 instanceof String || op2 instanceof String) {
      return op1 + String.valueOf(op2);
    }

    if (!(op1 instanceof Number) || !(op2 instanceof Number)) {
      return null;
    }

    if (op1 instanceof Double || op2 instanceof Double) {
      return ((Number) op1).doubleValue() + ((Number) op2).doubleValue();
    }

    if (op1 instanceof Float || op2 instanceof Float) {
      return ((Number) op1).floatValue() + ((Number) op2).floatValue();
    }

    if (op1 instanceof Long || op2 instanceof Long) {
      return ((Number) op1).longValue() + ((Number) op2).longValue();
    }

    if (op1 instanceof Integer || op2 instanceof Integer) {
      return ((Number) op1).intValue() - ((Number) op2).intValue();
    }

    return null;
  }

  public static Object subtract(Object op1, Object op2) {

    if (!(op1 instanceof Number) || !(op2 instanceof Number)) {
      return null;
    }

    if (op1 instanceof Double || op2 instanceof Double) {
      return ((Number) op1).doubleValue() - ((Number) op2).doubleValue();
    }

    if (op1 instanceof Float || op2 instanceof Float) {
      return ((Number) op1).floatValue() - ((Number) op2).floatValue();
    }

    if (op1 instanceof Long || op2 instanceof Long) {
      return ((Number) op1).longValue() - ((Number) op2).longValue();
    }

    if (op1 instanceof Integer || op2 instanceof Integer) {
      return ((Number) op1).intValue() - ((Number) op2).intValue();
    }

    return null;
  }

  public static Object multiply(Object op1, Object op2) {

    if (!(op1 instanceof Number) || !(op2 instanceof Number)) {
      return null;
    }

    if (op1 instanceof Double || op2 instanceof Double) {
      return ((Number) op1).doubleValue() * ((Number) op2).doubleValue();
    }

    if (op1 instanceof Float || op2 instanceof Float) {
      return ((Number) op1).floatValue() * ((Number) op2).floatValue();
    }

    if (op1 instanceof Long || op2 instanceof Long) {
      return ((Number) op1).longValue() * ((Number) op2).longValue();
    }

    if (op1 instanceof Integer || op2 instanceof Integer) {
      return ((Number) op1).intValue() * ((Number) op2).intValue();
    }

    return null;
  }
}
