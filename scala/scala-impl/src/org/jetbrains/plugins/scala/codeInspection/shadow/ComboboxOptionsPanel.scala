package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInspection.ui.InspectionOptionsPanel
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.sbt.project.template.SComboBox

import java.awt.event.ItemEvent
import javax.swing.JLabel

class ComboboxOptionsPanel[Id](@NlsContexts.Label label: String,
                               options:                  Seq[(Id, String)],
                               readProperty:             () => Id,
                               writeProperty:            Id => Unit
                              ) extends InspectionOptionsPanel {
  private lazy val component = new SComboBox[String]()

  private def initialize(): Unit = {
    component.setItems(options.map(_._2).toArray)
    options.toMap.get(readProperty()).foreach(component.setSelectedItemSafe)
    component.addItemListener((_: ItemEvent) =>
      component
        .getSelectedItemTyped
        .flatMap { selectedStr => options.collectFirst { case (id, str) if selectedStr == str => id } }
        .foreach(writeProperty)
    )

    val title = new JLabel(label)
    title.setLabelFor(component)
    add(title, "span, wrap")

    add(component, "span, wrap")
  }

  initialize()
}
