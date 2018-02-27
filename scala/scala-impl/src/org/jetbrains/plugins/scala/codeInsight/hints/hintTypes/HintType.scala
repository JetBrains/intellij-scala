package org.jetbrains.plugins.scala
package codeInsight
package hints
package hintTypes

import com.intellij.codeInsight.hints._
import com.intellij.psi.PsiElement

private[hints] abstract class HintType protected(defaultValue: Boolean, idSegments: String*) extends HintFunction {

  val option: Option = {
    val id = "scala" +: idSegments :+ "hint"
    new Option(id.mkString("."), s"Show ${idSegments.mkString(" ")} hints", defaultValue)
  }

  protected val delegate: HintFunction

  override final def isDefinedAt(element: PsiElement): Boolean = delegate.isDefinedAt(element)

  override final def apply(element: PsiElement): Seq[InlayInfo] =
    if (option.isEnabled && isDefinedAt(element)) delegate(element)
    else Seq.empty
}
