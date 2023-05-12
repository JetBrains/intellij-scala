package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.{AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.IterableOnceExt

import scala.annotation.nowarn

class AnnotatorHolderMock(file: PsiFile) extends AnnotatorHolderMockBase[Message](file) {

  def errorAnnotations: List[Message.Error] = annotations.filterByType[Message.Error]

  @nowarn("cat=deprecation")
  private val severityMapping: Map[HighlightSeverity, (String, String) => Message] =
    Map(
      HighlightSeverity.ERROR -> Message.Error.apply,
      HighlightSeverity.WARNING -> Message.Warning.apply,
      HighlightSeverity.WEAK_WARNING -> Message.Warning.apply,
      HighlightSeverity.INFORMATION -> Message.Info.apply,
      HighlightSeverity.INFO -> Message.Info.apply
    )

  override def createMockAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Option[Message] = {
    val transformer = severityMapping.get(severity)
    transformer.map(_.apply(fileTextOf(range), message))
  }
}


class AnnotatorHolderWithRangeMock(file: PsiFile) extends AnnotatorHolderMockBase[MessageWithRange](file) {

  @nowarn("cat=deprecation")
  private val severityMapping: Map[HighlightSeverity, (TextRange, String, String) => MessageWithRange] =
    Map(
      HighlightSeverity.ERROR -> MessageWithRange.Error.apply,
      HighlightSeverity.WARNING -> MessageWithRange.Warning.apply,
      HighlightSeverity.WEAK_WARNING -> MessageWithRange.Warning.apply,
      HighlightSeverity.INFORMATION -> MessageWithRange.Info.apply,
      HighlightSeverity.INFO -> MessageWithRange.Info.apply
    )

  override def createMockAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Option[MessageWithRange] = {
    val transformer = severityMapping.get(severity)
    transformer.map(_.apply(range, fileTextOf(range), message))
  }
}

abstract class AnnotatorHolderMockBase[T](file: PsiFile) extends ScalaAnnotationHolder {

  def annotations: List[T] = myAnnotations.reverse

  protected var myAnnotations: List[T] = List[T]()

  def createMockAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Option[T]

  //noinspection ApiStatus,UnstableApiUsage
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

  protected def fileTextOf(range: TextRange): String = {
    val fileText = getCurrentAnnotationSession.getFile.getText
    fileText.substring(range.getStartOffset, range.getEndOffset)
  }
}