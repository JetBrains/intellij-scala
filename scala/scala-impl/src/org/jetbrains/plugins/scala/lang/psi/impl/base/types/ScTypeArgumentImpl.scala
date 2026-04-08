package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgument, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScTypeArgumentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeArgument {
  override def toString: String = "TypeArgument"

  override def typeElement: Option[ScTypeElement] =
    findChild[ScTypeElement]

  override def nameElement: Option[PsiElement] =
    findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def name: Option[String] =
    nameElement.map(_.getText)

  override def deleteChildInternal(child: ASTNode): Unit = {
    if (typeElement.exists(_.getNode == child)) this.delete()
    else super.deleteChildInternal(child)
  }
}
