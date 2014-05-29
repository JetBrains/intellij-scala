package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import com.intellij.psi.util.PsiTreeUtil

/**
 * Author: Alexander Podkhalyuzin
 * Date: 06.03.2008
 */
trait ScCatchBlock extends ScalaPsiElement {
  def expression: Option[ScExpression] = findChild(classOf[ScExpression])
  def getLeftParenthesis : Option[PsiElement]
  def getRightParenthesis : Option[PsiElement]
}

object ScCatchBlock {
  def unapply(catchBlock: ScCatchBlock): Option[ScCaseClauses] = {
    for {
      expr <- catchBlock.expression
      child = PsiTreeUtil.findChildOfType(expr, classOf[ScCaseClauses])
      if child != null
    } yield child
  }
}