package org.jetbrains.bsp.settings

import com.intellij.configurationStore.StoreUtil
import com.intellij.testFramework.JavaModuleTestCase
import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.settings.BspProjectSettings.{AutoConfig, BloopConfig, BspConfigFile}
import org.jetbrains.bsp.settings.PreImportConfig._
import org.junit.Assert._
import org.junit.Test
import com.intellij.openapi.project.{Project, ProjectManager}
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.{Path, Paths}

@RunWith(classOf[Enclosed])
class BspProjectSettingsTest

object BspProjectSettingsTest {

  class BspServerConfigEqualityTests {
    @Test
    def testBspServerConfigEquality(): Unit = {
      assertTrue(AutoConfig.equals(AutoConfig))
      assertFalse(BloopConfig.equals(AutoConfig))

      assertTrue(BspConfigFile(Path.of("/some/path")).equals(BspConfigFile(Path.of("/some/path"))))
      assertFalse(BspConfigFile(Path.of("/some/path")).equals(BspConfigFile(Path.of("/some/path/path"))))
      assertFalse(BspConfigFile(Path.of("/some/path")).equals(BloopConfig))
    }
  }

  /**
   * Helper method to test serialization by closing and reopening the project.
   * This ensures settings are actually serialized the same way it's done in production.
   */
  private def testWithProjectReopen(
    project: Project,
    setupSettings: BspProjectSettings => Unit,
    verify: BspProjectSettings => Unit
  ): Unit = {
    val projectPath = project.getBasePath
    val projectFile = project.getProjectFilePath

    val settings = new BspProjectSettings
    settings.setExternalProjectPath(projectPath)
    setupSettings(settings)

    BspUtil.bspSettings(project).linkProject(settings)
    StoreUtil.saveSettings(project, true)

    // Close and reopen project to force deserialization from disk
    val projectManager = ProjectManager.getInstance()
    projectManager.closeAndDispose(project)

    val reopenedProject = projectManager.loadAndOpenProject(projectFile)
    assertNotNull("Project should reopen", reopenedProject)

    try {
      // Verify settings were deserialized from disk
      val loadedSettings = BspUtil.bspSettings(reopenedProject).getLinkedProjectSettings(projectPath)
      assertNotNull("Settings should be loaded", loadedSettings)
      verify(loadedSettings)
    } finally {
      projectManager.closeAndDispose(reopenedProject)
    }
  }

  @RunWith(classOf[JUnit4])
  class ServerConfigSerializationTests extends JavaModuleTestCase {

    @Test
    def testAutoConfig(): Unit =
      testWithProjectReopen(
        getProject,
        _.setServerConfig(AutoConfig),
        settings => assertEquals("AutoConfig should be preserved", AutoConfig, settings.serverConfig)
      )

    @Test
    def testBloopConfig(): Unit =
      testWithProjectReopen(
        getProject,
        _.setServerConfig(BloopConfig),
        settings => assertEquals("BloopConfig should be preserved", BloopConfig, settings.serverConfig)
      )

    @Test
    def testBspConfigFile(): Unit = {
      val path = Paths.get("/test/path/to/config.json")
      testWithProjectReopen(
        getProject,
        _.setServerConfig(BspConfigFile(path)),
        settings => assertEquals("BspConfigFile should be preserved", BspConfigFile(path), settings.serverConfig)
      )
    }
  }

  @RunWith(classOf[JUnit4])
  class BooleanFieldsSerializationTests extends JavaModuleTestCase {
    @Test
    def testBooleanFieldsDefaultValues(): Unit =
      testWithProjectReopen(
        getProject,
        _ => {},
        settings => {
          assertFalse("buildOnSave default should be false", settings.buildOnSave)
          assertTrue("runPreImportTask default should be true", settings.runPreImportTask)
        }
      )

    @Test
    def testBooleanFieldsNonDefaultValues(): Unit =
      testWithProjectReopen(
        getProject,
        settings => {
          settings.buildOnSave = true
          settings.runPreImportTask = false
        },
        settings => {
          assertTrue("buildOnSave should be preserved after serialization", settings.buildOnSave)
          assertFalse("runPreImportTask should be preserved after serialization", settings.runPreImportTask)
        }
      )
  }

  @RunWith(classOf[JUnit4])
  class PreImportConfigSerializationTests extends JavaModuleTestCase {

    @Test
    def testAutoPreImport(): Unit =
      testWithProjectReopen(
        getProject,
        _.setPreImportConfig(AutoPreImport),
        settings => assertEquals("AutoPreImport should be preserved", AutoPreImport, settings.preImportConfig)
      )

    @Test
    def testNoPreImport(): Unit =
      testWithProjectReopen(
        getProject,
        _.setPreImportConfig(NoPreImport),
        settings => assertEquals("NoPreImport should be preserved", NoPreImport, settings.preImportConfig)
      )

    @Test
    def testBloopSbtPreImport(): Unit =
      testWithProjectReopen(
        getProject,
        _.setPreImportConfig(BloopSbtPreImport),
        settings => assertEquals("BloopSbtPreImport should be preserved", BloopSbtPreImport, settings.preImportConfig)
      )
  }
}
