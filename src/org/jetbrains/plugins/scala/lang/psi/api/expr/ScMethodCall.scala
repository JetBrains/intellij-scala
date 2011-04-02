package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import statements.params.ScArguments
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import types.result.TypeResult
import types.{ScType, ApplicabilityProblem}
import types.nonvalue.Parameter

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScMethodCall extends ScExpression {
  def deepestInvokedExpr: ScExpression = {
    getInvokedExpr match {
      case call: ScMethodCall => {
        call.deepestInvokedExpr
      }
      case expr => expr
    }
  }

  def getInvokedExpr: ScExpression = findChildByClassScala(classOf[ScExpression])

  def args: ScArgumentExprList = findChildByClassScala(classOf[ScArgumentExprList])

  def argumentExpressions : Seq[ScExpression] = if (args != null) args.exprs else Nil

  def argumentExpressionsIncludeUpdateCall: Seq[ScExpression] = {
    updateExpression match {
      case Some(expr) => argumentExpressions ++ Seq(expr)
      case _ => argumentExpressions
    }
  }

  override def accept(visitor: ScalaElementVisitor) = visitor.visitMethodCallExpression(this)

  def isUpdateCall: Boolean = getContext.isInstanceOf[ScAssignStmt] &&
                      getContext.asInstanceOf[ScAssignStmt].getLExpression == this

  def updateExpression: Option[ScExpression] = {
    getContext match {
      case a: ScAssignStmt if a.getLExpression == this => a.getRExpression
      case _ => None
    }
  }


  def applicationProblems: Seq[ApplicabilityProblem]

  def matchedParameters: Map[ScExpression, Parameter]

  /**
   * This method useful in case if you want to update some polymorphic type
   * according to method call expected type
   * For exmample:
   */
  def updateAccordingToExpectedType(_nonValueType: TypeResult[ScType]): TypeResult[ScType]
}