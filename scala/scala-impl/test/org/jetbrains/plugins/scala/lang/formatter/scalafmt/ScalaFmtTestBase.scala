package org.jetbrains.plugins.scala.lang.formatter.scalafmt

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader.DownloadProgressListener
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.assertTrue
import org.scalafmt.dynamic.ScalafmtVersion

import java.nio.file.Path
import scala.collection.mutable

trait ScalaFmtTestBase extends AbstractScalaFormatterTestBase with ScalaFmtForTestsSetupOps {

  override protected def scalafmtConfigsBasePath: Path =
    Path.of(TestUtils.getTestDataPath, "formatter", "scalafmt")

  override def setUp(): Unit = {
    super.setUp()
    ScalaFmtForTestsSetupOps.ensureDownloaded(
      DefaultVersion,
      ScalafmtVersion.parse("3.8.3").get,
      ScalafmtVersion(2, 7, 5),
      ScalafmtVersion(2, 5, 3)
    )
  }

  override protected def prepareText(actual: String): String =
    actual
}

trait ScalaFmtSelectionTestBase extends ScalaFmtTestBase {
  override def setUp(): Unit = {
    super.setUp()
    scalaSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = false
  }
}

trait ScalaFmtForTestsSetupOps extends UsefulTestCase {

  protected def getScalaCodeStyleSettings: ScalaCodeStyleSettings

  override def setUp(): Unit = {
    super.setUp()
    val scalaSettings = getScalaCodeStyleSettings
    scalaSettings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
    scalaSettings.SCALAFMT_SHOW_INVALID_CODE_WARNINGS = false

    scalaSettings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS = true
    scalaSettings.SCALAFMT_CONFIG_PATH = ""
    configIsSet = false
  }

  protected def scalafmtConfigsBasePath: Path

  protected def getProject: Project

  private var configIsSet = false

  final def setScalafmtConfig(configFileName: String): Unit = {
    if (configIsSet)
      throw new AssertionError("scalafmt config should be set only once")
    val configFile = scalafmtConfigsBasePath / configFileName
    assertTrue(
      s"""Scalafmt config file doesn't exist: $configFile.
         |Possible files in parent directory:
         |${configFile.getParent.children().mkString("\n")}""".stripMargin,
      configFile.exists
    )
    getScalaCodeStyleSettings.SCALAFMT_CONFIG_PATH = configFile.toString
    configIsSet = true

    // This is mostly needed during local testing when you add new config files
    // Without this call, we would need to drop the "test_system" directory
    // because IntelliJ wouldn't detect any changes in the test data directories
    StandardFileSystems.local().refresh(false)
  }
}

object ScalaFmtForTestsSetupOps {

  def ensureDownloaded(versions: ScalafmtVersion*): Unit = {
    versions.foreach(ensureDownloadedSingle)
  }

  private val alreadyDownloadedVersions = mutable.HashSet.empty[ScalafmtVersion]

  private def ensureDownloadedSingle(version: ScalafmtVersion): Unit = synchronized {
    if (alreadyDownloadedVersions.contains(version))
      return

    val log: Any => Unit = println

    val defaultNote = if (version == DefaultVersion) " (default)" else ""
    val downloadingMessage = s"Downloading scalafmt version $version$defaultNote"
    log(s"[START] $downloadingMessage")
    val stringToUnit: DownloadProgressListener = progressMessage => {
      //log(progressMessage) // uncomment to test
    }
    ScalafmtDynamicService.instance.ensureVersionIsResolved(version, Nil, stringToUnit)
    log(s"[END] $downloadingMessage")

    alreadyDownloadedVersions += version
  }
}
