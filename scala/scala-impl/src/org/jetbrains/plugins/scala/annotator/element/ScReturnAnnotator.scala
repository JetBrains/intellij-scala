package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker.registerUsedImports
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReturn}
import org.jetbrains.plugins.scala.lang.psi.types.api
import org.jetbrains.plugins.scala.project.ProjectContext

object ScReturnAnnotator extends ElementAnnotator[ScReturn] {

  override def annotate(element: ScReturn, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val ctx: ProjectContext = element

    val function = element.method.getOrElse {
      val error = ScalaBundle.message("return.outside.method.definition")
      val annotation = holder.createErrorAnnotation(element.keyword, error)
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      return
    }

    function.returnType match {
      case Right(tp) if function.hasAssign && !tp.equiv(api.Unit) =>
        val importUsed = element.expr.toSet[ScExpression]
          .flatMap(_.getTypeAfterImplicitConversion().importsUsed)

        registerUsedImports(element, importUsed)
      case _ =>
    }
  }
}
