package org.jetbrains.plugins.scala

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}

package object lang {
  def collectMethodInvocationArgClauses(e: PsiElement): Seq[Seq[ScExpression]] = {
    val topLevelMethodInvocation =
      e.contexts.takeWhile(_.is[MethodInvocation]).lastOption

    def recurse(
      inv: MethodInvocation,
    ): Seq[Seq[ScExpression]] = {
      val currentArgs = inv.argumentExpressions

      inv.getEffectiveInvokedExpr match {
        case inner: MethodInvocation =>
          recurse(inner) :+ currentArgs
        case _ => Seq(currentArgs)
      }
    }

    topLevelMethodInvocation match {
      case Some(inv: MethodInvocation) => recurse(inv)
      case _                           => Seq.empty
    }
  }
}
