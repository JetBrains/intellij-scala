package org.jetbrains.plugins.scala.annotator.hints

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints.AnnotatorHintsKey

// Annotator hints, SCL-15593
case class AnnotatorHints(hints: Seq[Hint], modificationCount: Long) {
  def putTo(element: PsiElement): Unit = {
    element.putUserData(AnnotatorHintsKey, this)
  }
}

object AnnotatorHints {
  private val AnnotatorHintsKey = Key.create[AnnotatorHints]("AnnotatorHints")

  def in(element: PsiElement): Option[AnnotatorHints] = Option(element.getUserData(AnnotatorHintsKey))

  def clearIn(element: PsiElement): Unit = {
    element.putUserData(AnnotatorHintsKey, null)
  }
}