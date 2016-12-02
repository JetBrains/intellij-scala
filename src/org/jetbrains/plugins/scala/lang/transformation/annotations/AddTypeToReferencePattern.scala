package org.jetbrains.plugins.scala.lang.transformation.annotations

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScPatternArgumentList, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenerator
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.transformation._

/**
  * @author Pavel Fatin
  */
class AddTypeToReferencePattern extends AbstractTransformer {
  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case (e: ScReferencePattern) && Parent(_: ScCaseClause | _: ScGenerator | _: ScPattern | _: ScPatternArgumentList) && Typeable(t)
      if !e.nextSibling.exists(_.getText == ":") =>

      val annotation = annotationFor(t, e)
      val typedPattern = ScalaPsiElementFactory.createPatternFromText(e.getText + ": " + annotation.getText)(e.getManager)

      val result = e.replace(typedPattern)

      bindTypeElement(result.getLastChild.getFirstChild)
  }
}
