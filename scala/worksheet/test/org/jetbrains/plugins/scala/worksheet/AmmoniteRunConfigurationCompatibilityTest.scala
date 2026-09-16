package org.jetbrains.plugins.scala.worksheet

import com.intellij.execution.configurations.UnknownRunConfiguration
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert.assertTrue

class AmmoniteRunConfigurationCompatibilityTest extends ScalaLightCodeInsightFixtureTestCase {

  // SCL-25613, SCL-25932
  /**
   * Although Ammonite support was dropped, migrating a run configuration
   * created by an older plugin version must not cause an exception.
   *
   * This low-priority compatibility test may be removed in 2027.3.
   */
  def testLegacyConfigurationIsLoadedAsUnknown(): Unit = {
    assertLegacyConfigurationIsLoadedAsUnknown(createLegacyAmmoniteConfigurationElement())
  }

  private def assertLegacyConfigurationIsLoadedAsUnknown(legacyConfiguration: Element): Unit = {
    // Loading must not throw: IntelliJ preserves unrecognized configuration types.
    val loaded = RunManagerImpl.getInstanceImpl(getProject).loadConfiguration(legacyConfiguration, false)
    assertTrue(
      "Legacy Ammonite configurations must be preserved as unknown",
      loaded.getConfiguration.isInstanceOf[UnknownRunConfiguration]
    )
  }

  private def createLegacyAmmoniteConfigurationElement(): Element = {
    JDOMUtil.load(
      """<configuration name="Legacy Ammonite script" type="ScalaAmmoniteRunConfigurationType" factoryName="Ammonite">
         |  <option name="fileName" value="legacy.sc" />
         |</configuration>""".stripMargin
    )
  }
}
