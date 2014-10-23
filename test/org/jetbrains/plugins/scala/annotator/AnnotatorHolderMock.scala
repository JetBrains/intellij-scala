package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{Annotation, AnnotationHolder, AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 18.05.2010
 */

class AnnotatorHolderMock extends AnnotationHolder {
  private val FakeAnnotation = new com.intellij.lang.annotation.Annotation(
    0, 0, HighlightSeverity.WEAK_WARNING, "message", "tooltip")
  
  def annotations = myAnnotations.reverse
  
  private var myAnnotations = List[Message]()
  
  def createInfoAnnotation(range: TextRange, message: String) = null

  def createInfoAnnotation(node: ASTNode, message: String) = {
    myAnnotations ::= Info(node.getText, message)
    FakeAnnotation
  }

  def createInfoAnnotation(elt: PsiElement, message: String) = {
    myAnnotations ::= Info(elt.getText, message)
    FakeAnnotation
  }

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String,
                                htmlTooltip: String): Annotation = null

  def createInformationAnnotation(range: TextRange, message: String) = null

  def createInformationAnnotation(node: ASTNode, message: String) = null

  def createInformationAnnotation(elt: PsiElement, message: String) = null

  def createWarningAnnotation(range: TextRange, message: String) = null

  def createWarningAnnotation(node: ASTNode, message: String) = null

  def createWarningAnnotation(elt: PsiElement, message: String) = { 
    myAnnotations ::= Warning(elt.getText, message)
    FakeAnnotation
  }

  def createErrorAnnotation(range: TextRange, message: String) = {
    myAnnotations ::= ErrorWithRange(range, message)
    FakeAnnotation
  }

  def createErrorAnnotation(node: ASTNode, message: String) = null

  def createErrorAnnotation(elt: PsiElement, message: String) = { 
    myAnnotations ::= Error(elt.getText, message)
    FakeAnnotation
  }

  def getCurrentAnnotationSession: AnnotationSession = null

  def createWeakWarningAnnotation(p1: TextRange, p2: String): Annotation = null

  def createWeakWarningAnnotation(p1: ASTNode, p2: String): Annotation = null

  def createWeakWarningAnnotation(p1: PsiElement, p2: String): Annotation = null

  def isBatchMode: Boolean = false

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Annotation = FakeAnnotation
} 