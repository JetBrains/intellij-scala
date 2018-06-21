package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{Annotation, AnnotationHolder, AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile}

/**
 * Pavel.Fatin, 18.05.2010
 */

class AnnotatorHolderMock(file: PsiFile) extends AnnotationHolder {
  private val FakeAnnotation = new com.intellij.lang.annotation.Annotation(
    0, 0, HighlightSeverity.WEAK_WARNING, "message", "tooltip")

  def annotations = myAnnotations.reverse
  def errorAnnotations = annotations.filter {
    case error: Error => true
    case _ => false
  }

  private var myAnnotations = List[Message]()

  def createInfoAnnotation(range: TextRange, message: String) = FakeAnnotation

  def createInfoAnnotation(node: ASTNode, message: String) = {
    myAnnotations ::= Info(node.getText, message)
    FakeAnnotation
  }

  def createInfoAnnotation(elt: PsiElement, message: String) = {
    myAnnotations ::= Info(elt.getText, message)
    FakeAnnotation
  }

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String,
                                htmlTooltip: String): Annotation = FakeAnnotation

  def createInformationAnnotation(range: TextRange, message: String) = FakeAnnotation

  def createInformationAnnotation(node: ASTNode, message: String) = FakeAnnotation

  def createInformationAnnotation(elt: PsiElement, message: String) = FakeAnnotation

  def createWarningAnnotation(range: TextRange, message: String) = {
    myAnnotations ::= Warning(textOf(range), message)
    FakeAnnotation
  }

  def createWarningAnnotation(node: ASTNode, message: String) = FakeAnnotation

  def createWarningAnnotation(elt: PsiElement, message: String) = {
    myAnnotations ::= Warning(elt.getText, message)
    FakeAnnotation
  }

  def createErrorAnnotation(range: TextRange, message: String) = {
    myAnnotations ::= Error(textOf(range), message)
    FakeAnnotation
  }

  def createErrorAnnotation(node: ASTNode, message: String) = FakeAnnotation

  def createErrorAnnotation(elt: PsiElement, message: String) = {
    myAnnotations ::= Error(elt.getText, message)
    FakeAnnotation
  }

  def getCurrentAnnotationSession: AnnotationSession = new AnnotationSession(file)

  def createWeakWarningAnnotation(p1: TextRange, p2: String): Annotation = FakeAnnotation

  def createWeakWarningAnnotation(p1: ASTNode, p2: String): Annotation = FakeAnnotation

  def createWeakWarningAnnotation(p1: PsiElement, p2: String): Annotation = {
    myAnnotations ::= Warning(p1.getText, p2)
    FakeAnnotation
  }

  def isBatchMode: Boolean = false

  private def textOf(range: TextRange): String =
    getCurrentAnnotationSession.getFile.getText
      .substring(range.getStartOffset, range.getEndOffset)

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Annotation = FakeAnnotation
}