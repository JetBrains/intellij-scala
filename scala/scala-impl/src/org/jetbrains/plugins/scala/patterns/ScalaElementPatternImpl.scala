package org.jetbrains.plugins.scala.patterns

import com.intellij.patterns.{ElementPattern, PsiMethodPattern}
import com.intellij.psi.{PsiElement, PsiMethod}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScReference}
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
        if (!hostIsTheArgument(host, index, argsList))
          return false

        argsList.getParent match {
          case call: ScMethodCall =>
            call.getEffectiveInvokedExpr match {
              case ref: ScReference =>
                return resolvesAndMatchesPattern(ref, methodPattern, context)
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }

    false
  }

  //TODO: handle Scala 3 universal apply call
  def isConstructorCallArgument[T <: ScalaPsiElement](
    host: T,
    context: ProcessingContext,
    index: Int,
    constrPattern: PsiMethodPattern,
    isScala3: Boolean
  ): Boolean = {
    host.getParent match {
      case argsList: ScArgumentExprList =>
        argsList.getParent match {
          case call: ScConstructorInvocation =>
            //check arguments only after we checked for parent for performance optimization
            //(tree structure check is cheaper)
            if (!hostIsTheArgument(host, index, argsList))
              return false

            call.reference match {
              case Some(ref) =>
                return resolvesAndMatchesPattern(ref, constrPattern, context)
              case None =>
            }
          case call: ScMethodCall if isScala3 => //handle universal apply
            call.getEffectiveInvokedExpr match {
              case ref: ScReference =>
                return resolvesAndMatchesPattern(ref, constrPattern, context)
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }

    false
  }

  private def hostIsTheArgument[T <: ScalaPsiElement](host: T, index: Int, argsList: ScArgumentExprList) = {
    val args = argsList.exprs
    val hostIsTheArgument = index < args.length && (args(index) eq host)
    hostIsTheArgument
  }

  private def resolvesAndMatchesPattern(
    ref: ScReference,
    methodPattern: ElementPattern[_ <: PsiMethod],
    context: ProcessingContext,
  ): Boolean = {
    val resolveResults = ref.multiResolveScala(false)
    //using "find" instead of "exists" for easier debugging
    val result = resolveResults.find(r => methodPattern.accepts(r.getElement, context))
    result.nonEmpty
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
