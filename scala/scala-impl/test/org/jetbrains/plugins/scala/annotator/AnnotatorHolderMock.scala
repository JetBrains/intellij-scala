package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.{AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.IterableOnceExt

import scala.annotation.nowarn

class AnnotatorHolderMock(file: PsiFile) extends AnnotatorHolderMockBase[Message](file) {

  def errorAnnotations: List[Error] = annotations.filterByType[Error]

  private val severityMapping: Map[HighlightSeverity, (String, String) => Message] =
    Map(
      HighlightSeverity.ERROR -> Error.apply,
      HighlightSeverity.WARNING -> Warning.apply,
      HighlightSeverity.WEAK_WARNING -> Warning.apply,
      HighlightSeverity.INFORMATION -> Info.apply,
      HighlightSeverity.INFO -> Info.apply
    ): @nowarn("cat=deprecation")

  private def textOf(range: TextRange): String =
    getCurrentAnnotationSession.getFile.getText
      .substring(range.getStartOffset, range.getEndOffset)

  override def createMockAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Option[Message] = {
    val transformer = severityMapping.get(severity)
    transformer.map(_.apply(textOf(range), message))
  }
}

abstract class AnnotatorHolderMockBase[T](file: PsiFile) extends ScalaAnnotationHolder {

  def annotations: List[T] = myAnnotations.reverse

  protected var myAnnotations: List[T] = List[T]()

  def createMockAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Option[T]

  override def getCurrentAnnotationSession: AnnotationSession = new AnnotationSession(file)

  override def isBatchMode: Boolean = false

  override def newAnnotation(severity: HighlightSeverity, message: String): ScalaAnnotationBuilder =
    new DummyAnnotationBuilder(severity, message)

  override def newSilentAnnotation(severity: HighlightSeverity): ScalaAnnotationBuilder =
    new DummyAnnotationBuilder(severity, null)

  private class DummyAnnotationBuilder(severity: HighlightSeverity, @Nls message: String)
    extends DummyScalaAnnotationBuilder(severity, message) {

    override def onCreate(severity: HighlightSeverity, message: String, range: TextRange): Unit =
      myAnnotations :::= createMockAnnotation(severity, range, message).toList
  }
}