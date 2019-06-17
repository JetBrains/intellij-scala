package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility
import org.jetbrains.plugins.scala.project.ProjectContext

object ScSelfInvocationAnnotator extends ElementAnnotator[ScSelfInvocation] {
  // TODO unify using ConstructorInvocationLike
  import ScConstructorInvocationAnnotator._

  override def annotate(element: ScSelfInvocation, typeAware: Boolean = true)
                       (implicit holder: AnnotationHolder): Unit = {
    implicit val ctx: ProjectContext = element

    if (!typeAware)
      return

    val resolved = element.multiResolve


    if (resolved.exists(isConstructorMalformed)) {
      holder.createErrorAnnotation(element.thisElement, "Constructor has malformed definition")
    }


    resolved match {
      case Seq() =>
        val annotation: Annotation = holder.createErrorAnnotation(element.thisElement,
          "Cannot find constructor for this call")
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)

      case Seq(r@ScConstructorResolveResult(constr)) if constr.effectiveParameterClauses.length > 1 && !isConstructorMalformed(r) =>
        // if there is only one well-formed, resolved, scala constructor with multiple parameter clauses,
        // check all of these clauses

        val res = Compatibility.checkConstructorConformance(
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
