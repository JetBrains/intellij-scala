package org.jetbrains.plugins.scala.codeInspection.ui

import net.miginfocom.swing.MigLayout
import org.jetbrains.sbt.project.template.SComboBox

import javax.swing.{JLabel, JPanel}

final class InspectionOptionsCombobox(val label:        String,
                                      val options:      Seq[InspectionOption],
                                      getSelectedIndex: () => Int,
                                      setSelectedIndex: Int => Unit
                                     ) extends JPanel(new MigLayout("fillx, ins 0")) {
  private lazy val combobox = new SComboBox(options.toArray)

  def setOption(selectedIndex: Int): Unit =
    options
      .lift(selectedIndex)
      .foreach(combobox.setSelectedItemSafe)

  private def setIndex(option: InspectionOption): Unit =
    options
      .zipWithIndex
      .collectFirst { case (opt, index) if opt == option => index }
      .foreach(setSelectedIndex)

  private def initialize(): Unit = {
    setOption(getSelectedIndex())
    combobox.addItemListener { _ => combobox.getSelectedItemTyped.foreach(setIndex) }
    combobox.setTextRenderer2(_.label)

    val title = new JLabel(label)
    title.setLabelFor(combobox)
    add(title, "")
    add(combobox, "wrap, spanx")
  }

  initialize()
}
