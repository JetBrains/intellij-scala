package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

object ScSelfInvocationAnnotator extends ElementAnnotator[ScSelfInvocation] {
  // TODO unify using ConstructorInvocationLike
  import ScConstructorInvocationAnnotator._

  override def annotate(element: ScSelfInvocation, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val ctx: ProjectContext = element

    if (!typeAware)
      return

    val resolved: Seq[ScalaResolveResult] = element.multiResolve

    if (resolved.exists(isConstructorMalformed)) {
      holder.createErrorAnnotation(element.thisElement,
        ScalaBundle.message("annotator.error.constructor.has.malformed.definition"))
    }

    resolved match {
      case Seq(ScConstructorResolveResult(constr)) =>
        if (constr.getTextRange.getStartOffset > element.getTextRange.getStartOffset) {
          holder.createErrorAnnotation(element.thisElement, ScalaBundle.message("called.constructor.definition.must.precede"))
        }
      case _ =>
    }

    resolved match {
      case Seq() =>
        holder.createErrorAnnotation(
          element.thisElement,
          ScalaBundle.message("annotator.error.cannot.find.constructor.for.this.call"),
          ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
        )

      case Seq(r@ScConstructorResolveResult(constr)) if constr.effectiveParameterClauses.length > 1 && !isConstructorMalformed(r) =>
        // if there is only one well-formed, resolved, scala constructor with multiple parameter clauses,
        // check all of these clauses

        val res = Compatibility.checkConstructorApplicability(
          element,
          r.substitutor,
          element.arguments,
          constr.effectiveParameterClauses
        )

        annotateProblems(res.problems, r, element)
      case _ =>
        for (r <- resolved)
          annotateProblems(r.problems, r, element)
    }
  }
}
