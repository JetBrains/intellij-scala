package org.jetbrains.sbt
package project.module

import java.awt.{GridBagConstraints, GridBagLayout, FlowLayout, BorderLayout}
import java.util
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

import com.intellij.openapi.roots.ui.configuration.{ModuleConfigurationState, ModuleElementsEditor}
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.{JBTextField, JBLabel, JBList}
import com.intellij.ui.{CollectionListModel, ToolbarDecorator}
import com.intellij.uiDesigner.compiler.GridBagLayoutCodeGenerator
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
import com.intellij.util.ui.UIUtil
import com.jgoodies.forms.layout.FormLayout
import org.jetbrains.plugins.scala.util.JListCompatibility
import org.jetbrains.plugins.scala.util.JListCompatibility.CollectionListModelWrapper
import org.jetbrains.sbt.project.settings.SbtSettings

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtModuleImportsEditor(state: ModuleConfigurationState) extends ModuleElementsEditor(state) {
  private val myForm = new SbtModuleImportsForm
  private val modelWrapper = new CollectionListModelWrapper(new CollectionListModel[String](new util.ArrayList[String]))

  override def getDisplayName = SbtBundle("sbt.settings.importsAndSbtVersion")

  override def getHelpTopic = null

  override def createComponentImpl() = {
    myForm.sbtImportsList.setEmptyText(SbtBundle("sbt.settings.noImplicitImportsFound"))
    myForm.sbtImportsList.setModel(modelWrapper.getModelRaw)
    myForm.mainPanel
  }

  override def reset() {
    modelWrapper.getModel.replaceAll(importsInModule.asJava)
    myForm.sbtVersionTextField.setText(Option(SbtSettings.getInstance(state.getProject).sbtVersion).getOrElse("Not detected"))
  }

  override def saveData() {
    SbtModule.setImportsTo(getModel.getModule, importsInList)
  }

  override def isModified = importsInList != importsInModule

  private def importsInModule: Seq[String] = SbtModule.getImportsFrom(getModel.getModule)

  private def importsInList: Seq[String] = modelWrapper.getModel.getItems.asScala
}