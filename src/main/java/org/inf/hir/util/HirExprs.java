package org.inf.hir.util;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.Hir;
import org.inf.hir.HirCallableSignatureVisitor;
import org.inf.hir.HirIndexingTypeVisitor;
import org.inf.hir.HirStructLayoutVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyStruct;
import org.inf.ty.util.Tys;
import org.inf.util.ResolvedField;

@UtilityClass
public class HirExprs {


  public static boolean isDeadEnd(Hir.Expression... children) {
    for (final var child : children) {
      if (Tys.isDeadend(child.ty())) {
        return true;
      }
    }
    return false;
  }

  /// Reads the type for an identifier target or assignment destination, not its expression completion.
  /// Other targets, including functions and identifiers, expose their resolved completion type.
  public static Ty getBindingTy(final Hir.Expression expression) {
    if (expression instanceof Hir.Dec declaration) {
      return declaration.resolvedTy();
    }
    if (expression instanceof Hir.Parameter parameter) {
      return parameter.resolvedTy();
    }
    if (expression instanceof Hir.DotAccess access) {
      return getMemberTy(access);
    }
    if (expression instanceof Hir.ArrayAccess access) {
      return getIndexedTy(access);
    }
    return Tys.completionTy(expression.ty());
  }

  /// Declaration initializers use source constraints, including omitted array element types, never inferred bindings.
  public static Ty getAssignmentContextTy(final Hir.Expression target) {
    if (target instanceof final Hir.Dec declaration) {
      final var constraint = declaration.typeAnnotation().ty();
      return Tys.isInferred(constraint) ? null : constraint;
    }
    return getBindingTy(target);
  }

  /// Function bodies use source return constraints, never previously inferred returns.
  public static Ty getFunctionReturnContextTy(final Hir.FunctionSignature signature) {
    return getFunctionReturnContextTy(signature, null);
  }

  /// A use-site context supplies missing return information; validation still enforces source constraints.
  public static Ty getFunctionReturnContextTy(final Hir.FunctionSignature signature, final Ty inferredReturn) {
    final var constraint = signature.returnTypeAnnotation().ty();
    if (Tys.isInferred(constraint) || Tys.containsInferred(constraint) && inferredReturn != null) {
      return inferredReturn;
    }
    return constraint;
  }

  /// Returns only resolved function-valued information, including unions needed for compatibility diagnostics.
  /// A signature may be available even when evaluating the expression cannot complete.
  @Nullable
  public static Ty getCallableValueTy(final Hir.Expression expression) {
    return HirCallableSignatureVisitor.find(expression);
  }

  /// Selects a single resolved callee signature; unions do not establish a callable target.
  @Nullable
  public static TyFn getCallableSignature(final Hir.Expression expression) {
    final var type = getCallableValueTy(expression);
    return Tys.asStructural(type, TyFn.class);
  }

  /// Selects the resolved layout to construct, even when evaluating the target cannot complete.
  @Nullable
  public static TyStruct getConstructionTargetTy(final Hir.Expression target) {
    return HirStructLayoutVisitor.find(target);
  }

  /// Selects the resolved layout whose fields a member access inspects.
  @Nullable
  public static TyStruct getMemberReceiverTy(final Hir.Expression receiver) {
    return HirStructLayoutVisitor.find(receiver);
  }

  @Nullable
  public static ResolvedField getMemberField(final Hir.DotAccess access) {
    return getField(getMemberReceiverTy(access.target()), access.name());
  }

  @Nullable
  public static ResolvedField getInitializerField(final Hir.NewByBlock construction, final Hir.Assignment initializer) {
    if (!(initializer.lhs() instanceof Hir.Lexeme name)) {
      throw new UnexpectedExpressionException(initializer.lhs());
    }
    return getField(getConstructionTargetTy(construction.target()), name.name());
  }

  /// Reads the selected member independently of receiver completion.
  public static Ty getMemberTy(final Hir.DotAccess access) {
    final var field = getMemberField(access);
    return field == null ? Ty.INFER : field.ty();
  }

  /// Reads the resolved indexing receiver, retaining inferred and invalid types for diagnostics.
  @Nullable
  public static Ty getIndexingReceiverTy(final Hir.Expression receiver) {
    return HirIndexingTypeVisitor.find(receiver);
  }

  /// Distinguishes an element index from an array/range slice even after control transfers.
  @Nullable
  public static Ty getIndexingAccessorTy(final Hir.Expression accessor) {
    return HirIndexingTypeVisitor.find(accessor);
  }

  /// Reads the selected element or slice independently of receiver/accessor completion.
  @Nullable
  public static Ty getIndexedTy(final Hir.ArrayAccess access) {
    return Tys.structuralTy(access.ty());
  }

  /// Looks up a name in the current layout without retaining the result on an expression.
  @Nullable
  public static ResolvedField getField(final TyStruct owner, final String name) {
    if (owner != null) {
      final var fields = owner.fields();
      for (var i = 0; i < fields.length; i++) {
        if (name.equals(fields[i].name())) {
          return new ResolvedField(owner, i);
        }
      }
    }
    return null;
  }
}
