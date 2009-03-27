package org.jetbrains.plugins.scala.lang.psi.api.expr

import impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import api.base.patterns._
import lang.parser.ScalaElementTypes
import com.intellij.psi.tree._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScCatchBlock extends ScalaPsiElement {
  def caseClauseList = findChildByClass(classOf[ScCaseClauses])

  def caseClauses = caseClauseList.caseClauses

  def getBranches: Seq[ScExpression] = caseClauses.map {
    (clause: ScCaseClause) => clause.expr match {
      case Some(expr) => expr
      case None => ScalaPsiElementFactory.createExpressionFromText("{}", getManager)
    }
  }
}