package org.jetbrains.sbt
package project.module

import com.intellij.util.ui.UIUtil
import com.intellij.ui.components.{JBLabel, JBList}
import com.intellij.ui.{ToolbarDecorator, CollectionListModel}
import com.intellij.openapi.roots.ui.configuration.{ModuleConfigurationState, ModuleElementsEditor}
import javax.swing.border.EmptyBorder
import javax.swing.{JPanel, ListModel, JList, DefaultListModel}
import java.awt.BorderLayout
import java.util
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtModuleImportsEditor(state: ModuleConfigurationState) extends ModuleElementsEditor(state) {
  private val model = new CollectionListModel[String](new util.ArrayList[String]())

  override def getDisplayName = "Imports"

  override def getHelpTopic = null

  override def createComponentImpl() = {
    val listPanel = {
      val list =  new JBList(new DefaultListModel[String]())
      list.setEmptyText("No implicit imports")
      list.asInstanceOf[JList[String]].setModel(model.asInstanceOf[ListModel[String]])
      ToolbarDecorator.createDecorator(list, model).createPanel()
    }

    val mainPanel = new JPanel(new BorderLayout())
    mainPanel.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS))
    mainPanel.add(new JBLabel("SBT implicit imports", UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.NORMAL), BorderLayout.NORTH)
    mainPanel.add(listPanel, BorderLayout.CENTER)

    mainPanel
  }

  override def reset() {
    model.replaceAll(importsInModule.asJava)
  }

  override def saveData() {
    SbtModule.setImportsTo(getModel.getModule, importsInList)
  }

  override def isModified = importsInList != importsInModule

  private def importsInModule: Seq[String] = SbtModule.getImportsFrom(getModel.getModule)

  private def importsInList: Seq[String] = model.getItems.asScala
}