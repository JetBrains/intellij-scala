package org.jetbrains.plugins.scala.codeInspection.ui

import org.junit.Test

class InspectionOptionsTest {
  @Test
  def test_create_two_options(): Unit = {
    val inspectionOptions = InspectionOptions("twoOptions", "Two Options")
    assert(inspectionOptions.options.size == 2)
  }

  @Test
  def test_create_three_options_with_compiler_option(): Unit = {
    val optionName = "-Xlint:someOption"
    val inspectionOptions = InspectionOptions("threeOptions", "Three Options", optionName)
    assert(inspectionOptions.options.size == 3)
    assert(inspectionOptions.options.exists(_.label.contains(optionName)))
  }

  @Test
  def test_create_combobox_with_options(): Unit = {
    val optionName = "-Xlint:someOption"
    val inspectionOptions = InspectionOptions("threeOptions", "Three Options", optionName)
    val comboBox: InspectionOptionsCombobox = inspectionOptions.comboBox
    assert(comboBox.options.size == 3)
    assert(comboBox.options.exists(_.label.contains(optionName)))
  }

  @Test
  def test_create_checkbox_with_options(): Unit = {
    val label = "Two Options"
    val inspectionOptions = InspectionOptions("twoOptions", label)
    val checkBox: InspectionOptionsCheckbox = inspectionOptions.checkBox
    assert(checkBox.label == label)
  }

  @Test
  def test_select_from_combobox(): Unit = {
    val inspectionOptions = InspectionOptions("threeOptions", "Three options", "-Xlint:someOption")
    // The first option is default
    assert(inspectionOptions.getSelected == 0)

    val comboBox = inspectionOptions.comboBox
    comboBox.setOption(1)
    assert(inspectionOptions.getSelected == 1)

    comboBox.setOption(2)
    assert(inspectionOptions.getSelected == 2)
  }

  @Test
  def test_is_enabled_from_combobox(): Unit = {
    val alwaysTrue = InspectionOption("0", _ => true)
    val alwaysFalse = InspectionOption("1", _ => false)
    var customEnabled = false
    val custom = InspectionOption("2", _ => customEnabled)

    val inspectionOptions = new InspectionOptions("threeOptions", "Three options", Seq(alwaysTrue, alwaysFalse, custom))
    assert(inspectionOptions.isEnabled(null))

    val comboBox = inspectionOptions.comboBox
    comboBox.setOption(1)
    assert(!inspectionOptions.isEnabled(null))

    comboBox.setOption(2)
    assert(!inspectionOptions.isEnabled(null)) // customEnabled is false
    customEnabled = true
    assert(inspectionOptions.isEnabled(null))
  }

  @Test
  def test_select_from_checkbox(): Unit = {
    val inspectionOptions = InspectionOptions("twoOptions", "Two Options")
    // The first option means "checked"
    assert(inspectionOptions.isChecked)

    val checkBox = inspectionOptions.checkBox
    checkBox.check(false)
    assert(!inspectionOptions.isChecked)
    checkBox.check(true)
    assert(inspectionOptions.isChecked)
  }
}
