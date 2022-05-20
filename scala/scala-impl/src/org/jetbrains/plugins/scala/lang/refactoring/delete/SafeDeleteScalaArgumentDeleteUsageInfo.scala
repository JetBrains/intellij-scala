package org.jetbrains.plugins.scala
package lang
package refactoring
package delete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo
import org.jetbrains.plugins.scala.extensions.{Parent, _}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory


class SafeDeleteScalaArgumentDeleteUsageInfo(element: PsiElement, referencedElement: PsiElement, isSafeDelete: Boolean)
  extends SafeDeleteReferenceJavaDeleteUsageInfo(element, referencedElement, isSafeDelete) {

  override def deleteElement(): Unit = if (isSafeDelete) {
    val element = getElement
    element match {
      case Parent(infix@ScInfixExpr(_, _, `element`)) =>
        infix
          .replace(ScalaPsiElementFactory.createEquivMethodCall(infix))
          .asOptionOf[ScMethodCall]
          .flatMap(_.argumentExpressions.headOption)
          .foreach { arg =>
            val inner = new SafeDeleteScalaArgumentDeleteUsageInfo(arg, getReferencedElement, isSafeDelete = true)
            inner.deleteElement()
          }

      case _ =>
        super.deleteElement()
    }

  }
}
