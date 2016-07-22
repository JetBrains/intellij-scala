package org.jetbrains.plugins.scala.lang.transformation.annotations

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.transformation._

/**
  * @author Pavel Fatin
  */
class AddTypeToMethodDefinition extends AbstractTransformer {
  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case (e: ScFunctionDefinition) if e.hasAssign && e.returnTypeElement.isEmpty =>
      appendTypeAnnotation(e.paramClauses, e.returnType.get)
  }
}
