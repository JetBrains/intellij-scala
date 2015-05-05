package org.jetbrains.plugins.scala
package lang.refactoring.ui

import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaComboBoxVisibilityPanel._

/**
 * Nikolay.Tropin
 * 2014-09-01
 */
class ScalaComboBoxVisibilityPanel(additional: String*)
        extends ComboBoxVisibilityPanel[String](options(additional), names(additional))

object ScalaComboBoxVisibilityPanel {
  private def modifiers(additional: Seq[String]) =
    (Seq("private[this]", "private", "protected[this]", "protected") ++ additional.diff(Seq("", "public"))).sorted.distinct

  def options(additional: Seq[String]) = (modifiers(additional) :+ "").toArray
  def names(additional: Seq[String]) = (modifiers(additional) :+ "public").toArray
}
