package org.jetbrains.plugins.scala.testingSupport.junit

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.junit.JUnitConfiguration
import org.jetbrains.plugins.scala.testingSupport.IntegrationTestConfigAssertions
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.testdata.AllInPackageTestData
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions.ObjectOps
import org.junit.Assert.assertEquals

trait JUnitIntegrationTestConfigAssertions extends IntegrationTestConfigAssertions {

  override protected def assertRunConfigTestPackage(configAndSettings: RunnerAndConfigurationSettings, packageName: String): Unit = {
    val config = configAndSettings.getConfiguration

    val actualConfigPackage = config match {
      case c: JUnitConfiguration =>
        c.getPersistentData.getPackageName
      case c: AbstractTestRunConfiguration =>
        c.testConfigurationData.assertInstanceOf[AllInPackageTestData].testPackagePath
    }

    assertEquals("package name are not equal", packageName, actualConfigPackage)
  }
}
