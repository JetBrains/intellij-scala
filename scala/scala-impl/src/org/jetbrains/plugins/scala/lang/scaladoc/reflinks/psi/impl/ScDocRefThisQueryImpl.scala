package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, ResolveResult}
import org.jetbrains.plugins.scala.extensions.{OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefThisQuery

class ScDocRefThisQueryImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocRefThisQuery {

  override def getElement: PsiElement = this

  override def getRangeInElement: TextRange = thisToken.getTextRangeInParent

  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] =
    resolveThis().iterator.map(new ScalaResolveResult(_)).toArray
  override def resolveThis(): Option[ScNamedElement with ScDocCommentOwner] =
    this.parentOfType[ScDocCommentOwner].filterByType[ScNamedElement with ScDocCommentOwner]
  override def resolve(): ScNamedElement with ScDocCommentOwner = resolveThis().orNull

  override def getCanonicalText: String = "this"

  override def handleElementRename(newElementName: String): PsiElement =
    throw new UnsupportedOperationException("Cannot rename this reference")

  override def bindToElement(element: PsiElement): PsiElement = this

  override def isReferenceTo(element: PsiElement): Boolean = element == resolve()

  override def isSoft: Boolean = false

  override def toString: String = "ScDocRefThisQuery"
}
