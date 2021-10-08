package org.jetbrains.plugins.scala
package annotator
package annotationHolder

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{AnnotationBuilder, AnnotationHolder, AnnotationSession, HighlightSeverity}
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode

import scala.language.implicitConversions

class ScalaAnnotationHolderAdapter(innerHolder: AnnotationHolder) extends ScalaAnnotationHolder {

  //todo: more fine-grained disabling of annotators
  private val showCompilerErrors =
    ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(innerHolder.getCurrentAnnotationSession.getFile)

  override def getCurrentAnnotationSession: AnnotationSession =
    innerHolder.getCurrentAnnotationSession

  override def isBatchMode: Boolean =
    innerHolder.isBatchMode

  override def newAnnotation(severity: HighlightSeverity, message: String): ScalaAnnotationBuilder =
    if (showCompilerErrors) new CompilerErrorsAwareBuilder(innerHolder.newSilentAnnotation(HighlightSeverity.INFORMATION))
    else new ScalaAnnotationBuilderAdapter(innerHolder.newAnnotation(severity, message))

  override def newSilentAnnotation(severity: HighlightSeverity): ScalaAnnotationBuilder =
    if (showCompilerErrors) new CompilerErrorsAwareBuilder(innerHolder.newSilentAnnotation(HighlightSeverity.INFORMATION))
    else new ScalaAnnotationBuilderAdapter(innerHolder.newSilentAnnotation(severity))

  private class CompilerErrorsAwareBuilder(annotationBuilder: AnnotationBuilder)
    extends ScalaAnnotationBuilderAdapter(annotationBuilder) {

    override def tooltip(tooltip: String): this.type = this
    override def highlightType(highlightType: ProblemHighlightType): this.type = {
      annotationBuilder.highlightType(ProblemHighlightType.INFORMATION)
      this
    }
  }
}
