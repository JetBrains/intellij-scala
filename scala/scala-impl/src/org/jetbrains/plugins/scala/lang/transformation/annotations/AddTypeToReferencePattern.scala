package org.jetbrains.plugins.scala.lang
package transformation
package annotations

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScPatternArgumentList, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenerator
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createPatternFromText
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class AddTypeToReferencePattern extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (e: ScReferencePattern) && Parent(_: ScCaseClause | _: ScGenerator | _: ScPattern | _: ScPatternArgumentList) && Typeable(t)
      if !e.nextSibling.exists(_.getText == ":") =>

      appendTypeAnnotation(t) { annotation =>
        val typedPattern = createPatternFromText(e.getText + ": " + annotation.getText)
        e.replace(typedPattern).getLastChild.getFirstChild
      }
  }
}
