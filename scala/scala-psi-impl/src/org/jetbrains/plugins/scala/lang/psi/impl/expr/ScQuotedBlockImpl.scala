package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ToNullSafe
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScQuotedBlock

class ScQuotedBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScQuotedBlock {
  override def toString: String = "QuotedBlock"

  override def getEnclosingStartElement: Option[PsiElement] =
    this.getNode
      .nullSafe
      .map(_.findChildByType(ScalaTokenTypes.tLBRACE))
      .map(_.getPsi)
      .toOption

  override def isEnclosedByBraces: Boolean = true

  override def isEnclosedByColon: Boolean = false
}
