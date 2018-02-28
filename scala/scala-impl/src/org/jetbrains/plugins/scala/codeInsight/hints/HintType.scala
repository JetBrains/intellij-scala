package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeInsight.hints._
import com.intellij.psi.PsiElement

private trait HintType {
  val options: Seq[Option]

  def apply(element: PsiElement): Iterable[InlayInfo]
}
