package org.jetbrains.bsp.protocol

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.jetbrains.bsp.protocol.BspCommunication.SessionContext
import org.jetbrains.bsp.protocol.BspConfigRegeneration.RegenerationReason
import org.jetbrains.bsp.settings.BspProjectSettings.{AutoConfig, BloopConfig, BspServerConfig}
import org.jetbrains.plugins.scala.build.{BuildReporter, NoOpBuildReporter}
import com.intellij.testFramework.JavaModuleTestCase
import junit.framework.TestCase.assertTrue

import java.nio.file.{Files, Path}

/** Unit tests for [[BspConfigRegeneration]] */
class BspConfigRegenerationTest extends JavaModuleTestCase {

  def testRegenerationSkipped_BloopServerConfig(): Unit =
    assertRegenerationSkipped(BloopConfig, setupWorkspace = _ => ())

  def testRegenerationSkipped_BloopConfigDirExists(): Unit =
    assertRegenerationSkipped(AutoConfig, setupWorkspace = dir => Files.createDirectory(dir.resolve(".bloop")))

  private def assertRegenerationSkipped(serverConfig: BspServerConfig, setupWorkspace: Path => Unit): Unit = {
    val tempDir = Files.createTempDirectory("bsp-regen-test")
    try {
      given SessionContext = SessionContext(
        project = None,
        bspProjectSettings = None,
        indicator = Some(new EmptyProgressIndicator(ModalityState.nonModal())),
        initialServerConfig = serverConfig
      )
      given EelDescriptor = LocalEelDescriptor.INSTANCE
      given BuildReporter = new NoOpBuildReporter {}

      setupWorkspace(tempDir)

      val result = BspConfigRegeneration.regenerateBspConfig(tempDir, RegenerationReason.ServerFailure)
      assertTrue("The BSP connection file should not have been regenerated for Bloop project, but it was", result.isEmpty)
    } finally {
      NioFiles.deleteRecursively(tempDir)
    }
  }
}
