package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.IndirectPsiReference.Name
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Resolves to a synthetic PSI element with finalTarget as a context.
 * Used by ScEnd to create a non-highlighted reference to enable Rename and Find Usages.
 */
trait IndirectPsiReference extends PsiElement with PsiReference {
  override def getReference: PsiReference = this

  override def getElement: PsiElement = this

  override def getRangeInElement: TextRange = TextRange.create(0, getElement.getTextLength)

  override final def resolve(): PsiElement = {
    val intermediateTarget = finalTarget.map { element =>
      val target = ScalaPsiElementFactory.createScalaFileFromText(s"class ${IndirectPsiReference.Name}")(this).typeDefinitions.head
      target.context = element
      target
    }
    intermediateTarget.orNull
  }

  protected def finalTarget: Option[PsiElement]

  override def handleElementRename(newElementName: String): PsiElement = this

  override def bindToElement(element: PsiElement): PsiElement = this

  override def isReferenceTo(element: PsiElement): Boolean = false

  override def isSoft: Boolean = true

  override def getCanonicalText: String = Name
}

object IndirectPsiReference {
  private final val Name = "IntermediateTarget2cf17ff3b2a54d14b64914496f02dc65" // Random unique ID

  object IntermediateTarget {
    def unapply(intermediateTarget: PsiElement): Option[PsiElement] = intermediateTarget match {
      case target: ScClass if target.name == Name => Some(target.getContext)
      case _ => None
    }
  }
}
