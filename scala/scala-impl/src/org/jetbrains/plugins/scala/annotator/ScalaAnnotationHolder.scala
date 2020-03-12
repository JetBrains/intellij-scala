package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls

/**
 * This is a clone of [[com.intellij.lang.annotation.AnnotationHolder]]
 *
 * We need it for now to handle desugarings (see [[annotationHolder.DelegateAnnotationHolder]]),
 * and because AnnotationHolder is not supposed to be overridden anymore.
 */
trait ScalaAnnotationHolder {
  /**
   * Creates an error annotation with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createErrorAnnotation(elt: PsiElement, @Nls message: String): ScalaAnnotation

  /**
   * Creates an error annotation with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createErrorAnnotation(node: ASTNode, @Nls message: String): ScalaAnnotation

  /**
   * Creates an error annotation with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createErrorAnnotation(range: TextRange, @Nls message: String): ScalaAnnotation

  /**
   * Creates a warning annotation with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createWarningAnnotation(elt: PsiElement, @Nls message: String): ScalaAnnotation

  /**
   * Creates a warning annotation with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createWarningAnnotation(node: ASTNode, @Nls message: String): ScalaAnnotation

  /**
   * Creates a warning annotation with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createWarningAnnotation(range: TextRange, @Nls message: String): ScalaAnnotation

  /**
   * Creates an annotation with severity {@link HighlightSeverity#WEAK_WARNING} ('weak warning') with the specified
   * message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the info message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createWeakWarningAnnotation(elt: PsiElement, @Nls message: String): ScalaAnnotation

  /**
   * Creates an annotation with severity {@link HighlightSeverity#WEAK_WARNING} ('weak warning') with the specified
   * message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the info message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createWeakWarningAnnotation(node: ASTNode, @Nls message: String): ScalaAnnotation

  /**
   * Creates an annotation with severity {@link HighlightSeverity#WEAK_WARNING} ('weak warning') with the specified
   * message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the info message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createWeakWarningAnnotation(range: TextRange, @Nls message: String): ScalaAnnotation

  /**
   * Creates an information annotation (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createInfoAnnotation(elt: PsiElement, @Nls message: String): ScalaAnnotation

  /**
   * Creates an information annotation (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createInfoAnnotation(node: ASTNode, @Nls message: String): ScalaAnnotation

  /**
   * Creates an information annotation (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation)with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createInfoAnnotation(range: TextRange, @Nls message: String): ScalaAnnotation

  /**
   * Creates an annotation with the given severity (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message over the specified text range.
   *
   * @param severity the severity.
   * @param range    the text range over which the annotation is created.
   * @param message  the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createAnnotation(severity: HighlightSeverity, range: TextRange, @Nls message: String): ScalaAnnotation

  /**
   * Creates an annotation with the given severity (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message and tooltip markup over the specified text range.
   *
   * @param severity    the severity.
   * @param range       the text range over which the annotation is created.
   * @param message     the information message.
   * @param htmlTooltip the tooltip to show (usually the message, but escaped as HTML and surrounded by a { @code <html>} tag
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  def createAnnotation(severity: HighlightSeverity, range: TextRange, @Nls message: String, htmlTooltip: String): ScalaAnnotation

  def getCurrentAnnotationSession: AnnotationSession
  def isBatchMode: Boolean
}
