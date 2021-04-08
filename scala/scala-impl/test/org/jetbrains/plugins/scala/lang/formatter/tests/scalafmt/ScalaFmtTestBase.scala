package org.jetbrains.plugins.scala.lang.formatter.tests.scalafmt

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
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

    scalaSettings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS = true
    getScalaSettings.SCALAFMT_CONFIG_PATH = ""
    configIsSet = false

    // emulate  `beforeAll` or `setupAll` that is not available in AbstractScalaFormatterTestBase
    ScalaFmtTestBase.ensureScalafmtVersionsDownloaded
  }

  val configPath = TestUtils.getTestDataPath + "/formatter/scalafmt/"

  private var configIsSet = false

  final def setScalafmtConfig(configFile: String): Unit = {
    if (configIsSet)
      throw new AssertionError("scalafmt config should be set only once")
    getScalaSettings.SCALAFMT_CONFIG_PATH = configPath + configFile
    configIsSet = true
  }

  override protected def prepareText(actual: String): String =
    actual
}

object ScalaFmtTestBase {
  // using val to emulate java static initializer,
  val ensureScalafmtVersionsDownloaded: Unit = {
    val versions = Seq(
      DefaultVersion,
      "2.7.5",
      "2.5.3",
    )
    versions.foreach(ensureDownloaded)
  }

  private def ensureDownloaded(version: String): Unit = {
    val log: Any => Unit = println

    val defaultNote = if (version == DefaultVersion) " (default)" else ""
    val downloadingMessage = s"Downloading scalafmt version $version$defaultNote"
    log(s"[START] $downloadingMessage")
    val stringToUnit: DownloadProgressListener = progressMessage => {
      //log(progressMessage) // uncomment to test
    }
    ScalafmtDynamicService.instance.ensureVersionIsResolved(version, stringToUnit)
    log(s"[END] $downloadingMessage")
  }
}