package org.jetbrains.plugins.scalaDirective
package psi.api

import com.intellij.psi.{PsiComment, PsiElement}
//import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScDirective extends /*Scala*/PsiElement with PsiComment {
  def key: Option[PsiElement]

  def value: Option[PsiElement]
}