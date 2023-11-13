package org.jetbrains.plugins.scala.lang.dfa.invocationInfo

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationChainExtractor.{innerInvocationChain, splitInvocationChain}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.{ProperArgument, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.ArgumentFactory.{ArgumentCountLimit, buildAllArguments, insertThisArgToArgList}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.ParamToArgMapping.generateParamToArgMapping
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScMethodCall, ScNewTemplateDefinition, ScReferenceExpression}

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

  val calledElementIsInProject: Boolean = invokedElement.map(_.psiElement).exists(e => e.getManager.isInProject(e))
}

object InvocationInfo {
  def fromMethodCall(methodCall: ScMethodCall): Seq[InvocationInfo] = {
    val innerChain = innerInvocationChain(methodCall)
    splitInvocationChain(innerChain)
  }

  def fromMethodInvocation(invocation: MethodInvocation): InvocationInfo = {
    if (invocation.matchedParameters.size > ArgumentCountLimit) InvocationInfo(None, Nil, invocation)
    else {
      val target = invocation.target
      val isTupled = target.exists(_.tuplingUsed)

      val thisArgument = Argument.thisArg(invocation.thisExpr)
      val properArguments = buildAllArguments(List(invocation.matchedParameters),
        List(invocation.argumentExpressions), invocation, isTupled).headOption.getOrElse(Nil)
      val allArguments = insertThisArgToArgList(invocation, properArguments, thisArgument)

      InvocationInfo(InvokedElement.fromTarget(target, invocation.applicationProblems), List(allArguments), invocation)
    }
  }

  def fromReferenceExpression(referenceExpression: ScReferenceExpression): InvocationInfo = {
    val target = referenceExpression.bind()

    val thisArgument = Argument.thisArg(referenceExpression.qualifier)
    val properArguments = buildAllArguments(List(referenceExpression.matchedParameters), List(),
      referenceExpression, isTupled = false).headOption.getOrElse(Nil)

    InvocationInfo(InvokedElement.fromTarget(target, Nil), List(thisArgument :: properArguments), referenceExpression)
  }

  def fromConstructorInvocation(newTemplateDefinition: ScNewTemplateDefinition): InvocationInfo = {
    val invocationInfo = newTemplateDefinition.firstConstructorInvocation
      .filter(_.matchedParameters.size <= ArgumentCountLimit)
      .map { constructorInvocation =>
        val target = constructorInvocation.reference.flatMap(_.bind())
        val isTupled = target.exists(_.tuplingUsed)

        val thisArgument = Argument.thisArg(expression = None)
        val properArguments = buildAllArguments(List(constructorInvocation.matchedParameters),
          constructorInvocation.arguments.map(_.exprs), newTemplateDefinition, isTupled)
        val allArguments = (thisArgument :: properArguments.headOption.getOrElse(Nil)) :: properArguments.drop(1)

        InvocationInfo(InvokedElement.fromTarget(target, Nil), allArguments, newTemplateDefinition)
      }

    invocationInfo.getOrElse(InvocationInfo(None, Nil, newTemplateDefinition))
  }
}
