package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi.PsiElement
import types.ScType
import lexer.ScalaTokenTypes

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
  def exprs: Seq[ScExpression] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScExpression]).toSeq: _*)

  //TODO java helper (should be removed later)
  def exprsArray = exprs.toArray

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

  /**
   * Return possible applications without using resolve of reference to this call (to avoid SOE)
   */
  def possibleApplications: Array[Array[(String, ScType)]]

  def missedLastExpr: Boolean = {
    var child = getLastChild
    while (child != null && child.getNode.getElementType != ScalaTokenTypes.tCOMMA) {
      if (child.isInstanceOf[ScExpression]) return false
      child = child.getPrevSibling
    }
    return child != null && child.getNode.getElementType == ScalaTokenTypes.tCOMMA
  }

  def isBraceArgs: Boolean = findChild(classOf[ScBlock]) != None
}