package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScMethodCallImpl._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

class ScMethodCallImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScMethodCall {

  override def thisExpr: Option[ScExpression] =
    getEffectiveInvokedExpr.asOptionOf[ScReferenceExpression].flatMap { invokedExpr =>
      val refName = invokedExpr.refName
      invokedExpr.bind() match {
        case Some(resolved) if isApplyOrUpdateOnNonFunctionObject(refName, resolved) ||
          isApplyOrUpdateToFunctionObject(resolved) =>
          Some(invokedExpr)
        case _ =>
          invokedExpr.qualifier
      }
    }

  override def getInvokedExpr: ScExpression = findChild[ScExpression].get

  override def argumentExpressions: Seq[ScExpression] = args.exprs

  override def getEffectiveInvokedExpr: ScExpression =
    getInvokedExpr match {
      case x: ScParenthesisedExpr => x.innerElement.getOrElse(x)
      case x => x
    }

  override def toString: String = "MethodCall"
}

object ScMethodCallImpl {

  private def isApplyOrUpdateOnNonFunctionObject(refName: String, resolved: ScalaResolveResult): Boolean =
    refName != resolved.name && isNamedApplyOrUpdate(resolved.name)

  private def isApplyOrUpdateToFunctionObject(resolveResult: ScalaResolveResult): Boolean =
    resolveResult.innerResolveResult.exists(result => isNamedApplyOrUpdate(result.element.name))

  private def isNamedApplyOrUpdate(name: String): Boolean = name == "apply" || name == "update"
}
