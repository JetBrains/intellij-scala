package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{Annotation, AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.IterableOnceExt

import scala.annotation.nowarn

/**
 * Pavel.Fatin, 18.05.2010
 */

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
  @nowarn("cat=deprecation")
  protected val FakeAnnotation: ScalaAnnotation = {
    //noinspection DialogTitleCapitalization
    val annotation = new Annotation(0, 0, HighlightSeverity.WEAK_WARNING, "message", "tooltip")
    new ScalaAnnotation(annotation)
  }

  def annotations: List[T] = myAnnotations.reverse

  protected var myAnnotations: List[T] = List[T]()

  def createMockAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Option[T]

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String): ScalaAnnotation = {
    val mockAnnotation = createMockAnnotation(severity, range, message)
    myAnnotations :::= mockAnnotation.toList
    FakeAnnotation
  }

  override def getCurrentAnnotationSession: AnnotationSession = new AnnotationSession(file)

  override def createInfoAnnotation(range: TextRange, message: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.INFORMATION, range, message)

  override def createInfoAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.INFORMATION, node.getTextRange, message)

  override def createInfoAnnotation(elt: PsiElement, message: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.INFORMATION, elt.getTextRange, message)

  override def createWarningAnnotation(range: TextRange, message: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.WARNING, range, message)

  override def createWarningAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.WARNING, node.getTextRange, message)

  override def createWarningAnnotation(elt: PsiElement, message: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.WARNING, elt.getTextRange, message)

  override def createErrorAnnotation(range: TextRange, message: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.ERROR, range, message)

  override def createErrorAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.ERROR, node.getTextRange, message)

  override def createErrorAnnotation(elt: PsiElement, message: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.ERROR, elt.getTextRange, message)

  override def createWeakWarningAnnotation(p1: TextRange, p2: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.WEAK_WARNING, p1, p2)

  override def createWeakWarningAnnotation(p1: ASTNode, p2: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.WEAK_WARNING, p1.getTextRange, p2)

  override def createWeakWarningAnnotation(p1: PsiElement, p2: String): ScalaAnnotation =
    createAnnotation(HighlightSeverity.WEAK_WARNING, p1.getTextRange, p2)

  override def isBatchMode: Boolean = false

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String, htmlTooltip: String): ScalaAnnotation =
    createAnnotation(severity, range, message)
}