package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import quickfix.{AddGenericCallParentheses, AddCallParentheses}

/**
  * Pavel Fatin
  */

class EmptyParenMethodAccessedAsParameterlessInspection extends AbstractMethodSignatureInspection(
  "ScalaEmptyParenMethodAccessedAsParameterless", "Empty-paren method accessed as parameterless") {

  def actionFor(holder: ProblemsHolder) = {
    case e: ScReferenceExpression if e.isValid =>
      e.getParent match {
        case gc: ScGenericCall =>
          ScalaPsiUtil.findCall(gc) match {
            case None =>
              e.resolve() match {
                case (f: ScFunction) if f.isEmptyParen =>
                  holder.registerProblem(e.nameId, getDisplayName, new AddGenericCallParentheses(gc))
                case _ =>
              }
            case Some(_) =>
          }
        case _: ScMethodCall | _: ScInfixExpr | _: ScPrefixExpr | _: ScUnderscoreSection => // okay
        case _ => e.resolve() match {
          case (f: ScFunction) if f.isEmptyParen =>
            holder.registerProblem(e.nameId, getDisplayName, new AddCallParentheses(e))
          case _ =>
        }
      }
  }
}