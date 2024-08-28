package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.exhaustiveness.Space
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.exhaustiveness.Space.Empty

object ScCaseClausesAnnotator extends ElementAnnotator[ScCaseClauses] {
  override def annotate(element: ScCaseClauses, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    val caseClauses = element.caseClauses

    val selTyp = caseClauses.headOption.flatMap(_.pattern).flatMap(_.expectedType) match {
      case Some(selTyp) => selTyp
      case None => return
    }

    val targetSpace = Space.from(selTyp)

    val patternSpace = Space.Or(caseClauses.map { clause =>
      clause.pattern match {
        case Some(p) if clause.guard.isEmpty => Space.from(p)
        case _ => Empty
      }
    }.toList)


    val uncovered = (targetSpace - patternSpace)
      .simplified
      .flatten

    val dedupped = Space.dedup(uncovered).map(_.toReadableString(element))

    if (dedupped.nonEmpty) {
      val anchor = element.getParent match {
        case expr: ScBegin => expr.keyword

      }
      holder.createWarningAnnotation(anchor, "Match may not be exhaustive. It would fail on cases:\n" + dedupped.mkString("\n"))
    }
  }
}
