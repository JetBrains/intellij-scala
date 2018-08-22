package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TestUtils

trait ScalaFmtTestBase extends AbstractScalaFormatterTestBase {
  override def setUp(): Unit = {
    super.setUp()
    getScalaSettings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
  }

  val configPath = TestUtils.getTestDataPath + "/formatter/scalafmt/"
}
