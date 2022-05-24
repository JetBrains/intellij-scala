package org.jetbrains.plugins.scala.testingSupport.test.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ui.CommonProgramParametersPanel
import com.intellij.ide.`macro`.MacrosDialog
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.testingSupport.test.testdata.TestConfigurationData

import java.awt.BorderLayout
import java.util

/** based on [[com.intellij.execution.ui.CommonJavaParametersPanel]] */
class CommonScalaParametersPanel extends CommonProgramParametersPanel {

  private var myVMParametersComponent: LabeledComponent[RawCommandLineEditor] = _

  override protected def addComponents(): Unit = {
    myVMParametersComponent = LabeledComponent.create(new RawCommandLineEditor, ExecutionBundle.message("run.configuration.java.vm.parameters.label"))
    copyDialogCaption(myVMParametersComponent)
    myVMParametersComponent.setLabelLocation(BorderLayout.WEST)
    add(myVMParametersComponent)

    super.addComponents()
  }

  override protected def setupAnchor(): Unit = {
    super.setupAnchor()
    myAnchor = UIUtil.mergeComponentsWithAnchor(this, myVMParametersComponent)
  }

  def reset(configurationData: TestConfigurationData): Unit = {
    super.reset(configurationData)
    myVMParametersComponent.getComponent.setText(configurationData.javaOptions)
  }

  /** @see [[org.jetbrains.plugins.scala.testingSupport.test.ScalaTestFrameworkCommandLineState.VariablesExpander]] */
  override protected def isMacroSupportEnabled = true

  /** @see [[org.jetbrains.plugins.scala.testingSupport.test.ScalaTestFrameworkCommandLineState.VariablesExpander]] */
  override protected def initMacroSupport(): Unit = {
    updatePathMacros()
    addMacroSupport(myProgramParametersComponent.getComponent.getEditorField, MacrosDialog.Filters.ALL)
    addMacroSupport(myWorkingDirectoryField.getTextField.asInstanceOf[ExtendableTextField], MacrosDialog.Filters.DIRECTORY_PATH)
    addMacroSupport(myVMParametersComponent.getComponent.getEditorField, MacrosDialog.Filters.ALL)
  }

  def getVMParameters: String = myVMParametersComponent.getComponent.getText

  def getEnvironmentVariables: util.Map[String, String] = myEnvVariablesComponent.getEnvs

  def isPassParentEnvs: Boolean = myEnvVariablesComponent.isPassParentEnvs

  def getProgramParameters: String = getProgramParametersComponent.getComponent.getText
}
