package org.jetbrains.plugins.scala.lang.dfa.invocationInfo

import org.jetbrains.plugins.scala.extensions.{ObjectExt, ToNullSafe}
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.{PassByValue, ProperArgument, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.ArgumentFactory.{buildArguments, buildUnmatchedArguments, insertThisArgToArgList}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.ParamToArgMapping.generateParamToArgMapping
import org.jetbrains.plugins.scala.lang.psi.api.InvocationDetails
import org.jetbrains.plugins.scala.lang.psi.api.InvocationDetails.ValueClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import scala.annotation.tailrec

/**
 * An abstraction to represent all possible Scala invocations in a standardized, convenient, syntax-agnostic way.
 * Sugared and desugared versions of the same invocation (like ```3 + 8``` and ```3.+(8)```,
 * or ```arr(5) = 3``` and ```arr.update(5, 3)``` generate the same InvocationInfo (possibly up to the order of evaluation
 * of their arguments, for example in ```x :: list``` and ```list.::(x)``` or ```func(secondArg = 2, firstArg = 8))```.
 *
 * It is designed to collect and offer all necessary information about the original invocation, including multiple
 * argument lists, by-name arguments, evaluation order, ''this'' argument etc.
 *
 * @param invokedElement            ```None``` if the call is unresolved or there are any applicability problems, otherwise
 *                                  ```Some(psiElement)```, where ```psiElement``` is the resolved function/element that was invoked
 * @param argListsInEvaluationOrder arguments (split into original argument lists) in the order in which they should
 *                                  be evaluated, including those not passed explicitly, like default parameters,
 *                                  '''this''' argument etc.
 */
case class InvocationInfo(invokedElement: Option[InvokedElement],
                          argListsInEvaluationOrder: List[List[Argument]],
                          place: ScExpression) {

  /**
   * @return the ''this'' argument for this invocation, for example ```obj``` in ```obj.method(5)``` or ```list``` in ```x :: list```
   */
  val thisArgument: Option[Argument] = argListsInEvaluationOrder.headOption.flatMap(_.find(_.kind == ThisArgument))

  /**
   * @return [[argListsInEvaluationOrder]] filtered for proper arguments (with ''this'' argument filtered out)
   */
  val properArguments: List[List[Argument]] = argListsInEvaluationOrder.map(_.filter(_.kind.is[ProperArgument]))

  def argumentExpressionsInEvaluationOrder: List[Option[ScExpression]] =
    argListsInEvaluationOrder.flatMap(_.iterator.filter(_.passingMechanism == PassByValue).map(_.content))

  /**
   * If ```paramToProperArgMapping(paramIndex) == argIndex```, then the returned mapping maps
   * the parameter on position ```paramIndex``` in order in the function's parameter sequence
   * to the argument on position ```argIndex``` in the evaluation order of proper arguments
   * (ignoring the "this" argument) in an invocation of this function.
   *
   * If the function has multiple parameter/argument lists, the lists are flattened and the indices are counted
   * disregarding the boundaries between the lists.
   *
   * @return list representing parameter-to-proper-argument mapping for this invocation
   */
  val paramToProperArgMapping: List[Option[Int]] = generateParamToArgMapping(invokedElement, properArguments)

  val anchor: ScalaStatementAnchor = ScalaStatementAnchor(place)

  val calledElementIsInProject: Boolean =
    invokedElement.map(_.psiElement).exists(e => e.getManager.isInProject(e.getContext.nullSafe.getOrElse(e)))
}

object InvocationInfo {
  def fromMethodCall(methodCall: ScMethodCall): Seq[InvocationInfo] = {
    @tailrec
    def collect(expression: ScExpression, calls: List[InvocationInfo]): List[InvocationInfo] = expression match {
      case invocation: MethodInvocation =>
        val next = detailsOf(invocation).fold(calls)(details => fromDetails(details, invocation) :: calls)
        invocation match {
          case call: ScMethodCall => collect(call.getEffectiveInvokedExpr, next)
          case _ => next
        }
      case _ => calls
    }

    collect(methodCall, Nil)
  }

  def fromMethodInvocation(invocation: MethodInvocation): InvocationInfo =
    detailsOf(invocation).fold(InvocationInfo(None, Nil, invocation))(fromDetails(_, invocation))

  private def detailsOf(invocation: MethodInvocation): Option[InvocationDetails] = invocation.getContext match {
    case assignment: ScAssignment if assignment.leftExpression == invocation => assignment.invocationDetails
    case _ => invocation.invocationDetails
  }

  def fromReferenceExpression(referenceExpression: ScReferenceExpression): InvocationInfo =
    referenceExpression.invocationDetails.fold(
      InvocationInfo(None, List(List(Argument.thisArg(referenceExpression.qualifier))), referenceExpression)
    )(fromDetails(_, referenceExpression))

  def fromConstructorInvocation(newTemplateDefinition: ScNewTemplateDefinition): InvocationInfo =
    newTemplateDefinition.firstConstructorInvocation
      .map(invocation => fromDetails(InvocationDetails.of(invocation), newTemplateDefinition))
      .getOrElse(InvocationInfo(None, Nil, newTemplateDefinition))

  private def fromDetails(details: InvocationDetails, fallbackPlace: ScExpression): InvocationInfo = {
    val clauses = details.argumentClauses.collect { case clause: ValueClause => clause }
    val invocations = clauses.flatMap(_.argsElement.getContext.asOptionOf[MethodInvocation])
    // Keep the anchor at the first application, including an infix call inside a curried call.
    val place = invocations.headOption.getOrElse(fallbackPlace)
    val properArguments = if (clauses.nonEmpty) clauses.map(buildArguments).toList else fallbackPlace match {
      // Unresolved calls have no signature clauses, but their arguments still need evaluating.
      case invocation: MethodInvocation => List(buildUnmatchedArguments(invocation.argumentExpressions)(invocation.projectContext))
      case _ => Nil
    }
    val thisArgument = Argument.thisArg(details.thisExpr)
    val firstArguments = insertThisArgToArgList(place, properArguments.headOption.getOrElse(Nil), thisArgument)
    val allArguments = firstArguments :: properArguments.drop(1)
    val problems = invocations.flatMap(_.applicationProblems) ++ details.target.toSeq.flatMap(_.problems)

    InvocationInfo(InvokedElement.fromTarget(details.target, problems), allArguments, place)
  }

  def tryFromImplicitConversion(fun: ScFunction, expr: ScExpression): Option[InvocationInfo] = {
    val parameters = fun.syntheticNavigationElement.asOptionOf[ScClass]
      .filter(_ => fun.isSynthetic && fun.isImplicitConversion)
      .flatMap(_.constructor)
      .flatMap(_.clauses)
      .getOrElse(fun.paramClauses)

    for {
      firstClause <- parameters.clauses.filterNot(_.isImplicit).headOption
      params = firstClause.parameters
      if params.size == 1
      param <- params.headOption
    } yield {
      val arg = Argument.fromArgParamMapping((expr, Parameter(param)))
      InvocationInfo(Some(InvokedElement(fun)), List(List(arg)), place = expr)
    }
  }
}
