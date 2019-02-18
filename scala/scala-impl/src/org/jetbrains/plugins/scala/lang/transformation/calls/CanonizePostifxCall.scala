package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.FirstChild
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPostfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class CanonizePostifxCall extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScPostfixExpr(l, FirstChild(r)) =>
      e.replace(code"$l.$r")
  }
}
