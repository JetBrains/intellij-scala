package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.util.TextRange
import com.intellij.lang.ASTNode
import java.lang.String
import com.intellij.psi.PsiElement
import com.intellij.lang.annotation.{Annotation, AnnotationSession, HighlightSeverity, AnnotationHolder}

/**
 * Pavel.Fatin, 18.05.2010
 */

class AnnotatorHolderMock extends AnnotationHolder {
  private val FakeAnnotation = new com.intellij.lang.annotation.Annotation(
    0, 0, HighlightSeverity.INFO, "message", "tooltip")
  
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
} 