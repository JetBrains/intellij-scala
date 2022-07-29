package org.jetbrains.plugins.scala
package annotator
package annotationHolder

import com.intellij.lang.annotation.{AnnotationHolder, AnnotationSession, HighlightSeverity}

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
