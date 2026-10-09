package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirTupleMatching;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.TyUnion;
import org.inf.ty.TyValueArray;
import org.inf.ty.util.Tys;
import org.inf.ty.util.TypeComparison;

/// Resolves omitted array element types in source constraints from the current value, never an earlier binding.
@UtilityClass
final class HirDeclarationTyping {

  static void resolveBinding(final Hir.Dec declaration, final Ty actual, final boolean availableOnly) {
    final var constraint = declaration.typeAnnotation().ty();
    final var resolved = resolve(constraint, actual);
    declaration.resolvedTy(resolved);
    if (availableOnly || actual == Ty.DEADEND || !requiresMemberValidation(constraint)) {
      return;
    }
    if (Tys.containsInferred(resolved) || !TypeComparison.sameValueType(actual, resolved)) {
      throw new InvalidTypeConversionException("Initializer does not match declaration member constraints", actual, resolved);
    }
  }

  private static boolean requiresMemberValidation(final Ty constraint) {
    return !Tys.isInferred(constraint) && Tys.containsInferred(constraint);
  }

  static Ty resolve(final Ty constraint, final Ty actual) {
    if (Tys.isInferred(constraint)) {
      return actual == null ? Ty.INFER : actual;
    }
    if (!Tys.containsInferred(constraint)) {
      return constraint;
    }
    if (actual instanceof final TyUnion union && !(constraint instanceof TyUnion)) {
      final var alternatives = new Ty[union.types().length];
      for (var i = 0; i < alternatives.length; i++) {
        alternatives[i] = resolve(constraint, union.types()[i]);
      }
      return Tys.union(alternatives);
    }
    return switch (constraint) {
      case TyStruct struct when actual instanceof TyStruct source -> resolveStruct(struct, source);
      case TyValueArray array when actual instanceof TyValueArray source ->
        new TyValueArray(resolve(array.elementType(), source.elementType()), array.size()).intern();
      default -> constraint;
    };
  }

  private static Ty resolveStruct(final TyStruct constraint, final TyStruct actual) {
    final var slots = HirTupleMatching.match(actual, constraint);
    if (slots == null) {
      return constraint;
    }
    final var fields = new TyField[constraint.fields().length];
    for (var i = 0; i < slots.length; i++) {
      final var index = slots[i];
      final var field = constraint.fields()[index];
      fields[index] = new TyField(field.name(), resolve(field.ty(), actual.fields()[i].ty()));
    }
    return new TyStruct(fields, constraint.tuple()).intern();
  }
}
