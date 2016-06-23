package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.extensions.{&&, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.Typed
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScPatternArgumentList, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenerator
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.transformation._

/**
  * @author Pavel Fatin
  */
object AddTypeToReferencePattern extends AbstractTransformer {
  def transformation = {
    case (e: ScReferencePattern) && Parent(_: ScCaseClause | _: ScGenerator | _: ScPattern | _: ScPatternArgumentList) && Typed(t)
      if !e.nextSibling.exists(_.getText == ":") =>

      val annotation = annotationFor(t, e)
      val typedPattern = ScalaPsiElementFactory.createPatternFromText(e.text + ": " + annotation.text, e.psiManager)

      val result = e.replace(typedPattern)

      bindTypeElement(result.getLastChild.getFirstChild)
  }
}
