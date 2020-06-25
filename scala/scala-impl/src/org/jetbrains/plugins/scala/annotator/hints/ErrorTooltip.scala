package org.jetbrains.plugins.scala.annotator.hints

import com.intellij.codeInsight.hint.TooltipGroup
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement

import scala.language.implicitConversions

sealed trait ErrorTooltip {
  def message: String
}

object ErrorTooltip {
  val tooltipGroup = new TooltipGroup("Scala inlay error tooltip", 0)

  implicit def fromString(message: String): ErrorTooltip = ErrorTooltip(message)

  def apply(message: String): ErrorTooltip = JustText(message)

  def apply(message: String, action: IntentionAction, element: PsiElement): ErrorTooltip = {
    WithAction(message, action, element)
  }

  case class JustText(override val message: String) extends ErrorTooltip

  case class WithAction(override val message: String, action: IntentionAction, element: PsiElement) extends ErrorTooltip
}
