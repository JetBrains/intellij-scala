package org.jetbrains.plugins.scala.semantic

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.lang.annotation.{AnnotationSession, HighlightSeverity}
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.annotator.{DummyScalaAnnotationBuilder, ScalaAnnotationBuilder, ScalaAnnotationHolder}
import org.jetbrains.plugins.scala.extensions.IterableOnceExt
import org.jetbrains.plugins.scala.semantic.AnnotatorHolderMock.{AnnotatorHolderMockBase, Message}

import scala.annotation.nowarn
import scala.math.Ordered.orderingToOrdered

// Copy of org.jetbrains.plugins.scala.annotator.AnnotatorHolderMock for non-test use
private class AnnotatorHolderMock(file: PsiFile) extends AnnotatorHolderMockBase[Message](file) {

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

private object AnnotatorHolderMock {
  abstract class AnnotatorHolderMockBase[T: Ordering](file: PsiFile) extends ScalaAnnotationHolder {

    implicit object TextRangeOrdering extends scala.math.Ordering[TextRange] {
      override def compare(x: TextRange, y: TextRange): Int =
        (x.getStartOffset, x.getEndOffset) compare(y.getStartOffset, y.getEndOffset)
    }

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

  sealed abstract class Message extends Ordered[Message] {
    def element: String

    def message: String

    override def compare(that: Message): Int =
      (this.element, this.message) compare (that.element, that.message)
  }

  object Message {
    case class Info(override val element: String, override val message: String) extends Message
    case class Warning(override val element: String, override val message: String) extends Message
    case class Error(override val element: String, override val message: String) extends Message
    case class Hint(override val element: String, text: String, override val message: String = "", offsetDelta: Int = 0) extends Message

    def fromHighlightInfo(info: HighlightInfo, fileText: String): Option[Message] = {
      val constructor = HighlightingSeverityToConstructor.get(info.getSeverity)
      val range = TextRange.create(info.getStartOffset, info.getEndOffset)
      constructor.map(_.apply(range.substring(fileText), info.getDescription))
    }

    @nowarn("cat=deprecation")
    val HighlightingSeverityToConstructor: Map[HighlightSeverity, (String, String) => Message] =
      Map(
        HighlightSeverity.ERROR -> Message.Error.apply,
        HighlightSeverity.WARNING -> Message.Warning.apply,
        HighlightSeverity.WEAK_WARNING -> Message.Warning.apply,
        HighlightSeverity.INFORMATION -> Message.Info.apply,
        HighlightSeverity.INFO -> Message.Info.apply
      )
  }
}
