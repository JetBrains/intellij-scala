package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.{EdtTestUtil, HeavyPlatformTestCase, PlatformTestUtil}
import org.jetbrains.plugins.scala.lang.formatter.scalafmt.ScalaFmtForTestsSetupOps
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError.ConfigParseError
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalafmtSettingsConfigResolver.{Failure, Result, Version}
import org.junit.Assert._
import org.scalafmt.dynamic.ScalafmtVersion

import java.nio.file.{Files, Path}
import java.util.concurrent.CompletableFuture

class ScalafmtSettingsConfigResolverTest extends HeavyPlatformTestCase {
  override def runInDispatchThread(): Boolean = false

  override def setUp(): Unit = {
    super.setUp()

    ScalaFmtForTestsSetupOps.ensureDownloaded(DefaultVersion, ScalafmtVersion(2, 7, 5))
  }

  def testExplicitVersion(): Unit = {
    val result = resolve("version = 2.7.5")
    assertEquals("An explicit version should resolve without the default marker",
      Right(Version(ScalafmtVersion(2, 7, 5), isDefault = false)), result)
  }

  def testMissingVersionUsesDefault(): Unit = {
    val result = resolve("maxColumn = 100")
    assertEquals("A configuration without a version should resolve the default formatter",
      Right(Version(DefaultVersion, isDefault = true)), result)
  }

  def testMalformedConfigurationHasNoRequestedVersion(): Unit = {
    val result = resolve("version = {")
    assertTrue(s"Malformed HOCON should produce a parse error before selecting a version: $result",
      result match {
        case Left(Failure(_: ConfigParseError, None)) => true
        case _ => false
      })
  }

  def testInvalidFormatterSettingRetainsRequestedVersion(): Unit = {
    val result = resolve("version = 2.7.5\nmaxColumn = invalid")
    assertTrue(s"A formatter configuration error should retain the selected version: $result",
      result match {
        case Left(Failure(_: ConfigParseError, Some(Version(version, false)))) =>
          version == ScalafmtVersion(2, 7, 5)
        case _ => false
      })
  }

  def testResolveFormatterForDefaultProject(): Unit = {
    val result = resolve("version = 2.7.5", defaultProject = true)
    assertEquals("Default-project Settings should resolve the configured formatter version",
      Right(Version(ScalafmtVersion(2, 7, 5), isDefault = false)), result)
  }

  private def resolve(configText: String, defaultProject: Boolean = false): Result = {
    val configFile = EdtTestUtil.runInEdtAndGet(() => {
      // Real files exercise document-backed parsing, VFS refresh and formatter configuration loading together.
      val projectRoot = createTestProjectStructure()
      val path = Path.of(projectRoot.getPath).resolve(".scalafmt.conf")
      Files.writeString(path, configText)
      val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
      assertNotNull("The configuration must be visible in VFS before resolution", file)
      file
    })
    val completed = new CompletableFuture[Result]()
    EdtTestUtil.runInEdtAndWait(() => {
      val project = if (defaultProject) ProjectManager.getInstance().getDefaultProject else getProject
      ScalafmtSettingsConfigResolver.resolveConfigAsync(project, configFile, result => completed.complete(result))
      // The real services may complete through EDT; waiting must continue dispatching without write intent.
      PlatformTestUtil.waitWithEventsDispatching("Configuration resolution should complete", () => completed.isDone, 10)
    }, false)
    completed.get()
  }
}
