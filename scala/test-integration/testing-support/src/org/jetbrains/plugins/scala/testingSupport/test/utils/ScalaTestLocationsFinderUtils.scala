package org.jetbrains.plugins.scala.testingSupport.test.utils

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil

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

        val methodCallAndTarget = expr match {
          case call: MethodInvocation if infixStyle => Some((call, call.getInvokedExpr))
          case call: ScMethodCall                   => Some((call, call.deepestInvokedExpr))
          case _                                    => None
        }

        methodCallAndTarget match {
          case Some((methodCall, target)) =>
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
          case _ =>
            None
        }
      }

    val constructorExpressions = body.exprs
    val result = inner(constructorExpressions)
    result
  }

  def collectTestLocationsForRefSpec(body: ScTemplateBody): Seq[ScNamedElement] = {

    def canBeRefSpecPart(member: ScMember with ScNamedElement): Boolean =
      !TestConfigurationUtil.isUnqualifiedPrivateOrThis(member) &&
        member.name.contains(" ")

    def collectTestLocationsForRefSpecInMembers(members: Seq[ScMember]): Seq[ScNamedElement] =
      members
        .collect {
          case f: ScFunctionDefinition if canBeRefSpecPart(f) =>
            Seq(f)
          case o: ScObject if canBeRefSpecPart(o) =>
            o +: collectTestLocationsForRefSpecInMembers(o.members)
        }.flatten

    collectTestLocationsForRefSpecInMembers(body.members)
  }
}
