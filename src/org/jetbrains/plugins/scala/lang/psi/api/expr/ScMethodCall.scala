package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import types.result.TypeResult
import types.{ScType, ApplicabilityProblem}
import types.nonvalue.Parameter
import com.intellij.psi.PsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScMethodCall extends ScExpression with MethodInvocation {
  def deepestInvokedExpr: ScExpression = {
    getEffectiveInvokedExpr match {
      case call: ScMethodCall => {
        call.deepestInvokedExpr
      }
      case expr => expr
    }
  }

  def args: ScArgumentExprList = findChildByClassScala(classOf[ScArgumentExprList])

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitMethodCallExpression(this)
  }

  def isUpdateCall: Boolean = getContext.isInstanceOf[ScAssignStmt] &&
                      getContext.asInstanceOf[ScAssignStmt].getLExpression == this

  def updateExpression(): Option[ScExpression] = {
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

  def argsElement: PsiElement = args
}