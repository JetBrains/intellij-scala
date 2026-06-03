package org.jetbrains.plugins.scala.editor.selectioner

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral

class ScalaWordSelectionerFilter extends Condition[PsiElement] {

  override def value(e: PsiElement): Boolean = e.getParent match {
    case _: ScInterpolatedStringLiteral => false //handled by ScalaStringLiteralSelectioner
    case _                              => true
  }
}
