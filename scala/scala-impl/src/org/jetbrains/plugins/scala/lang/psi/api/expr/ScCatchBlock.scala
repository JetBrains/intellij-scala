package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses

/**
  * Author: Alexander Podkhalyuzin
  * Date: 06.03.2008
  */
trait ScCatchBlockBase extends ScalaPsiElementBase { this: ScCatchBlock =>
  def expression: Option[ScExpression] = findChild[ScExpression]

  def getLeftParenthesis: Option[PsiElement]

  def getRightParenthesis: Option[PsiElement]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitCatchBlock(this)
  }
}

abstract class ScCatchBlockCompanion {
  def unapply(catchBlock: ScCatchBlock): Option[ScCaseClauses] = {
    for {
      block <- Option(catchBlock)
      expr <- block.expression
      child = PsiTreeUtil.findChildOfType(expr, classOf[ScCaseClauses])
      if child != null
    } yield child
  }
}