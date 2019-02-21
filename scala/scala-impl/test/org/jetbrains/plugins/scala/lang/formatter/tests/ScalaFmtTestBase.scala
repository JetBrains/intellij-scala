package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader.DownloadProgressListener
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TestUtils

trait ScalaFmtTestBase extends AbstractScalaFormatterTestBase {
  override def setUp(): Unit = {
    super.setUp()
    val scalaSettings = getScalaSettings
    scalaSettings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
    scalaSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = false
    scalaSettings.SCALAFMT_SHOW_INVALID_CODE_WARNINGS = false

    // emulate  `beforeAll` or `setupAll` that is not available in AbstractScalaFormatterTestBase
    ScalaFmtTestBase.initDefaultScalafmtVersion
  }

  val configPath = TestUtils.getTestDataPath + "/formatter/scalafmt/"

  def setScalafmtConfig(configFile: String): Unit =
    getScalaSettings.SCALAFMT_CONFIG_PATH = configPath + configFile
}

object ScalaFmtTestBase {
  // using val to emulate java static initializer,
  val initDefaultScalafmtVersion: Unit = {
    val log: Any => Unit = println
    val downloadingMessage = s"Downloading default scalafmt version ${ScalafmtDynamicService.DefaultVersion}"
    log(s"[START] $downloadingMessage")
    val stringToUnit: DownloadProgressListener = progressMessage => {
      log(progressMessage)
    }
    ScalafmtDynamicService.instance.ensureDefaultVersionIsResolved(stringToUnit)
    log(s"[END] $downloadingMessage")
  }
}