package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.{ASTNode, annotation}
import com.intellij.lang.annotation.{Annotation, AnnotationHolder, AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile}
import javax.swing.text.Highlighter.Highlight

/**
 * Pavel.Fatin, 18.05.2010
 */

class AnnotatorHolderMock(file: PsiFile) extends AnnotationHolder {
  private val FakeAnnotation = new com.intellij.lang.annotation.Annotation(
    0, 0, HighlightSeverity.WEAK_WARNING, "message", "tooltip")

  def annotations: List[Message] = myAnnotations.reverse
  def errorAnnotations: List[Message] = annotations.filter {
    case error: Error => true
    case _ => false
  }

  private var myAnnotations = List[Message]()

  def getCurrentAnnotationSession: AnnotationSession = new AnnotationSession(file)

  def createInfoAnnotation(range: TextRange, message: String): Annotation =
    createAnnotation(HighlightSeverity.INFO, range, message)

  def createInfoAnnotation(node: ASTNode, message: String): Annotation =
    createAnnotation(HighlightSeverity.INFO, node.getTextRange, message)

  def createInfoAnnotation(elt: PsiElement, message: String): Annotation =
    createAnnotation(HighlightSeverity.INFO, elt.getTextRange, message)

  def createInformationAnnotation(range: TextRange, message: String): Annotation =
    createAnnotation(HighlightSeverity.INFORMATION, range, message)

  def createInformationAnnotation(node: ASTNode, message: String): Annotation =
    createAnnotation(HighlightSeverity.INFORMATION, node.getTextRange, message)

  def createInformationAnnotation(elt: PsiElement, message: String): Annotation =
    createAnnotation(HighlightSeverity.INFORMATION, elt.getTextRange, message)

  def createWarningAnnotation(range: TextRange, message: String): Annotation =
    createAnnotation(HighlightSeverity.WARNING, range, message)

  def createWarningAnnotation(node: ASTNode, message: String): Annotation =
    createAnnotation(HighlightSeverity.WARNING, node.getTextRange, message)

  def createWarningAnnotation(elt: PsiElement, message: String): Annotation =
    createAnnotation(HighlightSeverity.WARNING, elt.getTextRange, message)

  def createErrorAnnotation(range: TextRange, message: String): Annotation =
    createAnnotation(HighlightSeverity.ERROR, range, message)

  def createErrorAnnotation(node: ASTNode, message: String): Annotation =
    createAnnotation(HighlightSeverity.ERROR, node.getTextRange, message)

  def createErrorAnnotation(elt: PsiElement, message: String): Annotation =
    createAnnotation(HighlightSeverity.ERROR, elt.getTextRange, message)

  def createWeakWarningAnnotation(p1: TextRange, p2: String): Annotation =
    createAnnotation(HighlightSeverity.WEAK_WARNING, p1, p2)

  def createWeakWarningAnnotation(p1: ASTNode, p2: String): Annotation =
    createAnnotation(HighlightSeverity.WEAK_WARNING, p1.getTextRange, p2)

  def createWeakWarningAnnotation(p1: PsiElement, p2: String): Annotation =
    createAnnotation(HighlightSeverity.WEAK_WARNING, p1.getTextRange, p2)

  def isBatchMode: Boolean = false

  private def textOf(range: TextRange): String =
    getCurrentAnnotationSession.getFile.getText
      .substring(range.getStartOffset, range.getEndOffset)

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String, htmlTooltip: String): Annotation =
    createAnnotation(severity, range, message)

  private val severityMapping: Map[HighlightSeverity, (String, String) => Message] = Map(
    HighlightSeverity.ERROR -> Error.apply,
    HighlightSeverity.WARNING -> Warning.apply,
    HighlightSeverity.WEAK_WARNING -> Warning.apply,
    HighlightSeverity.INFORMATION -> Info.apply,
    HighlightSeverity.INFO -> Info.apply
  )

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Annotation = {
    severityMapping.get(severity) match {
      case Some(msg) => myAnnotations ::= msg(textOf(range), message)
      case _ =>
    }
    FakeAnnotation
  }
}