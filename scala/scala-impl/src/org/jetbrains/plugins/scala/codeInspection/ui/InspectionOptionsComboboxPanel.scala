package org.jetbrains.plugins.scala.codeInspection.ui

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.ui.InspectionOptionsPanel
import org.jetbrains.sbt.project.template.SComboBox

import javax.swing.JLabel

class InspectionOptionsComboboxPanel(owner: InspectionProfileEntry) extends InspectionOptionsPanel(owner) {
  def addCombobox(label:            String,
                  options:          Seq[String],
                  getSelectedIndex: () => Int,
                  setSelectedIndex: Int => Unit): Unit = {
    val combobox = new SComboBox(options.toArray)

    def setOption(selectedIndex: Int): Unit =
      options
        .lift(selectedIndex)
        .foreach(combobox.setSelectedItemSafe)

    def setIndex(label: String): Unit =
      options
        .zipWithIndex
        .collectFirst { case (opt, index) if opt == label => index }
        .foreach(setSelectedIndex)

    setOption(getSelectedIndex())
    combobox.addItemListener { _ => combobox.getSelectedItemTyped.foreach(setIndex) }

    val title = new JLabel(label)
    title.setLabelFor(combobox)
    add(title, "")
    add(combobox, "wrap, spanx")
  }
}

