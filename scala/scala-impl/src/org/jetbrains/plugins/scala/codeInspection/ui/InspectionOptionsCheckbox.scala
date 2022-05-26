package org.jetbrains.plugins.scala.codeInspection.ui

import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls

import java.awt.event.ItemEvent
import javax.swing.{JCheckBox, JPanel}

final class InspectionOptionsCheckbox(
  @Nls val label: String,
  isChecked: () => Boolean,
  setChecked: Boolean => Unit
) extends JPanel(new MigLayout("fillx, ins 0")) {
  private lazy val checkbox = new JCheckBox(label, isChecked())

  def check(value: Boolean): Unit = checkbox.setSelected(value)

  private def initialize(): Unit = {
    checkbox.addItemListener { e => setChecked(e.getStateChange == ItemEvent.SELECTED) }
    add(checkbox, "wrap, spanx")
  }

  initialize()
}
