package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses

trait ScCatchBlock extends ScalaPsiElement {
  def expression: Option[ScExpression] = findChild[ScExpression]

  def caseClauses: Option[ScCaseClauses] = findChild[ScCaseClauses]

  def getLeftParenthesis: Option[PsiElement]

  def getRightParenthesis: Option[PsiElement]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitCatchBlock(this)
  }
}

object ScCatchBlock {
  def unapply(catchBlock: ScCatchBlock): Option[ScCaseClauses] = {
    for {
      block <- Option(catchBlock)
      expr <- block.expression
      child = PsiTreeUtil.findChildOfType(expr, classOf[ScCaseClauses])
      if child != null
    } yield child
  }
}