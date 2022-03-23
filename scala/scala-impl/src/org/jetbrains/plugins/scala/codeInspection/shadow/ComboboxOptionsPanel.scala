package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.ui.InspectionOptionsPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ReflectionUtil
import org.jetbrains.sbt.project.template.SComboBox

import java.awt.event.ItemEvent
import javax.swing.JLabel
import scala.util.chaining.scalaUtilChainingOps


class ComboboxOptionsPanel(@NlsContexts.Label label: String,
                           owner: InspectionProfileEntry,
                           property: String,
                           options: Map[Int, String],
                           defaultItem: Int = 0
                          ) extends InspectionOptionsPanel {
  private val component = new SComboBox(options.values.toArray).tap { cb =>
    cb.setSelectedItemSafe(options(defaultItem))
    cb.addItemListener((_: ItemEvent) =>
      cb.getSelectedItemTyped.flatMap {
        selectedStr => options.collectFirst { case (id, str) if selectedStr == str => id }
      }.foreach {
        ReflectionUtil.setField(owner.getClass, owner, classOf[Int], property, _)
      }
    )
  }

  private val title = new JLabel(label).tap { _.setLabelFor(component) }
  add(title)
  addComponent(component)
}
