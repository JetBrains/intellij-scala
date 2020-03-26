package org.jetbrains.bsp.project.test.environment
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.junit.JUnitConfiguration

class JUnitClassExtractor extends RunConfigurationClassExtractor {
  override def runConfigurationSupported(config: RunConfiguration): Boolean =
    config.isInstanceOf[JUnitConfiguration]

  override def classes(config: RunConfiguration): Option[List[String]] = {
    config match {
      case jUnitConfig: JUnitConfiguration =>
        jUnitConfig.getTestType match {
          case JUnitConfiguration.TEST_METHOD | JUnitConfiguration.TEST_CLASS =>
            Some(List(jUnitConfig.getPersistentData.getMainClassName))
          case _ => None
        }
      case _ => None
    }
  }
}

