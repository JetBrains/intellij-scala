package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt

trait NameRenderer extends TextEscaper {
  def renderName(e: PsiNamedElement): String
  def renderNameWithPoint(e: PsiNamedElement): String
  def escapeName(name: String): String = name
  override final def escape(text: String): String = escapeName(text)
}

object NameRenderer {

  object Noop extends NameRenderer {
    override def renderName(e: PsiNamedElement): String = e.name
    override def renderNameWithPoint(e: PsiNamedElement): String = e.name
  }
}