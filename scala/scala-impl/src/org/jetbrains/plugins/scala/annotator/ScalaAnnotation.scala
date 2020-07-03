package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.Nls

class ScalaAnnotation(annotation: Annotation) {

  def registerFix(fix: IntentionAction): Unit =
    annotation.registerFix(fix)

  def registerFix(fix: IntentionAction, range: TextRange): Unit =
    annotation.registerFix(fix, range)

  def registerFix(fix: IntentionAction, range: TextRange, key: HighlightDisplayKey): Unit =
    annotation.registerFix(fix, range, key)

  def getStartOffset: Int =
    annotation.getStartOffset

  def getTextAttributes: TextAttributesKey =
    annotation.getTextAttributes

  def setEnforcedTextAttributes(enforcedAttributes: TextAttributes): Unit =
    annotation.setEnforcedTextAttributes(enforcedAttributes)

  def setTooltip(@Nls tooltip: String): Unit =
    annotation.setTooltip(tooltip)

  def setHighlightType(highlightType: ProblemHighlightType): Unit =
    annotation.setHighlightType(highlightType)

  def setTextAttributes(enforcedAttributes: TextAttributesKey): Unit =
    annotation.setTextAttributes(enforcedAttributes)

  def setAfterEndOfLine(afterEndOfLine: Boolean): Unit =
    annotation.setAfterEndOfLine(afterEndOfLine)

  override def toString: String =
    annotation.toString

  override def equals(obj: Any): Boolean =
    annotation.equals(obj)

  override def hashCode(): Int =
    annotation.hashCode()
}
