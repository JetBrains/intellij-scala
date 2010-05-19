package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.util.TextRange
import com.intellij.lang.ASTNode
import java.lang.String
import com.intellij.psi.PsiElement
import com.intellij.lang.annotation.{HighlightSeverity, AnnotationHolder}

/**
 * Pavel.Fatin, 18.05.2010
 */

class AnnotatorHolderMock extends AnnotationHolder {
  private val FakeAnnotation = new com.intellij.lang.annotation.Annotation(
    0, 0, HighlightSeverity.INFO, "message", "tooltip")
  
  var annotations = List[Message]()
  
  def createInfoAnnotation(range: TextRange, message: String) = null

  def createInfoAnnotation(node: ASTNode, message: String) = null

  def createInfoAnnotation(elt: PsiElement, message: String) = null

  def createInformationAnnotation(range: TextRange, message: String) = null

  def createInformationAnnotation(node: ASTNode, message: String) = null

  def createInformationAnnotation(elt: PsiElement, message: String) = null

  def createWarningAnnotation(range: TextRange, message: String) = null

  def createWarningAnnotation(node: ASTNode, message: String) = null

  def createWarningAnnotation(elt: PsiElement, message: String) = { 
    annotations ::= Warning(elt.getText, message)
    FakeAnnotation
  }

  def createErrorAnnotation(range: TextRange, message: String) = null

  def createErrorAnnotation(node: ASTNode, message: String) = null

  def createErrorAnnotation(elt: PsiElement, message: String) = { 
    annotations ::= Error(elt.getText, message)
    FakeAnnotation
  }
} 