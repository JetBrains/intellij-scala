package org.jetbrains.plugins.scala.lang
package transformation
package annotations

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class AddTypeToFunctionParameter extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (p: ScParameter) && Parent(e @ Parent(Parent(_: ScFunctionExpr))) if p.paramType.isEmpty =>
      appendTypeAnnotation(p.getRealParameterType.get) { annotation =>
        val replacement = code"(${p.getText}: ${annotation.getText}) => ()"
          .getFirstChild.getFirstChild

        val result = e.replace(replacement).asInstanceOf[ScParameterClause]
        result.parameters.head.typeElement.get
      }
  }
}
