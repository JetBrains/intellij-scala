package org.jetbrains.plugins.scala.codeInspection.ui

import org.jdom.Element
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.project.ModuleExt

import scala.util.Try

final case class InspectionOption(label: String, isEnabled: ScNamedElement => Boolean)

final class InspectionOptions(
  val propertyName:          String,
  val label:                 String,
  val options:               Seq[InspectionOption],
  private var selectedIndex: Int = 0
) {
  def readSettings(node: Element): Unit =
    Try(node.getAttributeValue(propertyName).toInt).foreach { selectedIndex = _ }

  def writeSettings(node: Element): Unit =
    node.setAttribute(propertyName, selectedIndex.toString)

  def isEnabled(element: ScNamedElement): Boolean =
    options(selectedIndex).isEnabled(element)

  def setSelected(index: Int): Unit =
    selectedIndex = index

  def getSelected: Int = selectedIndex

  def comboBox: InspectionOptionsCombobox =
    new InspectionOptionsCombobox(
      label            = label,
      options          = options,
      getSelectedIndex = () => getSelected,
      setSelectedIndex = setSelected
    )

  def setChecked(checked: Boolean): Unit =
    selectedIndex = if (checked) 0 else 1

  def isChecked: Boolean = selectedIndex == 0

  def checkBox: InspectionOptionsCheckbox =
    new InspectionOptionsCheckbox(
      label      = label,
      isChecked  = () => isChecked,
      setChecked = setChecked
    )
}

object InspectionOptions {
  def apply(propertyName: String, label: String): InspectionOptions =
    new InspectionOptions(
      propertyName,
      label,
      Seq(
        InspectionOption(ScalaInspectionBundle.message("inspection.option.enabled"), _ => true),
        InspectionOption(ScalaInspectionBundle.message("inspection.option.disabled"), _ => false)
      )
    )

  def apply(propertyName: String, label: String, compilerOptionName: String): InspectionOptions = {
    val isCompilerOptionEnabled: ScNamedElement => Boolean = { elem =>
      elem.module.exists(_.scalaCompilerSettings.additionalCompilerOptions.contains(compilerOptionName))
    }
    new InspectionOptions(
      propertyName,
      label,
      Seq(
        InspectionOption(ScalaInspectionBundle.message("inspection.option.enabled"), _ => true),
        InspectionOption(ScalaInspectionBundle.message("inspection.option.check.compiler", compilerOptionName), isCompilerOptionEnabled),
        InspectionOption(ScalaInspectionBundle.message("inspection.option.disabled"), _ => false)
      )
    )
  }
}
