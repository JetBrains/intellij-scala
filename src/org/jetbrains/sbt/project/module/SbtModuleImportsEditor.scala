package org.jetbrains.sbt
package project.module

import java.awt.BorderLayout
import java.util
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

import com.intellij.openapi.roots.ui.configuration.{ModuleConfigurationState, ModuleElementsEditor}
import com.intellij.ui.components.{JBLabel, JBList}
import com.intellij.ui.{CollectionListModel, ToolbarDecorator}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.util.JListCompatibility
import org.jetbrains.plugins.scala.util.JListCompatibility.CollectionListModelWrapper

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtModuleImportsEditor(state: ModuleConfigurationState) extends ModuleElementsEditor(state) {
  private val modelWrapper = new CollectionListModelWrapper(new CollectionListModel[String](new util.ArrayList[String]))

  override def getDisplayName = "Imports"

  override def getHelpTopic = null

  override def createComponentImpl() = {
    val listPanel = {
      val list = JListCompatibility.createJBListFromModel(JListCompatibility.createDefaultListModel())
      list.asInstanceOf[JBList].setEmptyText("No implicit imports")
      JListCompatibility.setModel(list, modelWrapper.getModelRaw)
      ToolbarDecorator.createDecorator(list, modelWrapper.getModel).createPanel()
    }

    val mainPanel = new JPanel(new BorderLayout())
    mainPanel.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS))
    mainPanel.add(new JBLabel("SBT implicit imports", UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.NORMAL), BorderLayout.NORTH)
    mainPanel.add(listPanel, BorderLayout.CENTER)

    mainPanel
  }

  override def reset() {
    modelWrapper.getModel.replaceAll(importsInModule.asJava)
  }

  override def saveData() {
    SbtModule.setImportsTo(getModel.getModule, importsInList)
  }

  override def isModified = importsInList != importsInModule

  private def importsInModule: Seq[String] = SbtModule.getImportsFrom(getModel.getModule)

  private def importsInList: Seq[String] = modelWrapper.getModel.getItems.asScala
}