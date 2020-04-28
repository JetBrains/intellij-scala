package org.jetbrains.plugins.scala.testingSupport.test

import java.awt.BorderLayout
import java.util

import com.intellij.execution.{CommonProgramRunConfigurationParameters, ExecutionBundle}
import com.intellij.execution.ui.CommonProgramParametersPanel
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.RawCommandLineEditor
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.testingSupport.test.testdata.TestConfigurationData

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

  /**
   * TODO: how it interferes with macro support in
   *  org.jetbrains.plugins.scala.testingSupport.test.ScalaTestFrameworkCommandLineState.expandPath  ??
   */
  override protected def isMacroSupportEnabled = false

  override protected def initMacroSupport(): Unit = super.initMacroSupport()

  def setVMParameters(text: String): Unit = myVMParametersComponent.getComponent.setText(text)

  def getVMParameters: String = myVMParametersComponent.getComponent.getText

  def getEnvironmentVariables: util.Map[String, String] = myEnvVariablesComponent.getEnvs

  def getProgramParameters: String = getProgramParametersComponent.getComponent.getText
}
