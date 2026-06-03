package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.lang.annotation.{AnnotationSession, HighlightSeverity}
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.extensions.IterableOnceExt

import scala.annotation.nowarn

class AnnotatorHolderMock(file: PsiFile) extends AnnotatorHolderMockBase[Message](file) {

  def errorAnnotations: List[Message.Error] = annotations.filterByType[Message.Error]

  override def createMockAnnotation(
    severity: HighlightSeverity,
    range: TextRange,
    message: String,
    enforcedAttributes: TextAttributesKey,
    fixes: Seq[CommonIntentionAction]
  ): Option[Message] = {
    val constructor = Message.HighlightingSeverityToConstructor.get(severity)
    constructor.map(_.apply(fileTextOf(range), message))
  }
}


class AnnotatorHolderExtendedMock(file: PsiFile) extends AnnotatorHolderMockBase[Message2](file) {

  override def createMockAnnotation(
    severity: HighlightSeverity,
    range: TextRange,
    message: String,
    enforcedAttributes: TextAttributesKey,
    fixes: Seq[CommonIntentionAction]
  ): Option[Message2] = {
    val code = fileTextOf(range)
    Some(Message2(severity, range, code, message, enforcedAttributes, fixes))
  }
}

abstract class AnnotatorHolderMockBase[T : Ordering](file: PsiFile) extends ScalaAnnotationHolder {

  import org.jetbrains.plugins.scala.annotator.Message2.TextRangeOrdering

  //for more stable tests, sorted annotations by range and if it's the same then by the value
  def annotations: List[T] = myAnnotations
    .sortBy(a => (a._1, a._2))
    .map(_._2)

  private var myAnnotations: List[(TextRange, T)] = List[(TextRange, T)]()

  def createMockAnnotation(severity: HighlightSeverity, range: TextRange, message: String, enforcedAttributes: TextAttributesKey, fixes: Seq[CommonIntentionAction]): Option[T]

  //noinspection ApiStatus,UnstableApiUsage
  override def getCurrentAnnotationSession: AnnotationSession = new AnnotationSession(file): @nowarn("cat=deprecation")

  override def isBatchMode: Boolean = false

  override def newAnnotation(severity: HighlightSeverity, message: String): ScalaAnnotationBuilder =
    new DummyAnnotationBuilder(severity, message)

  override def newSilentAnnotation(severity: HighlightSeverity): ScalaAnnotationBuilder =
    new DummyAnnotationBuilder(severity, null)

  private class DummyAnnotationBuilder(severity: HighlightSeverity, @Nullable @Nls message: String)
    extends DummyScalaAnnotationBuilder(severity, message) {

    override def onCreate(
      severity: HighlightSeverity,
      message: String,
      range: TextRange,
      enforcedAttributes: TextAttributesKey,
      fixes: Seq[CommonIntentionAction]
    ): Unit =
      myAnnotations :::= createMockAnnotation(severity, range, message, enforcedAttributes, fixes).toList.map(range -> _)
  }

  protected def fileTextOf(range: TextRange): String = {
    val fileText = getCurrentAnnotationSession.getFile.getText
    fileText.substring(range.getStartOffset, range.getEndOffset)
  }
}
