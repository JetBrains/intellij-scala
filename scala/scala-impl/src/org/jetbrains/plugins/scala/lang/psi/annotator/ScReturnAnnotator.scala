package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker.registerUsedImports
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReturn}
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit


trait ScReturnAnnotator extends Annotatable { self: ScReturn =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    val function = method.getOrElse {
      val error = ScalaBundle.message("return.outside.method.definition")
      val annotation: Annotation = holder.createErrorAnnotation(keyword, error)
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      return
    }

    function.returnType match {
      case Right(tp) if function.hasAssign && !tp.equiv(Unit) =>
        val importUsed = expr.toSet[ScExpression]
          .flatMap(_.getTypeAfterImplicitConversion().importsUsed)

        registerUsedImports(this, importUsed)
      case _ =>
    }
  }
}
