package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.{PsiClass, PsiElement, SmartPsiElementPointer}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.ref.WeakReference

trait ElementUsage {
  def targetCanBePrivate: Boolean
}

private object ElementUsageWithoutReference extends ElementUsage {
  override val targetCanBePrivate: Boolean = false
}

private final class ElementUsageWithReference private (
  reference: SmartPsiElementPointer[PsiElement],
  target: WeakReference[ScNamedElement]
) extends ElementUsage {

  override lazy val targetCanBePrivate: Boolean = {
    val targetContainingClass = PsiTreeUtil.getParentOfType(target.underlying.get, classOf[PsiClass])
    var refContainingClass = PsiTreeUtil.getParentOfType(reference.getElement, classOf[PsiClass])

    var counter = 0

    if (targetContainingClass == null) {
      false
    } else {
      while (counter < ElementUsageWithReference.MaxSearchDepth && refContainingClass != null && refContainingClass != targetContainingClass) {
        refContainingClass = PsiTreeUtil.getParentOfType(refContainingClass, classOf[PsiClass])
        counter += 1
      }

      refContainingClass == targetContainingClass
    }
  }
}

private object ElementUsageWithReference {
  private val MaxSearchDepth = 10

  def apply(reference: PsiElement, target: ScNamedElement): ElementUsageWithReference =
    new ElementUsageWithReference(reference.createSmartPointer, WeakReference(target))

  def apply(reference: SmartPsiElementPointer[PsiElement], target: ScNamedElement): ElementUsageWithReference =
    new ElementUsageWithReference(reference, WeakReference(target))
}
