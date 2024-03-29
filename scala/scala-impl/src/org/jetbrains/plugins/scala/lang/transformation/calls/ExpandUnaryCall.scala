package org.jetbrains.plugins.scala.lang.transformation.calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPrefixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.transformation.{AbstractTransformer, RenamedReference}
import org.jetbrains.plugins.scala.project.ProjectContext

class ExpandUnaryCall extends AbstractTransformer {
  override protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScPrefixExpr(RenamedReference(s, t), r) if t == "unary_" + s =>
      e.replace(code"$r.$t}")
  }
}
