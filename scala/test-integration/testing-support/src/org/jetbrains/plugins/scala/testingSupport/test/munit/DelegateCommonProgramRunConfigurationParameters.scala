package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.execution.CommonProgramRunConfigurationParameters
import org.jetbrains.plugins.scala.testingSupport.test.testdata.TestConfigurationData

import java.util

trait DelegateCommonProgramRunConfigurationParameters {
  self: CommonProgramRunConfigurationParameters =>

  protected def delegateToTestData: TestConfigurationData

  @inline private def data: TestConfigurationData = delegateToTestData

  override def setProgramParameters(value: String): Unit = data.setProgramParameters(value)
  override def getProgramParameters: String = data.getProgramParameters

  override def setWorkingDirectory(value: String): Unit = data.setWorkingDirectory(value)
  override def getWorkingDirectory: String = data.getWorkingDirectory

  override def setEnvs(envs: util.Map[String, String]): Unit = data.setEnvs(envs)
  override def getEnvs: util.Map[String, String] = data.getEnvs

  override def setPassParentEnvs(passParentEnvs: Boolean): Unit = data.setPassParentEnvs(passParentEnvs)
  override def isPassParentEnvs: Boolean = data.isPassParentEnvs
}
