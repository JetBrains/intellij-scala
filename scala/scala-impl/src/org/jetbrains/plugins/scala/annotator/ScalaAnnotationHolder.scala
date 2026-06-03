package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.{AnnotationBuilder, AnnotationSession, HighlightSeverity}

/**
 * This is a clone of public API of [[com.intellij.lang.annotation.AnnotationHolder]]
 *
 * We need it for now to handle desugarings (see [[annotationHolder.DelegateAnnotationHolder]]),
 * and because AnnotationHolder is not supposed to be overridden anymore.
 */
trait ScalaAnnotationHolder extends ScalaAnnotationHolderAPI {

  def getCurrentAnnotationSession: AnnotationSession

  def isBatchMode: Boolean

  /**
   * Begin constructing a new annotation.
   * To finish construction and show the annotation on screen {@link AnnotationBuilder# create ( )} must be called.
   * For example: <p>{@code holder.newAnnotation(HighlightSeverity.WARNING, "My warning message").create();}</p>
   *
   * @param severity The severity of the annotation.
   * @param message  The message this annotation will show in the status bar and the tooltip.
   * @apiNote The builder created by this method is already initialized by the current element, i.e. the psiElement currently visited by inspection
   *          visitor. You'll need to call {@link AnnotationBuilder# range ( TextRange )} or similar method explicitly only if target element differs from current element.
   *          Please note, that the range in {@link AnnotationBuilder# range ( TextRange )} must be inside the range of the current element.
   */
  def newAnnotation(severity: HighlightSeverity, @InspectionMessage message: String): ScalaAnnotationBuilder

  /**
   * Begin constructing a new annotation with no message and no tooltip.
   * To finish construction and show the annotation on screen {@link AnnotationBuilder# create ( )} must be called.
   * For example: <p>{@code holder.newSilentAnnotation(HighlightSeverity.WARNING).textAttributes(MY_ATTRIBUTES_KEY).create();}</p>
   *
   * @param severity The severity of the annotation.
   * @apiNote The builder created by this method is already initialized by the current element, i.e. the psiElement currently visited by inspection
   *          visitor. You'll need to call {@link AnnotationBuilder# range ( TextRange )} or similar method explicitly only if target element differs from current element.
   *          Please note, that the range in {@link AnnotationBuilder# range ( TextRange )} must be inside the range of the current element.
   */
  def newSilentAnnotation(severity: HighlightSeverity): ScalaAnnotationBuilder
}
