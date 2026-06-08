package org.jetbrains.bsp.project.importing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.{JavaModuleTestCase, PlatformTestUtil, VfsTestUtil}
import org.jetbrains.bsp.settings.BspProjectSettings
import org.jetbrains.bsp.settings.BspProjectSettings.BloopConfig
import org.junit.Assert.{assertFalse, assertNotNull, assertTrue}

class BspOpenProjectProviderGenerationGuardTest extends JavaModuleTestCase {

  private def getProjectDir: VirtualFile = {
    val baseDir = PlatformTestUtil.getOrCreateProjectBaseDir(myProject)
    assertNotNull(baseDir)
    baseDir
  }

  private def shouldGenerateBspConfig(settings: BspProjectSettings): Boolean = {
    val workspace = getProjectDir.toNioPath
    val setupChoices = bspConfigSteps.workspaceSetupChoices(workspace)
    BspOpenProjectProvider.shouldGenerateBspConfig(setupChoices, workspace, settings.serverConfig)
  }

  def testShouldNotGenerateBspConfig_bloopConfig_setupChoices(): Unit = {
    VfsTestUtil.createFile(getProjectDir, "build.sbt")
    val settings = new BspProjectSettings()
    settings.serverConfig = BloopConfig
    assertFalse(
      "shouldGenerateBspConfig must be false when serverConfig is BloopConfig",
      shouldGenerateBspConfig(settings)
    )
  }

  def testShouldNotGenerateBspConfig_bloopConfig_existingBspFile(): Unit = {
    val bspDir = VfsTestUtil.createDir(getProjectDir, ".bsp")
    VfsTestUtil.createFile(bspDir, "sbt.json")
    val settings = new BspProjectSettings()
    settings.serverConfig = BloopConfig
    assertFalse(
      "shouldGenerateBspConfig must be false when serverConfig is BloopConfig",
      shouldGenerateBspConfig(settings)
    )
  }

  def testShouldNotGenerateBspConfig_existingBspFile(): Unit = {
    VfsTestUtil.createFile(getProjectDir, "build.sbt")
    val bspDir = VfsTestUtil.createDir(getProjectDir, ".bsp")
    VfsTestUtil.createFile(bspDir, "sbt.json")
    val settings = new BspProjectSettings()
    assertFalse(
      "shouldGenerateBspConfig must be false when BSP connection file is present",
      shouldGenerateBspConfig(settings)
    )
  }

  def testShouldNotGenerateBspConfig_noSetupChoices(): Unit = {
    val settings = new BspProjectSettings()
    assertFalse(
      "shouldGenerateBspConfig must be false when setupChoices is empty",
      shouldGenerateBspConfig(settings)
    )
  }

  def testShouldGenerateBspConfig_multipleSetupChoices_sbt(): Unit = {
    VfsTestUtil.createFile(getProjectDir, "build.sbt")
    val settings = new BspProjectSettings()
    assertTrue(
      // in this case sbt/BSP and sbt + Bloop options are available
      "shouldGenerateBspConfig must be true when no BSP connection files are present and multiple setupChoices are available",
      shouldGenerateBspConfig(settings)
    )
  }

  def testShouldGenerateBspConfig_multipleSetupChoices(): Unit = {
    VfsTestUtil.createFile(getProjectDir, "build.mill")
    val settings = new BspProjectSettings()
    assertTrue(
      "shouldGenerateBspConfig must return true when no BSP connection files are present and exactly one setupChoice is available",
      shouldGenerateBspConfig(settings)
    )
  }
}
