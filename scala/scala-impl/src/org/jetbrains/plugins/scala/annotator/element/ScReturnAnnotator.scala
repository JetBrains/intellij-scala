package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReturn}
import org.jetbrains.plugins.scala.lang.psi.types.api
import org.jetbrains.plugins.scala.project.ProjectContext

object ScReturnAnnotator extends ElementAnnotator[ScReturn] {

  override def annotate(element: ScReturn, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val ctx: ProjectContext = element

    val function = element.method.getOrElse {
      val error      = ScalaBundle.message("return.outside.method.definition")
      val annotation = holder.createErrorAnnotation(element.keyword, error)
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      return
    }

    function.returnType match {
      case Right(tp) if function.hasAssign && !tp.isUnit =>
        if (element.expr.isEmpty) {
          TypeMismatchError.register(element, tp, api.Unit) { (expected, actual) =>
            ScalaBundle.message("expr.type.does.not.conform.expected.type", actual, expected)
          }
        }
      case Right(u) if u.isUnit && element.expr.nonEmpty => element.expr.foreach(redundantReturnExpression)
      case _                                        =>
    }
  }

  private def redundantReturnExpression(e: ScExpression)(implicit holder: ScalaAnnotationHolder): Unit = {
    val tpe = e.getTypeAfterImplicitConversion().tr
    tpe.foreach { t =>
      val message = ScalaBundle.message("return.expression.is.redundant", t.presentableText(e))
      holder.createWarningAnnotation(e, message)
    }
  }
}
