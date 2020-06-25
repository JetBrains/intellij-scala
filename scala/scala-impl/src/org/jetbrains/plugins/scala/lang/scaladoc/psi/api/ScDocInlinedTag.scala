package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import com.intellij.psi.javadoc.PsiDocTagValue
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScDocInlinedTag extends ScalaPsiElement {
  // TODO: doesn't work
  def getValueElement: PsiDocTagValue

  def name: String
}