package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.ImplicitArgumentsClause
import org.jetbrains.plugins.scala.lang.psi.api.{InvocationDetailsOwner, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * A common trait for  Method calls, Infix, Postfix and Prefix expressions to handle them uniformly
 */
trait MethodInvocation extends ScExpression with ScalaPsiElement with InvocationDetailsOwner {
  /**
   * @return expression that will be the this-ref of the call
   */
  def thisExpr: Option[ScExpression]

  /**
   * @return the target of the invocation
   */
  def target: Option[ScalaResolveResult]

  /**
    * For Infix, Postfix and Prefix expressions
    * it's reference expression for operation
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
   * Seq of application problems like type mismatch.
   *
   * @return seq of application problems
   *
   *
   * 20.03.2025 Andrei Sugak: This is a very weird, borderline unusable method,
   *                          it contains some artificial errors (e.g. MissedValuedParameter for
   *                          implicit parameters, see: t7591/Demo.scala .max call) and misses
   *                          some existing ones (e.g. TypeMismatch in ApplyDynamicWrongSignature.scala)
   *                          In need of a rewrite.
   */
  def applicationProblems: Seq[ApplicabilityProblem] = Seq.empty

  /**
   * The source arguments matched to their parameters.
   */
  override def matchedParameters: Seq[(ScExpression, Parameter)] =
    if (isAutoTupling) {
      matchedParametersInner.flatMap {
        case (parameter, expression, _) if expression != null => Seq(expression -> parameter)
        case (parameter, _, _) =>
          val sourceArguments = this match {
            case call: ScMethodCall => argumentExpressions ++ call.updateExpression()
            case _                  => argumentExpressions
          }
          sourceArguments.map(_ -> parameter)
      }
    } else {
      matchedParametersInner.collect {
        case (parameter, expression, _) if expression != null => expression -> parameter
      }
    }

  protected def matchedParametersInner: Seq[(Parameter, ScExpression, ScType)]

  def matchedTypeParameters: Seq[(ScType, TypeParameter)] = Seq.empty

  /** Whether this argument list was adapted to a single tuple (or `Unit`) argument. */
  def isAutoTupling: Boolean

  /**
    * In case if invoked expression converted implicitly to invoke apply or update method
    *
    * @return imports used for implicit conversion
    */
  def getImportsUsed: Set[ImportUsed]

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
   * The prefix of [[findImplicitArguments]] applied to the receiver before its synthetic `apply` or `update`.
   * Typing records these on this expression, but InvocationDetails assigns them to the receiver's invocation.
   */
  private[psi] def implicitArgumentsOfInvokedExpression: Seq[ImplicitArgumentsClause] = Seq.empty

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

  def unapply(methodInvocation: MethodInvocation): Some[(ScExpression, Seq[ScExpression])] =
    Some((methodInvocation.getInvokedExpr, methodInvocation.argumentExpressions))

  /**
    * @return map of expressions and parameters
    */
  def matchedParametersMap(methodInvocation: MethodInvocation): Map[Parameter, Seq[ScExpression]] =
    methodInvocation.matchedParametersInner
      .groupBy(_._1)
      .map { pair =>
        pair._1 -> pair._2.map(_._2)
      }
}
