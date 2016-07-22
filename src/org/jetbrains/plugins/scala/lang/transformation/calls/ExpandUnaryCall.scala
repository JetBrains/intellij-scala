package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPrefixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
class ExpandUnaryCall extends AbstractTransformer {
  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case e @ ScPrefixExpr(RenamedReference(s, t), r) if t == "unary_" + s =>
      e.replace(code"$r.$t}")
  }
}
