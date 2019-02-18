package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.FirstChild
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class CanonizeInfixCall extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScInfixExpr(l, FirstChild(o), r) =>
      val (a, b) = if (e.isRightAssoc) (r, l) else (l, r)

      val element = b match {
        case block: ScBlockExpr => code"$a.$o {${block.exprs}}"
        case _ => code"$a.$o($b)"
      }

      e.replace(element)
  }
}
