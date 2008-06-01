package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.openapi.util.TextRange

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScReferenceElement extends ScalaPsiElement with PsiPolyVariantReference {

  def bind() : Option[ScalaResolveResult] = {
    val results = multiResolve(false)
    results.length match {
      case 1 => Some(results(0).asInstanceOf[ScalaResolveResult])
      case _ => None
    }
  }

  def resolve(): PsiElement = bind match {
    case None => null
    case Some(res) => res.element
  }

  override def getReference = this

  def nameNode : PsiElement

  def refName: String = nameNode.getText

  def getElement = this

  def getRangeInElement: TextRange =
    new TextRange(nameNode.getTextRange.getStartOffset - getTextRange.getStartOffset, getTextLength)

  def getCanonicalText: String = null

  def isSoft(): Boolean = false

  def handleElementRename(newElementName: String): PsiElement = {
    return this;
    //todo
  }

  def isReferenceTo(element: PsiElement): Boolean = resolve() == element
}