package org.jetbrains.plugins.scala.codeInspection.ui

import org.junit.Test

class InspectionOptionsComboboxTest {
  @Test
  def test_two_options_combobox_is_created(): Unit = {
    var index = 0

    val trueOption = InspectionOption("true", _ => true)
    val falseOption = InspectionOption("false", _ => false)

    val combobox = new InspectionOptionsCombobox(
      label            = "combobox",
      options          = Seq(trueOption, falseOption),
      getSelectedIndex = () => index,
      setSelectedIndex = i => { index = i }
    )

    assert(combobox.options.size == 2)
  }

  @Test
  def test_setting_option_updates_index(): Unit = {
    var index = 0

    val option0 = InspectionOption("0", _ => true)
    val option1 = InspectionOption("1", _ => false)

    val combobox = new InspectionOptionsCombobox(
      label            = "combobox",
      options          = Seq(option0, option1),
      getSelectedIndex = () => index,
      setSelectedIndex = i => { index = i }
    )

    assert(index == 0)
    combobox.setOption(1)
    assert(index == 1)
    combobox.setOption(0)
    assert(index == 0)
  }

  @Test
  def test_setting_option_outside_range_does_nothing(): Unit = {
    var index = 0

    val option0 = InspectionOption("0", _ => true)
    val option1 = InspectionOption("1", _ => false)

    val combobox = new InspectionOptionsCombobox(
      label            = "combobox",
      options          = Seq(option0, option1),
      getSelectedIndex = () => index,
      setSelectedIndex = i => { index = i }
    )

    assert(combobox.options.size == 2)
    assert(index == 0)
    combobox.setOption(1)
    assert(index == 1)
    combobox.setOption(2)
    assert(index == 1)
  }
}
