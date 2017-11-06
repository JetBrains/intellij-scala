package org.jetbrains.plugins.scala.lang.transformation
package declarations

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReturnStmt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class MakeResultExpressionExplicit extends AbstractTransformer {
  def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (e: ScFunctionDefinition) if e.hasExplicitType && !e.hasUnitResultType =>
      e.returnUsages.foreach {
        case _: ScReturnStmt => // skip
        case it => it.replace(code"return $it")
      }
  }
}
