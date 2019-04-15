package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import org.jetbrains.plugins.scala.annotator.ConstructorInvocationAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility


trait ScSelfInvocationAnnotator extends Annotatable { self: ScSelfInvocation =>
  import ConstructorInvocationAnnotator._

  override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    if (!typeAware)
      return

    val resolved = self.multiResolve


    if (resolved.exists(isConstructorMalformed)) {
      holder.createErrorAnnotation(self.thisElement, "Constructor has malformed definition")
    }


    resolved match {
      case Seq() =>
        val annotation: Annotation = holder.createErrorAnnotation(self.thisElement,
          "Cannot find constructor for this call")
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)

      case Seq(r@ScConstructorResolveResult(constr)) if constr.effectiveParameterClauses.length > 1 && !isConstructorMalformed(r) =>
        // if there is only one well-formed, resolved, scala constructor with multiple parameter clauses,
        // check all of these clauses

        val res = Compatibility.checkConstructorConformance(
          self,
          r.substitutor,
          self.arguments,
          constr.effectiveParameterClauses
        )

        annotateProblems(res.problems, r, self, holder)
      case _ =>
        for (r <- resolved)
          annotateProblems(r.problems, r, self, holder)
    }
  }
}
