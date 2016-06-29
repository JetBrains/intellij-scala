package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.FirstChild
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPostfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
object CanonizePostifxCall extends AbstractTransformer {
  def transformation: PartialFunction[PsiElement, Unit] = {
    case e @ ScPostfixExpr(l, FirstChild(r)) =>
      e.replace(code"$l.$r")
  }
}
