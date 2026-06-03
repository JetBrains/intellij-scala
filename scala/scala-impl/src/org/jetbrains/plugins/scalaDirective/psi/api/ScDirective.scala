package org.jetbrains.plugins.scalaDirective.psi.api

import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScDirective extends ScalaPsiElement with PsiComment {
  def prefix: PsiElement

  def command: PsiElement

  def key: Option[PsiElement]

  def value: Option[PsiElement]
}
