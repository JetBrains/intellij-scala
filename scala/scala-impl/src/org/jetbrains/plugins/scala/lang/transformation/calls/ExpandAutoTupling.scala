package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.Resolved
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class ExpandAutoTupling extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScMethodCall(t @ Resolved(result), es) if result.tuplingUsed =>
      e.replace(code"$t((${@@(es)}))")

    case ScInfixExpr(_, Resolved(result), r) if result.tuplingUsed =>
      r.replace(code"($r)")
  }
}
