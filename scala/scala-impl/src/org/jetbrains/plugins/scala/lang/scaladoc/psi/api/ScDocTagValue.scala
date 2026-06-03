package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import com.intellij.psi.javadoc.PsiDocTagValue
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPolyResolvable
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaDocTagValue

trait ScDocTagValue extends PsiDocTagValue with ScPolyResolvable with PsiNamedElement {

  override def setName(name: String): PsiElement = {
    replace(createScalaDocTagValue(name)(getManager))
    this
  }
}