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
  private lazy val component = new SComboBox(options.map(_._2).toArray)

  private def initialize(): Unit = {
    options.toMap.get(readProperty()).foreach(component.setSelectedItemSafe)
    component.addItemListener((_: ItemEvent) =>
      component
        .getSelectedItemTyped
        .flatMap { selectedStr => options.collectFirst { case (id, str) if selectedStr == str => id } }
        .foreach(writeProperty)
    )

    val title = new JLabel(label)
    title.setLabelFor(component)
    add(title)

    addComponent(component)
  }

  initialize()
}
