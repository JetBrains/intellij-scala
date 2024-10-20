package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScContextBoundImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScContextBound {
  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitContextBound(this)
  }

  override def typeElement: ScTypeElement =
    findChild[ScTypeElement].get

  override def nameId: Option[PsiElement] =
    findLastChildByTypeScala(ScalaTokenTypes.tIDENTIFIER)
}
