package org.inf.hir.util;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.Hir;
import org.inf.hir.HirCallableSignatureVisitor;
import org.inf.hir.HirIndexingTypeVisitor;
import org.inf.hir.HirStructLayoutVisitor;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyStruct;
import org.inf.ty.util.Tys;
import org.inf.util.ResolvedField;

import java.util.concurrent.atomic.AtomicBoolean;

/// TODO: Look through all the functions in here and see how they could be simplified/removed, once we've normalized structural/completion/constraint ty.
@UtilityClass
public class Hirs {

  public static boolean isDeadEnd(Hir.Expression... children) {
    for (final var child : children) {
      if (Tys.isDeadend(child.ty())) {
        return true;
      }
    }
    return false;
  }

  /// Reads bound-value completion, not declaration/parameter completion.
  /// Declarations read their initialization assignment's typed completion, retaining binding structure.
  /// Initializer changes require typing to refresh that completion.
  /// Other targets, including functions and identifiers, expose their resolved completion type.
  public static Ty getBindingTy(final Hir.Expression expression) {
    if (expression instanceof Hir.Dec declaration) {
      final var structural = Tys.structuralTy(declaration.ty());
      final var initialization = declaration.initialization();
      return initialization != null && Tys.isDeadend(initialization.ty())
        ? Tys.withCompletion(Ty.DEADEND, structural) : structural;
    }
    if (expression instanceof Hir.Parameter parameter) {
      return Tys.structuralTy(parameter.ty());
    }
    if (expression instanceof Hir.DotAccess access) {
      return getMemberTy(access);
    }
    if (expression instanceof Hir.ArrayAccess access) {
      return getIndexedTy(access);
    }
    return Tys.completionTy(expression.ty());
  }

  /// Reads a binding destination without inspecting initialization completion.
  public static Ty getBindingStructureTy(final Hir.Expression expression) {
    if (expression instanceof Hir.Identifier identifier && identifier.target() != null) {
      return getBindingStructureTy(identifier.target());
    }
    if (expression instanceof Hir.Dec || expression instanceof Hir.Parameter) {
      return Tys.structuralTy(expression.ty());
    }
    return Tys.structuralTy(getBindingTy(expression));
  }

  /// Declaration initializers use source constraints, including omitted array element types, never inferred bindings.
  public static Ty getAssignmentContextTy(final Hir.Expression target) {
    if (target instanceof final Hir.Dec declaration) {
      final var constraint = Tys.declaredConstraintTy(declaration.ty());
      return Tys.isInferred(constraint) ? null : constraint;
    }
    return getBindingStructureTy(target);
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

  /// Selects an available construction layout, not merely retained structural metadata.
  /// A diverging tuple has none; a call may supply its current return layout despite noncompletion.
  @Nullable
  public static TyStruct getConstructionTargetLayout(final Hir.Expression target) {
    return HirStructLayoutVisitor.findAvailableLayout(target);
  }

  /// Selects an available member layout with the same policy as construction and spread queries.
  /// Availability permits field diagnostics, not runtime field loads after a transfer.
  @Nullable
  public static TyStruct getMemberReceiverLayout(final Hir.Expression receiver) {
    return HirStructLayoutVisitor.findAvailableLayout(receiver);
  }

  @Nullable
  public static ResolvedField getMemberField(final Hir.DotAccess access) {
    return getField(getMemberReceiverLayout(access.target()), access.name());
  }

  @Nullable
  public static ResolvedField getInitializerField(final Hir.NewByBlock construction, final Hir.Assignment initializer) {
    if (!(initializer.lhs() instanceof Hir.Lexeme name)) {
      throw new UnexpectedExpressionException(initializer.lhs());
    }
    return getField(getConstructionTargetLayout(construction.target()), name.name());
  }

  /// Reads the selected member independently of receiver completion.
  public static Ty getMemberTy(final Hir.DotAccess access) {
    final var field = getMemberField(access);
    return field == null ? Ty.INFER : field.ty();
  }

  /// Reads the resolved indexing receiver, retaining inferred and invalid types for diagnostics.
  /// Diverging tuples supply `DEADEND`, not their retained fields; calls may supply a known return layout.
  @Nullable
  public static Ty getIndexingReceiverTy(final Hir.Expression receiver) {
    return HirIndexingTypeVisitor.find(receiver);
  }

  /// Distinguishes an element index from an array/range slice even after control transfers.
  /// This is diagnostic type information, not permission to evaluate an accessor after a transfer.
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

  public static Ty structuralTy(Hir.Expression expr) {
    return expr == null ? null : Tys.structuralTy(expr.ty());
  }

  public static Ty getDeclaredConstraintTy(Hir.Expression expr) {
    return expr == null ? null : Tys.declaredConstraintTy(expr.ty());
  }

  public static Ty ty(Hir.Expression expr) {
    return expr == null ? null : expr.ty();
  }

  public static boolean isInferred(Hir.Expression expr) {
    return expr != null && Tys.isInferred(expr.ty());
  }

  public static boolean has(Hir.Expression root, Hir.Expression needle) {
    final var found = new AtomicBoolean(false);

    root.visit(new HirVisitor() {

      @Override
      public void visitChild(Hir.Expression expr) {
        if (found.get()) {
          return;
        }
        if (expr == needle) {
          found.set(true);
        } else {
          HirVisitor.super.visitChild(expr);
        }
      }
    });

    return found.get();
  }
}
