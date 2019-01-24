package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation


trait ScSelfInvocationAnnotator extends Annotatable { self: ScSelfInvocation =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    bind match {
      case Some(_) =>
      case None =>
        if (typeAware) {
          val annotation: Annotation = holder.createErrorAnnotation(self.thisElement,
            "Cannot find constructor for this call")
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        }
    }
  }
}
