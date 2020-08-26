package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

/**
 * This class can be used in two ways:
 * 1. foo(a, b, c)
 * 2. foo {expr}
 * In second way there is no parentheses, just one block expression.
 */
trait ScArgumentExprList extends ScArguments {
  /**
   * Expressions applied to appropriate method call (@see ScMethodCall).
   */
  def exprs: collection.Seq[ScExpression] = findChildrenByClassScala(classOf[ScExpression]).toSeq

  //TODO java helper (should be removed later)
  def exprsArray: Array[ScExpression] = exprs.toArray

  /**
   * Number of clause.
   * For example: foo()()'()'()
   * then this method return 3.
   */
  def invocationCount: Int

  /**
   * Reference from which started to invoke method calls.
   */
  def callReference: Option[ScReferenceExpression]

  /**
   * Expression from which we try to invoke call, or apply method.
   */
  def callExpression: ScExpression

  /**
   * Generic call for this argument list if exist
   */
  def callGeneric: Option[ScGenericCall]

  /**
   * Mapping from argument expressions to corresponding parameters, as found during
   * applicability checking.
   */
  def matchedParameters: collection.Seq[(ScExpression, Parameter)]

  def isUsing: Boolean

  def parameterOf(argExpr: ScExpression): Option[Parameter] = ScalaPsiUtil.parameterOf(argExpr)

  def missedLastExpr: Boolean = {
    var child = getLastChild
    while (child != null && child.getNode.getElementType != ScalaTokenTypes.tCOMMA) {
      if (child.isInstanceOf[ScExpression]) return false
      child = child.getPrevSibling
    }
    child != null && child.getNode.getElementType == ScalaTokenTypes.tCOMMA
  }

  def addExpr(expr: ScExpression): ScArgumentExprList

  def addExprAfter(expr: ScExpression, anchor: PsiElement): ScArgumentExprList

  def isBraceArgs: Boolean = findChild(classOf[ScBlock]).isDefined

  override def getArgsCount: Int = exprs.length

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitArgumentExprList(this)
  }
}

object ScArgumentExprList {
  def unapplySeq(e: ScArgumentExprList): Some[Seq[ScExpression]] = Some(e.exprs.toSeq)
}