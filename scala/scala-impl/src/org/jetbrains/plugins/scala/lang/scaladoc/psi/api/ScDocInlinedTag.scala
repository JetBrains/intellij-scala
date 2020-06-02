package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import com.intellij.psi.javadoc.PsiDocTagValue

trait ScDocInlinedTag extends ScalaPsiElement {
  def getValueElement: PsiDocTagValue
}