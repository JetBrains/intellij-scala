package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.{PsiElement, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}

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

  def referenceIsInMemberThatHasTypeDefAsAncestor(typeDefs: ScTypeDefinition*): Boolean = {
    val memberThatReferenceIsPartOf = reference.getElement.parentOfType[ScMember]
    memberThatReferenceIsPartOf.exists(m => typeDefs.exists(typeDef => m == typeDef || typeDef.isAncestorOf(m)))
  }

  private def isReferenceToDefMacroImpl: Boolean =
    reference.getElement.asOptionOf[ScStableCodeReference].exists(ScMacroDefinition.isMacroImplReference)

  override def targetCanBePrivate: Boolean = !isReferenceToDefMacroImpl &&
    target.underlying.get().parentOfType[ScTypeDefinition].exists { typeDef =>
      val typeDefAndCompanion = typeDef +: typeDef.baseCompanion.toSeq
      referenceIsInMemberThatHasTypeDefAsAncestor(typeDefAndCompanion: _*)
    }
}

private object ElementUsageWithKnownReference {
  def apply(reference: PsiElement, target: ScNamedElement): ElementUsageWithKnownReference =
    new ElementUsageWithKnownReference(reference.createSmartPointer, WeakReference(target))

  def apply(reference: SmartPsiElementPointer[PsiElement], target: ScNamedElement): ElementUsageWithKnownReference =
    new ElementUsageWithKnownReference(reference, WeakReference(target))
}
