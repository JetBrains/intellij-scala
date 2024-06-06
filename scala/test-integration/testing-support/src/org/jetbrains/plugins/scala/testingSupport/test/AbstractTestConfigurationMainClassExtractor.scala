package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.configurations.ModuleBasedConfiguration
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{ClassTestData, TestConfigurationData}
import org.jetbrains.sbt.project.extensionPoints.ModuleBasedConfigurationMainClassExtractor

class AbstractTestConfigurationMainClassExtractor extends ModuleBasedConfigurationMainClassExtractor {

  override def getConfigurationMainClass(config: ModuleBasedConfiguration[_, _]): Option[String] =
    config match {
      case x: AbstractTestRunConfiguration => getMainClassFromConfigurationData(x.testConfigurationData)
      case _ => None
    }

  private def getMainClassFromConfigurationData(data: TestConfigurationData): Option[String] =
    data match {
      case data: ClassTestData =>
        val testClasspath = data.testClassPath
        Option(testClasspath).filter(StringUtils.isNotBlank)
      case _ => None
    }
}
