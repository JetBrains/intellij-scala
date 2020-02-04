package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package api

import com.intellij.psi.javadoc.PsiDocTagValue
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createDocTagValue

/**
 * User: Dmitry Naydanov
 * Date: 11/23/11
 */
trait ScDocTagValue extends PsiDocTagValue with PsiReference with PsiNamedElement {

  override def setName(name: String): PsiElement = {
    replace(createDocTagValue(name)(getManager))
    this
  }
}