package org.jetbrains.plugins.scala.testingSupport.test.utils

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody

private[testingSupport]
object ScalaTestLocationsFinderUtils {

  def collectTestLocations(
    body: ScTemplateBody,
    infixStyle: Boolean,
    intermediateMethodNames: Set[String],
    leafMethodNames: Set[String]
  ): Seq[ScReferenceExpression] = {

    def inner(expressions: Seq[ScExpression]): Seq[ScReferenceExpression] =
      expressions.flatMap { expr =>
        ProgressManager.checkCanceled()

        val (methodCall, target) = expr match {
          case call: MethodInvocation if infixStyle => (call, call.getInvokedExpr)
          case call: ScMethodCall                   => (call, call.deepestInvokedExpr)
          case _                                    => return Seq.empty
        }

        target match {
          case ref: ScReferenceExpression =>
            if (intermediateMethodNames.contains(ref.refName)) {
              val childExpressions = methodCall.argumentExpressions.collect { case block: ScBlockExpr => block.exprs }
              Seq(ref) ++ inner(childExpressions.flatten)
            }
            else if (leafMethodNames.contains(ref.refName))
              Seq(ref)
            else
              Seq.empty
          case _                          =>
            Seq.empty
        }
      }

    val constructorExpressions = body.exprs
    inner(constructorExpressions)
  }
}
