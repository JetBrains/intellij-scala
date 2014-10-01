package org.jetbrains.plugins.scala
package lang.refactoring.ui

import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import ScalaComboBoxVisibilityPanel._

/**
 * Nikolay.Tropin
 * 2014-09-01
 */
class ScalaComboBoxVisibilityPanel(additional: String*)
        extends ComboBoxVisibilityPanel[String](options(additional), names(additional))

object ScalaComboBoxVisibilityPanel {
  private def modifiers(additional: Seq[String]) =
    (Seq("private[this]", "private", "protected[this]", "protected") ++ additional).sorted.toArray

  def options(additional: Seq[String]) = modifiers(additional) :+ ""
  def names(additional: Seq[String]) = modifiers(additional) :+ "public"
}
