package org.jetbrains.plugins.scala.lang.psi.api.base

import impl.ScalaPsiElementFactory
import impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.plugins.scala.lang.psi.types._
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.resolve._
import statements.ScTypeAlias

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScReferenceElement extends ScalaPsiElement with PsiPolyVariantReference {

  def bind(): Option[ScalaResolveResult] = {
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

  def nameId: PsiElement

  def refName: String = nameId.getText

  def getElement = this

  def getRangeInElement: TextRange =
    if (nameId != null)
      new TextRange(nameId.getTextRange.getStartOffset - getTextRange.getStartOffset, getTextLength)
    else new TextRange(0, getTextLength)

  def getCanonicalText: String = null

  def isSoft(): Boolean = false

  def handleElementRename(newElementName: String): PsiElement = {
    val id = nameId.getNode
    if (id == null) return this
    val parent = id.getTreeParent
    parent.replaceChild(id, ScalaPsiElementFactory.createIdentifier(newElementName, getManager))
    return this
  }

  def isReferenceTo(element: PsiElement): Boolean = resolve() == element

  def qualifier : Option[ScalaPsiElement]
}