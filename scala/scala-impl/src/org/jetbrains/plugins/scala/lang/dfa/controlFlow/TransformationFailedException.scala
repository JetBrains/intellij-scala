package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.psi.PsiElement

case class TransformationFailedException(element: PsiElement, reason: String = "")
  extends Exception(s"Transformation failed for element $element. $reason")

object TransformationFailedException {
  def todo(element: PsiElement): TransformationFailedException =
    TransformationFailedException(element, s"No transformation implemented yet for $element")
}