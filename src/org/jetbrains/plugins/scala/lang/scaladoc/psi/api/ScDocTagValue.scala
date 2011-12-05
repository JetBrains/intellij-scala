package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package api

import com.intellij.psi.javadoc.PsiDocTagValue
import com.intellij.psi.PsiReference

/**
 * User: Dmitry Naydanov
 * Date: 11/23/11
 */

trait ScDocTagValue extends PsiDocTagValue with PsiReference  {
  def getValue: String
}