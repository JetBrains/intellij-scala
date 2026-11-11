package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaLanguageLevel}

/**
 * Since source version 3.4, explicit arguments for the evidence parameters of context bounds must be passed
 * with `using`: a warning in 3.4 and an error since 3.5 (see `Migrations.contextBoundParams` in the compiler).
 *
 * It matters for `implicit` evidence, e.g. `def foo[A: Show]()` with source version 3.5 (the compiler makes it
 * `def foo[A]()(implicit evidence$1: Show[A])`), or evidence merged into an `implicit` clause:
 * a `using` clause doesn't take arguments without `using` anyway.
 */
object ScArgumentExprListAnnotator extends ElementAnnotator[ScArgumentExprList] {

  override def annotate(arguments: ScArgumentExprList, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    if (typeAware && arguments.isInScala3File && !arguments.isUsing && arguments.exprs.nonEmpty) {
      val sourceLevel = arguments.effectiveLanguageLevel

      if (sourceLevel >= ScalaLanguageLevel.Scala_3_4 && isPassedToContextBoundEvidence(arguments)) {
        val message = ScalaBundle.message("explicit.arguments.for.context.bounds.require.using")

        // with `-source:3.5-migration`, the compiler reports a migration warning
        if (sourceLevel >= ScalaLanguageLevel.Scala_3_5) holder.createErrorAnnotation(arguments, message)
        else                                             holder.createWarningAnnotation(arguments, message)
      }
    }

  /** As in the compiler, the parameter clause must start with the evidence of a context bound without a name */
  private def isPassedToContextBoundEvidence(arguments: ScArgumentExprList): Boolean =
    arguments.matchedParameters.headOption.flatMap(_._2.paramInCode).exists { parameter =>
      parameter.getContext match {
        case clause: ScParameterClause =>
          clause.effectiveParameters.headOption.exists(ScalaPsiUtil.isAnonymousContextBoundEvidence)
        case _ => false
      }
    }
}
