package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings.gui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.project.Project
import javax.swing.{JPanel, JComponent}
import com.intellij.uiDesigner.core.GridLayoutManager

/**
 * Created by Roman.Shein on 17.07.2014.
 */
class ExampleChoiceDialog(val project: Project) extends DialogWrapper(project) {
  private val contentPane: JPanel = new JPanel(new GridLayoutManager(2, 2))

  override def createCenterPanel(): JComponent = contentPane
}
