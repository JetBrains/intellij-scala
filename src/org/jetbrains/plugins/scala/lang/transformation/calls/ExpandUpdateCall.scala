package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
class ExpandUpdateCall extends AbstractTransformer {
  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case e @ ScAssignStmt(ScMethodCall(r @ RenamedReference(_, "update"), keys), Some(value)) =>
      e.replace(code"$r.update(${@@(keys :+ value)})")
  }
}
