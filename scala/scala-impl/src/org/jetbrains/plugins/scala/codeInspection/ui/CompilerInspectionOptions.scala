package org.jetbrains.plugins.scala.codeInspection.ui

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.ui.InspectionOptionsComboboxPanel.{AlwaysEnabled, ComplyToCompilerOption}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.project.ModuleExt

object CompilerInspectionOptions {
  implicit class InspectionOptionsComboboxPanelExt(val panel: InspectionOptionsComboboxPanel) extends AnyVal {
    def addCombobox(label:              String,
                    compilerOptionName: String,
                    getSelectedIndex:   () => Int,
                    setSelectedIndex:   Int => Unit): Unit =
      panel.addCombobox(label, createOptions(compilerOptionName), getSelectedIndex, setSelectedIndex)
  }

  def isInspectionAllowed(elem: ScalaPsiElement, checkboxValue: Boolean, compilerOptionName: String): Boolean =
    !checkboxValue || elem.module.exists(_.scalaCompilerSettings.additionalCompilerOptions.contains(compilerOptionName))

  def isInspectionAllowed(elem: ScalaPsiElement, comboboxValue: Int, compilerOptionName: String): Boolean =
    comboboxValue match {
      case AlwaysEnabled          => true
      case ComplyToCompilerOption => elem.module.exists(_.scalaCompilerSettings.additionalCompilerOptions.contains(compilerOptionName))
      case _                      => false
    }

  def comboboxForCompilerOption(label:              String,
                                compilerOptionName: String,
                                getSelectedIndex:   () => Int,
                                setSelectedIndex:   Int => Unit): InspectionOptionsComboboxPanel =
    InspectionOptionsComboboxPanel(label, createOptions(compilerOptionName), getSelectedIndex, setSelectedIndex)

  private def createOptions(compilerOptionName: String): Seq[String] =
    Seq(
      ScalaInspectionBundle.message("inspection.option.enabled"),
      ScalaInspectionBundle.message("inspection.option.check.compiler", compilerOptionName),
      ScalaInspectionBundle.message("inspection.option.disabled")
    )
}
