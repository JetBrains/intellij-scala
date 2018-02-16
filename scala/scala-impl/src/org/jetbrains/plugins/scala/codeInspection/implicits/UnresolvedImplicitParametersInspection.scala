package org.jetbrains.plugins.scala.codeInspection.implicits

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
  * @author Ignat Loskutov
  */
class UnresolvedImplicitParametersInspection extends AbstractInspection {
  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScExpression =>
      expr.findImplicitParameters match {
        case Some(ps) =>
          ps.filter(_.name == InferUtil.notFoundParameterName) match {
            case Seq() =>
            case params =>
              val error = params
                .map(_.implicitSearchState.map(_.tp).getOrElse("unknown type"))
                .mkString("Implicit parameters not found for the following types: ", ", ", "")
              holder.registerProblem(expr, error, ProblemHighlightType.GENERIC_ERROR)
          }
        case _ =>
      }
  }
}
