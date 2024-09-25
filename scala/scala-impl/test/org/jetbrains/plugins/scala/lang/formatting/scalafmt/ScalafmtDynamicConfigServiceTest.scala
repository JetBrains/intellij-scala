package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.jetbrains.plugins.scala.lang.formatter.scalafmt.ScalaFmtTestBase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}
import org.scalafmt.dynamic.ScalafmtReflectConfig

import java.io.File

class ScalafmtDynamicConfigServiceTest extends ScalaFmtTestBase {

  private val originalScalafmtConfigurationsDir: File =
    new File(TestUtils.getTestDataPath + "/formatter/scalafmt/config_service_test_data/")

  private var tempConfigurationsDir: File = _

  override protected def scalafmtConfigsBasePath: String =
    tempConfigurationsDir.toString

  override def setUp(): Unit = {
    super.setUp()

    // Copy configuration files to a temporary directory.
    // This is necessary because the current test will make modifications in the configuration file,
    // and we don't want the test data to be modified in VCS
    tempConfigurationsDir = FileUtil.createTempDirectory("scalafmtConfigTests", "", true)
    originalScalafmtConfigurationsDir.listFiles().foreach { file =>
      FileUtil.copyFileOrDir(
        file,
        new File(tempConfigurationsDir, file.getName)
      )
    }
  }

  private def findConfigurationDocument(configFileName: String): Document = {
    val vFile = VirtualFileManager.getInstance().findFileByNioPath(new File(tempConfigurationsDir, configFileName).toPath)
    assertNotNull("Can't resolve scalafmt config common configuration file", vFile)
    val psiFile = PsiManager.getInstance(getProject).findFile(vFile)
    PsiDocumentManager.getInstance(getProject).getDocument(psiFile)
  }

  private def appendTextToConfiguration(configFileName: String, textToAppend: String): Unit = {
    val document = findConfigurationDocument(configFileName)
    inWriteCommandAction {
      document.setText(
        s"""${document.getText()}
           |$textToAppend""".stripMargin
      )
    }
  }

  private def assertThatConfigurationInstanceIsTheSameWithoutModificationsInConfigurations(config1: ScalafmtReflectConfig, config2: ScalafmtReflectConfig): Unit =
    assertTrue(
      "Configuration service should return the cached configuration (same instance) when there are no changes",
      config1 eq config2
    )

  def testUseCachedVersionAfterNoChangesAndReparseAfterConfigChanges(): Unit = {
    setScalafmtConfig("config1.conf")

    val scalaFile = createFile("dummy.scala", "")

    val configService = ScalafmtDynamicConfigService(getProject)

    val configVirtualFile = configService.configFileForFile(scalaFile).orNull
    assertNotNull("Can't resolve scalafmt config file", configVirtualFile)

    // Main assertions
    val config1 = configService.configForFile(scalaFile).orNull
    assertNotNull(s"Can't get scalafmt config for $configVirtualFile", config1)

    val config2 = configService.configForFile(scalaFile).orNull
    assertThatConfigurationInstanceIsTheSameWithoutModificationsInConfigurations(config1, config2)
    assertEquals(Some(2), config1.indentMain)

    // Modify the original config
    val newIndentSize = 7
    appendTextToConfiguration(
      "config1.conf",
      s"indent.main = $newIndentSize"
    )

    val config3 = configService.configForFile(scalaFile).orNull
    assertTrue(
      "Configuration service should return a new configuration instance for a modified config file",
      !(config2 eq config3)
    )
    assertEquals(
      "Configuration should have a newly set indent value",
      Some(newIndentSize),
      config3.indentMain
    )
  }

  def testUseCachedVersionAfterNoChangesAndReparseAfterConfigChangesInIncludedConfigurationFile(): Unit = {
    val configFileName = "config2.conf"
    val includedConfigFileName1 = "config2-included1.conf"
    val includedConfigFileName2 = "config2-included2.conf"

    setScalafmtConfig(configFileName)

    val scalaFile = createFile("dummy.scala", "")

    val configService = ScalafmtDynamicConfigService(getProject)

    val configVirtualFile = configService.configFileForFile(scalaFile).orNull
    assertNotNull("Can't resolve scalafmt config file", configVirtualFile)

    // Main assertions
    val config1 = configService.configForFile(scalaFile).orNull
    assertNotNull(s"Can't get scalafmt config for $configVirtualFile", config1)

    assertEquals(
      "Configuration should have correct main indentation",
      Some(2),
      config1.indentMain
    )

    // Modify the included configuration file
    val newIndentSize = 7
    appendTextToConfiguration(
      includedConfigFileName1,
      s"indent.main = $newIndentSize"
    )

    val config2 = configService.configForFile(scalaFile).orNull
    assertTrue(
      s"Configuration service should return a new configuration instance for a modified included config file $includedConfigFileName1",
      !(config1 eq config2)
    )
    assertEquals(
      "Configuration should have a newly set main indent value",
      Some(newIndentSize),
      config2.indentMain
    )

    val config3 = configService.configForFile(scalaFile).orNull
    assertThatConfigurationInstanceIsTheSameWithoutModificationsInConfigurations(config2, config3)

    assertEquals(
      "Configuration should have correct call site indentation",
      Some(2),
      config1.indentCallSite
    )

    // Modify the transitively included configuration file
    val newCallSiteIndentSize = 11
    appendTextToConfiguration(
      includedConfigFileName2,
      s"indent.callSite = $newCallSiteIndentSize"
    )

    val config4 = configService.configForFile(scalaFile).orNull
    assertTrue(
      s"Configuration service should return a new configuration instance for a modified transitively included config file $includedConfigFileName2",
      !(config3 eq config4)
    )
    assertEquals(
      "Configuration should have a newly set call site indent value",
      Some(newCallSiteIndentSize),
      config4.indentCallSite
    )
  }
}