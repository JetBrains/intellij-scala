package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package api

import com.intellij.psi.javadoc.PsiDocTagValue
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * User: Dmitry Naydanov
 * Date: 11/23/11
 */

trait ScDocTagValue extends PsiDocTagValue with PsiReference with PsiNamedElement {
  def getValue: String

//  def getName: String = getText

  def setName(name: String): PsiElement = {
    replace(ScalaPsiElementFactory.createDocTagValue(name, getManager))
    this
  }
}