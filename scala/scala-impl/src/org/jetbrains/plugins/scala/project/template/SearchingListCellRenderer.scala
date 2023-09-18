package org.jetbrains.plugins.scala.project.template

import com.intellij.ui.components.JBLabel
import com.intellij.ui.{AnimatedIcon, CellRendererPanel}

import java.awt.{BorderLayout, Component}
import java.util.concurrent.atomic.AtomicBoolean
import javax.accessibility.AccessibleContext
import javax.swing.{DefaultListCellRenderer, JList}

class SearchingListCellRenderer[T](isSearching: AtomicBoolean, textCustomizer: Option[T => String] = None) extends DefaultListCellRenderer(){

  override def getListCellRendererComponent(list: JList[_ <: AnyRef], value: scala.Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    textCustomizer.foreach(customizer => setText(customizer(value.asInstanceOf[T])))

    if (index == -1 && isSearching.get()) {
      val panel = new CellRendererPanel(new BorderLayout()) {
        override def getAccessibleContext: AccessibleContext = component.getAccessibleContext
      }
      component.setBackground(null)
      panel.add(component, BorderLayout.WEST)
      val progressIcon = new JBLabel(AnimatedIcon.Default.INSTANCE)
      panel.add(progressIcon, BorderLayout.EAST)
      return panel
    }
    component
  }

}
