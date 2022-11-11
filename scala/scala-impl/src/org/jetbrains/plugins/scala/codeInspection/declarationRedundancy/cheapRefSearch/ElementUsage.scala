package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, SmartPsiElementPointer}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}

import scala.annotation.tailrec
import scala.ref.WeakReference

trait ElementUsage {
  def targetCanBePrivate: Boolean
}

private object ElementUsageWithUnknownReference extends ElementUsage {
  override val targetCanBePrivate: Boolean = false
}

private final class ElementUsageWithKnownReference private(
  reference: SmartPsiElementPointer[PsiElement],
  target: WeakReference[ScNamedElement]
) extends ElementUsage {

  @tailrec
  private def elementLeaksType(e: PsiElement): Boolean = {

    def isUnqualifiedOrThisPrivate(modifierListOwner: ScModifierListOwner): Boolean =
      modifierListOwner.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis)

    e match {
      case null => false
      case f: ScFunction => !isUnqualifiedOrThisPrivate(f)
      case v: ScValueOrVariable => !isUnqualifiedOrThisPrivate(v)
      case c: ScClass => !isUnqualifiedOrThisPrivate(c)
      case _ => elementLeaksType(e.getParent)
    }
  }

  def referenceIsWithinPrivateScopeOfTypeDef(@NotNull typeDef: PsiClass): Boolean = {
    var refContainingClass = PsiTreeUtil.getParentOfType(reference.getElement, classOf[PsiClass])
    var counter = 0

    while (
      counter < ElementUsageWithKnownReference.MaxSearchDepth &&
        refContainingClass != null &&
        refContainingClass != typeDef
    ) {
      refContainingClass = PsiTreeUtil.getParentOfType(refContainingClass, classOf[PsiClass])
      counter += 1
    }

    refContainingClass == typeDef
  }

  private def referenceIsWithinTargetPrivateScope: Boolean = {
    val targetContainingClass = PsiTreeUtil.getParentOfType(target.underlying.get, classOf[PsiClass])
    if (targetContainingClass == null) {
      false
    } else {
      referenceIsWithinPrivateScopeOfTypeDef(targetContainingClass)
    }
  }

  override lazy val targetCanBePrivate: Boolean =
    if (target.underlying.get().isInstanceOf[ScTypeDefinition]) {
      !elementLeaksType(reference.getElement)
    } else {
      referenceIsWithinTargetPrivateScope
    }
}

private object ElementUsageWithKnownReference {
  private val MaxSearchDepth = 10

  def apply(reference: PsiElement, target: ScNamedElement): ElementUsageWithKnownReference =
    new ElementUsageWithKnownReference(reference.createSmartPointer, WeakReference(target))

  def apply(reference: SmartPsiElementPointer[PsiElement], target: ScNamedElement): ElementUsageWithKnownReference =
    new ElementUsageWithKnownReference(reference, WeakReference(target))
}
