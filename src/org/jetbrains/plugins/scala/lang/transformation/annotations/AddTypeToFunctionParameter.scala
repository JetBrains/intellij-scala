package org.jetbrains.plugins.scala.lang.transformation.annotations

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.transformation._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class AddTypeToFunctionParameter extends AbstractTransformer {
  def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (p: ScParameter) && Parent(e @ Parent(Parent(_: ScFunctionExpr))) if p.paramType.isEmpty =>

      val annotation = annotationFor(p.getRealParameterType().get, e)

      val f = code"(${p.getText}: ${annotation.getText}) => ()"

      val result = e.replace(f.getFirstChild.getFirstChild).asInstanceOf[ScParameterClause]

      bindTypeElement(result.parameters.head.typeElement.get)
  }
}
