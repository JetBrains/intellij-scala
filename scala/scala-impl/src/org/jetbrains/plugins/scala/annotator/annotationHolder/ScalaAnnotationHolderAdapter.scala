package org.jetbrains.plugins.scala.annotator.annotationHolder

import com.intellij.lang.annotation.{AnnotationHolder, AnnotationSession, HighlightSeverity}
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationBuilder, ScalaAnnotationBuilderAdapter, ScalaAnnotationHolder}

class ScalaAnnotationHolderAdapter(innerHolder: AnnotationHolder) extends ScalaAnnotationHolder {

  override def getCurrentAnnotationSession: AnnotationSession =
    innerHolder.getCurrentAnnotationSession

  override def isBatchMode: Boolean =
    innerHolder.isBatchMode

  override def newAnnotation(severity: HighlightSeverity, message: String): ScalaAnnotationBuilder =
    new ScalaAnnotationBuilderAdapter(innerHolder.newAnnotation(severity, message))

  override def newSilentAnnotation(severity: HighlightSeverity): ScalaAnnotationBuilder =
    new ScalaAnnotationBuilderAdapter(innerHolder.newSilentAnnotation(severity))
}
