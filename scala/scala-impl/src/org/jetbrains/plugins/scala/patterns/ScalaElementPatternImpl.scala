package org.jetbrains.plugins.scala.patterns

import com.intellij.patterns.ElementPattern
import com.intellij.psi.{PsiElement, PsiMethod}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScMethodCall, ScPostfixExpr, ScReferenceExpression}

private[scala]
object ScalaElementPatternImpl {

  def isRegExpLiteral[T <: ScalaPsiElement](literal: T): Boolean =
    literal.getParent match {
      case ref: ScReferenceExpression => ref.getText.endsWith(".r")
      case postfix: ScPostfixExpr     => postfix.operation.textMatches("r")
      case _                          => false
    }

  //TODO: support infix expressions
  // "qwe".matches("[0-9]+\\w+") //WORKS
  // "qwe" matches "[0-9]+\\w+"  //DOES NOT WORK
  def isMethodCallArgument[T <: ScalaPsiElement](
    host: T, context: ProcessingContext,
    index: Int,
    methodPattern: ElementPattern[_ <: PsiMethod]
  ): Boolean = {
    host.getParent match {
      case argsList: ScArgumentExprList =>
        val args = argsList.exprs
        val hostIsAnArgument = index < args.length && (args(index) eq host)
        if (!hostIsAnArgument)
          return false

        argsList.getParent match {
          case call: ScMethodCall =>
            call.getEffectiveInvokedExpr match {
              case ref: ScReference =>
                for (result <- ref.multiResolveScala(false)) {
                  if (methodPattern.accepts(result.getElement, context))
                    return true
                }
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
    false
  }

  //TODO: support infix expressions
  // "qwe".matches("[0-9]+\\w+") //WORKS
  // "qwe" matches "[0-9]+\\w+"  //DOES NOT WORK
  def methodRefWithArgumentIndex(host: PsiElement): Option[(ScReference, Int)] = {
    host.getParent match {
      case argsList: ScArgumentExprList =>
        val args = argsList.exprs
        val index = args.indexWhere(_ eq host)
        if (index != -1) {
          argsList.getParent match {
            case call: ScMethodCall =>
              call.getEffectiveInvokedExpr match {
                case ref: ScReference =>
                  return Some((ref, index))
                case _ =>
              }
            case _ =>
          }
        }
      case _ =>
    }

    None
  }
}
