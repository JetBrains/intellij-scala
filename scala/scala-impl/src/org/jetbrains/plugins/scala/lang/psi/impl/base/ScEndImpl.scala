package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScMarkerOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScExtensionBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

class ScEndImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnd {
  override def toString: String = "End: " + endingElementDesignator.getText

  override def endingElementDesignator: PsiElement = getLastChild

  override def getElement: PsiElement = this

  override def getReference: PsiReference = this

  override def getRangeInElement: TextRange = endingElementDesignator.getTextRangeInParent

  override def resolve(): PsiElement = {
    getParent match {
      case body: ScTemplateBody =>
        body
          .getParent.ensuring(_.is[ScExtendsBlock])
          .getParent
      case body: ScExtensionBody =>
        body.getParent.ensuring(_.is[ScExtension])
      case parent => parent
    }
  }

  override def beginMarker: Option[PsiElement] = this.parentsInFile.findByType[ScMarkerOwner].map(_.beginMarker)

  override def marker: PsiElement = getFirstChild

  override def getCanonicalText: String =
    resolve() match {
      case td: ScTypeDefinition => td.qualifiedName
      case _ => s"end ${endingElementDesignator.getText}"
    }

  override def handleElementRename(newElementName: String): PsiElement = {
    val designator = endingElementDesignator
    designator.elementType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        val newIdNode = ScalaPsiElementFactory.createIdentifier(newElementName.escapeNonIdentifiers)
        getNode.replaceChild(designator.getNode, newIdNode)
        getElement
      case _ =>
        throw new IncorrectOperationException("Cannot rename non identifier name")
    }
  }

  override def bindToElement(element: PsiElement): PsiElement =
    throw new IncorrectOperationException("Cannot bind end reference to new element")

  override def isReferenceTo(element: PsiElement): Boolean = resolve() == element

  override def isSoft: Boolean = false
}
