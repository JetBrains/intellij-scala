package org.jetbrains.plugins.scala
package lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * Pavel Fatin, Alexander Podkhalyuzin.
  */

// A common trait for Infix, Postfix and Prefix expressions
// and Method calls to handle them uniformly
trait MethodInvocation extends ScExpression with ScalaPsiElement {
  /**
    * For Infix, Postfix and Prefix expressions
    * it's refernce expression for operation
    *
    * @return method reference or invoked expression for calls
    */
  def getInvokedExpr: ScExpression

  /**
    * @return call arguments
    */
  def argumentExpressions: Seq[ScExpression]

  /**
    * Unwraps parenthesised expression for method calls
    *
    * @return unwrapped invoked expression
    */
  def getEffectiveInvokedExpr: ScExpression = getInvokedExpr

  /**
    * Important method for method calls like: foo(expr) = assign.
    * Usually this is same as argumentExpressions
    *
    * @return arguments with additional argument if call in update position
    */
  def argumentExpressionsIncludeUpdateCall: Seq[ScExpression] = argumentExpressions

  /**
    * Seq of application problems like type mismatch.
    *
    * @return seq of application problems
    */
  def applicationProblems: Seq[ApplicabilityProblem]

  /**
    * @return map of expressions and parameters
    */
  def matchedParameters: Seq[(ScExpression, Parameter)] = {
    matchedParametersInner.map(a => a.swap).filter(a => a._1 != null) //todo: catch when expression is null
  }

  /**
    * @return map of expressions and parameters
    */
  def matchedParametersMap: Map[Parameter, Seq[ScExpression]] = {
    matchedParametersInner.groupBy(_._1).map(t => t.copy(_2 = t._2.map(_._2)))
  }

  protected def matchedParametersInner: Seq[(Parameter, ScExpression)]

  /**
    * In case if invoked expression converted implicitly to invoke apply or update method
    *
    * @return imports used for implicit conversion
    */
  def getImportsUsed: collection.Set[ImportUsed]

  /**
    * In case if invoked expression converted implicitly to invoke apply or update method
    *
    * @return actual conversion element
    */
  def getImplicitFunction: Option[ScalaResolveResult]

  /**
    * true if this call is syntactic sugar for apply or update method.
    */
  def isApplyOrUpdateCall: Boolean = applyOrUpdateElement.isDefined

  def applyOrUpdateElement: Option[ScalaResolveResult]

  /**
    * It's arguments for method and infix call.
    * For prefix and postfix call it's just operation.
    *
    * @return Element, which reflects arguments
    */
  def argsElement: PsiElement

  /**
    * @return Is this method invocation in 'update' syntax sugar position.
    */
  def isUpdateCall: Boolean = false
}

object MethodInvocation {

  def unapply(methodInvocation: MethodInvocation): Option[(ScExpression, Seq[ScExpression])] =
    for {
      invocation <- Option(methodInvocation)
      expression = invocation.getInvokedExpr
      if expression != null
    } yield (expression, invocation.argumentExpressions)
}
