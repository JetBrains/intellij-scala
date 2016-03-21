package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.codeInspection.ProblemsHolderExt
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.AddCallParentheses
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScType}
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel Fatin
 *
 * TODO test:
 * {{{
 *   object A {
 *     def foo(): Int = 1
 *     foo // warn
 *
 *     def goo(x: () => Int) = 1
 *     goo(foo) // okay
 *
 *     foo : () => Int // okay
 *
 *     def bar[A]() = 0
 *     bar[Int] // warn
 *     bar[Int]: () => Any // okay
 *   }
 * }}}
 */
class EmptyParenMethodAccessedAsParameterlessInspection extends AbstractMethodSignatureInspection(
  "ScalaEmptyParenMethodAccessedAsParameterless", "Empty-paren method accessed as parameterless") {

  def actionFor(holder: ProblemsHolder) = {
    case e: ScReferenceExpression if e.isValid && IntentionAvailabilityChecker.checkInspection(this, e) =>
      e.getParent match {
        case gc: ScGenericCall =>
          ScalaPsiUtil.findCall(gc) match {
            case None => check(e, holder, gc.getType(TypingContext.empty))
            case Some(_) =>
          }
        case _: ScMethodCall | _: ScInfixExpr | _: ScPrefixExpr | _: ScUnderscoreSection => // okay
        case _ => check(e, holder, e.getType(TypingContext.empty))
      }
  }

  private def check(e: ScReferenceExpression, holder: ProblemsHolder, callType: TypeResult[ScType])
                   (implicit typeSystem: TypeSystem = holder.typeSystem) {
    e.resolve() match {
      case (f: ScFunction) if !f.isInCompiledFile && f.isEmptyParen =>
        callType.toOption match {
          case Some(ScFunctionType(_, Seq())) =>
          // might have been eta-expanded to () => A, so don't worn.
          // this avoids false positives. To be more accurate, we would need an 'etaExpanded'
          // flag in ScalaResolveResult.
          case _ => holder.registerProblem(e.nameId, getDisplayName, new AddCallParentheses(e))
        }
      case _ =>
    }
  }
}