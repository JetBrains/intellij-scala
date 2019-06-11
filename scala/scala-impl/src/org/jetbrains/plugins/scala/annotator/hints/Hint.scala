package org.jetbrains.plugins.scala.annotator.hints

import java.awt.Insets

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt

case class Hint(parts: Seq[Text],
                element: PsiElement,
                suffix: Boolean,
                menu: Option[String] = None,
                margin: Option[Insets] = None,
                relatesToPrecedingElement: Boolean = false) { //gives more natural behaviour

  // We want auto-generate apply() and copy() methods, but reference-based equality
  override def equals(obj: scala.Any): Boolean = obj.asOptionOf[AnyRef].exists(eq)
}