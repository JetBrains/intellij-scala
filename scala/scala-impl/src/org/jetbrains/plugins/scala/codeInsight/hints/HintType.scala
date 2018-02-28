package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeInsight.hints._
import com.intellij.psi.PsiElement

private[hints] trait HintType extends (PsiElement => Iterable[InlayInfo]) {

  val options: Seq[Option]
}
