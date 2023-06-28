package org.jetbrains.plugins.scala.lang.scalacli.psi.api

import com.intellij.psi.PsiComment
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.scalacli.psi.api.inner.{ScCliDirectiveCommand, ScCliDirectiveKey, ScCliDirectiveValue}

trait ScCliDirective extends ScalaPsiElement with PsiComment {
  def command: Option[ScCliDirectiveCommand]

  def key: Option[ScCliDirectiveKey]

  def values: Seq[ScCliDirectiveValue]
}
