package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import com.intellij.psi.javadoc.PsiDocTagValue
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaDocTagValue

trait ScDocTagValue extends PsiDocTagValue with PsiReference with PsiNamedElement {

  override def setName(name: String): PsiElement = {
    replace(createScalaDocTagValue(name)(getManager))
    this
  }
}