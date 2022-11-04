package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterType, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock

import scala.ref.WeakReference

trait ElementUsage {
  def targetCanBePrivate: Boolean
}

private object ElementUsageWithoutReference extends ElementUsage {
  override val targetCanBePrivate: Boolean = false
}

private final class ElementUsageWithReference private(
  reference: SmartPsiElementPointer[PsiElement],
  target: WeakReference[ScNamedElement]
) extends ElementUsage {

  private def referenceLeaksTargetType: Boolean = {

    val directParent = reference.getElement.getParent

    def isPrivate(modifierListOwner: ScModifierListOwner): Boolean =
      modifierListOwner.getModifierList.accessModifier.exists(_.isPrivate)

    def getGrandParent(e: PsiElement): Option[PsiElement] =
      e.parent.flatMap(_.parent)

    getGrandParent(reference.getElement) match {

      case Some(f: ScFunction) if f.returnTypeElement.contains(directParent) =>
        !isPrivate(f)

      case Some(p: ScValueOrVariable) if p.typeElement.contains(directParent) =>
        !isPrivate(p)

      case Some(c: ScConstructorInvocation) if c.typeElement == directParent =>

        val extendsBlock = getGrandParent(c).collect { case e: ScExtendsBlock => e }

        val modifierListOwner = extendsBlock.flatMap(_.parent)
          .collect { case m: ScModifierListOwner => m }

        val newTemplateDefinition = extendsBlock.flatMap(_.parent)
          .collect { case n: ScNewTemplateDefinition => n }

        newTemplateDefinition.isEmpty && !modifierListOwner.exists(isPrivate)

      case Some(t: ScParameterType) => getGrandParent(t).flatMap(getGrandParent) match {
        case Some(f: ScFunction) => !isPrivate(f)
        case _ => false
      }

      case Some(t: ScTypeParam) => getGrandParent(t) match {
        case Some(f: ScFunction) => !isPrivate(f)
        case _ => false
      }

      case _ => false
    }
  }

  def referenceIsWithinPrivateScopeOfTypeDef(typeDef: PsiClass): Boolean =
    if (typeDef == null) {
      false
    } else {

      var refContainingClass = PsiTreeUtil.getParentOfType(reference.getElement, classOf[PsiClass])
      var counter = 0

      while (
        counter < ElementUsageWithReference.MaxSearchDepth &&
          refContainingClass != null &&
          refContainingClass != typeDef
      ) {
        refContainingClass = PsiTreeUtil.getParentOfType(refContainingClass, classOf[PsiClass])
        counter += 1
      }

      refContainingClass == typeDef
    }

  private def referenceIsWithinTargetPrivateScope: Boolean = {
    val targetContainingClass =  PsiTreeUtil.getParentOfType(target.underlying.get, classOf[PsiClass])
    referenceIsWithinPrivateScopeOfTypeDef(targetContainingClass)
  }

  override lazy val targetCanBePrivate: Boolean = !referenceLeaksTargetType && referenceIsWithinTargetPrivateScope
}

private object ElementUsageWithReference {
  private val MaxSearchDepth = 10

  def apply(reference: PsiElement, target: ScNamedElement): ElementUsageWithReference =
    new ElementUsageWithReference(reference.createSmartPointer, WeakReference(target))

  def apply(reference: SmartPsiElementPointer[PsiElement], target: ScNamedElement): ElementUsageWithReference =
    new ElementUsageWithReference(reference, WeakReference(target))
}
