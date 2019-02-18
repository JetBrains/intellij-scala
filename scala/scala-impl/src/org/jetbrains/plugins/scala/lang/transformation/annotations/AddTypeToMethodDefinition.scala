package org.jetbrains.plugins.scala.lang
package transformation
package annotations

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class AddTypeToMethodDefinition extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (e: ScFunctionDefinition) if e.hasAssign && e.returnTypeElement.isEmpty =>
      appendTypeAnnotation(e.returnType.get, e.paramClauses)
  }
}
