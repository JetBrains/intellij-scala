package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import com.intellij.psi.javadoc.PsiDocTagValue
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createDocTagValue

trait ScDocTagValue extends PsiDocTagValue with PsiReference with PsiNamedElement {

  override def setName(name: String): PsiElement = {
    replace(createDocTagValue(name)(getManager))
    this
  }
}