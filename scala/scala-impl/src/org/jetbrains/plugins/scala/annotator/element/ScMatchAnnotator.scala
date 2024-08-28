package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.exhaustiveness.Space
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.exhaustiveness.Space.Empty
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatch

object ScMatchAnnotator extends ElementAnnotator[ScMatch] {
  override def annotate(element: ScMatch, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    val selTyp = element.expression.flatMap(_.`type`().toOption) match {
      case Some(selTyp) => selTyp
      case None => return
    }

    val targetSpace = Space.from(selTyp)

    val patternSpace = Space.Or(element.clauses.map { clause =>
      clause.pattern match {
        case Some(p) if clause.guard.isEmpty => Space.from(p)
        case _ => Empty
      }
    }.toList)


    val uncovered = (targetSpace - patternSpace)
      .simplified
      .flatten
      .filterNot(_ == Space.Empty)

    val dedupped = Space.dedup(uncovered)
      .map(_.toReadableString(element))
      .sorted

    if (dedupped.nonEmpty) {
      val anchor = element.asInstanceOf[ScBegin].keyword
      holder.createWarningAnnotation(anchor, "Match may not be exhaustive. It would fail on cases: " + dedupped.mkString(", "))
    }
  }
}
