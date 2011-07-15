package org.jetbrains.plugins.scala
package codeInspection
package redundantReturnInspection

import org.intellij.lang.annotations.Language
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScReferencePattern, ScStableReferenceElementPattern}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import com.intellij.psi.PsiElement

class EqualsBetweenInconvertibleTypes extends AbstractInspection(
  "ScalaEqualsBetweenInconvertibleTypes",
  ScalaBundle.message("equals.between.inconvertible.types.display.name")
) {
  @Language("HTML")
  override val description = ScalaBundle.message("equals.between.inconvertible.types.description")

  def actionFor(holder: ProblemsHolder) = {
    case invocation: MethodInvocation =>
      invocation.getInvokedExpr match {
        case ref: ScReferenceExpression =>
          // 1 == ""; 1.==("")
          if (ref.refName == "==") {
            ref.bind() match {
              case None =>
              case Some(x) =>
                val base = invocation match {
                  case x: ScInfixExpr => Some(x.getBaseExpr)
                  case _ => ref.qualifier
                }
                x.getElement match {
                  case x: ScSyntheticFunction if x.name == "==" /*&& x.getContainingClass.getQualifiedName == "scala.Any"*/ =>
                    (base, invocation.argumentExpressions) match {
                      case (Some(baseExpression), Seq(arg)) => checkCompatiblity(baseExpression, arg, holder, invocation)
                      case _ =>
                    }
                  case _ =>
                }
            }
          }
        case _ =>
      }
    case refElementPatt: ScStableReferenceElementPattern =>
      // val x = 0; val (a, `x`) = (0, "")
      val referenceExpression = refElementPatt.getReferenceExpression
      referenceExpression.flatMap(_.bind()).foreach {
        (result: ScalaResolveResult) =>
          val el = result.getElement
          val expectedType = refElementPatt.expectedType
          el match {
            case refPatt: ScReferencePattern =>
              val result1 = refPatt.getType(TypingContext.empty)
              (result1, expectedType) match {
                case (Success(t, _), Some(et)) =>
                  checkCompatibility(t, et, holder, refElementPatt)
                case _ =>
              }
            case _ =>
          }
      }
  }

  private def checkCompatiblity(baseExpression: ScExpression, arg: ScExpression, holder: ProblemsHolder, element: PsiElement) {
    val baseType = baseExpression.getType(TypingContext.empty)
    val argType = arg.getType(TypingContext.empty)
    TypeResult.ap2(baseType, argType) {
      (bt: ScType, at: ScType) =>
        checkCompatibility(bt, at, holder, element)
    }
  }

  def checkCompatibility(tp1: ScType, tp2: ScType, holder: ProblemsHolder, element: PsiElement) {
    val compatible = tp1.conforms(tp2, checkWeak = true) || tp2.conforms(tp1, checkWeak = true)
    if (!compatible) {
      val msg = ScalaBundle.message("equals.between.inconvertible.types.problem.descriptor", tp1.presentableText, tp2.presentableText)
      holder.registerProblem(element, msg)
    }
  }
}
