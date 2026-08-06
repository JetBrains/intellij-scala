package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.execution.CommonJavaRunConfigurationParameters

trait DelegateCommonJavaRunConfigurationParameters
  extends DelegateCommonProgramRunConfigurationParameters {
  self: CommonJavaRunConfigurationParameters =>

  private var _alternativeJrePathEnabled: Boolean = false;

  override def isAlternativeJrePathEnabled: Boolean = _alternativeJrePathEnabled
  override def setAlternativeJrePathEnabled(enabled: Boolean): Unit = _alternativeJrePathEnabled = enabled

  override def getAlternativeJrePath: String = delegateToTestData.jrePath
  override def setAlternativeJrePath(path: String): Unit = delegateToTestData.jrePath = path

  override def setVMParameters(value: String): Unit = delegateToTestData.javaOptions = value
  override def getVMParameters: String = delegateToTestData.javaOptions

  override def getRunClass: String = null
  override def getPackage: String = null
}
