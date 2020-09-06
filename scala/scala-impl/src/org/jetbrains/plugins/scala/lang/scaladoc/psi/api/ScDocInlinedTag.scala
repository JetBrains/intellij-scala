package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScDocInlinedTag extends ScalaPsiElement {
  def name: String
  def nameElement: ScPsiDocToken
  def valueElement: Option[PsiElement]
}