package org.jetbrains.plugins.scala.lang.psi.api.expr

import base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

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
  def exprs: Seq[ScExpression] = Seq(findChildrenByClass(classOf[ScExpression]): _*)

  /**
   * Is there param with assign expression with same name as
   * method param.
   * @return number of such param or -1
   */
  def nameCallFromParameter: Int

  /**
   * Number of clause.
   * For example: foo()()'()'()
   * then this method return 3.
   */
  def invocationCount: Int

  /**
   * Reference from which started to invoke method calls.
   */
  def callReference: Option[ScReferenceElement]

  /**
   * Expression from which we try to invoke call, or apply method.
   */
  def callExpression: ScExpression

  /**
   * Generic call for this argument list if exist
   */
  def callGeneric: Option[ScGenericCall]
}