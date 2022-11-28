package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, SmartPsiElementPointer}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}

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

  def referenceToTypeDefIsWithinTheSameTypeDef(@NotNull typeDef: PsiClass): Boolean = {
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

      def referenceIsInCompanionScope: Boolean =
        (PsiTreeUtil.getParentOfType(reference.getElement, classOf[ScTypeDefinition]), targetContainingClass) match {
          case (null, _) => false
          case (refTypeDef: ScTypeDefinition, targetTypeDef: ScTypeDefinition) =>
            refTypeDef.baseCompanion.contains(targetTypeDef)
          case _ => false
        }

      referenceToTypeDefIsWithinTheSameTypeDef(targetContainingClass) || referenceIsInCompanionScope
    }
  }

  override lazy val targetCanBePrivate: Boolean =
    referenceIsWithinTargetPrivateScope &&
      (target.underlying.get() match {
        case _: ScTypeDefinition if elementLeaksType(reference.getElement) => false
        case _ => true
      })
}

private object ElementUsageWithKnownReference {
  private val MaxSearchDepth = 10

  def apply(reference: PsiElement, target: ScNamedElement): ElementUsageWithKnownReference =
    new ElementUsageWithKnownReference(reference.createSmartPointer, WeakReference(target))

  def apply(reference: SmartPsiElementPointer[PsiElement], target: ScNamedElement): ElementUsageWithKnownReference =
    new ElementUsageWithKnownReference(reference, WeakReference(target))
}
