package org.jetbrains.plugins.scala.compiler.testUtils

import com.intellij.compiler.server.BuildManager
import com.intellij.java.testFramework.backend.CompilerTestUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.{EdtTestUtil, PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert.assertNotNull

import java.nio.file.Files

/**
 * Minimal compiler-project fixture for tests that need external Make/JPS infrastructure without invoking the platform
 * [[com.intellij.testFramework.CompilerTester]].
 *
 * It performs only the setup that lightweight tests currently need:
 *   - creates and registers a `src` source root;
 *   - creates and configures a compiler output root;
 *   - saves application settings (including the SDK table) to disk so external JPS can resolve module SDK names;
 *   - clears cached JPS build state after changing roots and SDKs.
 *
 * Created directories (`src`, `out`) are intentionally left in place on [[tearDown]]; the surrounding platform test
 * fixture is responsible for cleaning up the project base directory.
 *
 * Unlike [[com.intellij.testFramework.CompilerTester]], this class does not run compilation, collect compiler messages,
 * override module JDKs, manage temporary output fixtures, or perform the full platform compiler-test lifecycle.
 */
class SimpleCompilerTester(
  project: Project,
  module: Module
) {

  def setUp(): Unit = {
    addSrcRoot()
    addOutRoot()
    prepareExternalCompilerModel()
  }

  def tearDown(): Unit = {
    // nothing urgent to do here (though we could still remove the created dirs)
  }

  protected def getBaseDir: VirtualFile = {
    val baseDir = PlatformTestUtil.getOrCreateProjectBaseDir(project)
    assertNotNull(baseDir)
    baseDir
  }

  private def getOrCreateChildDir(name: String): VirtualFile = {
    val dir = getBaseDir.toNioPath.resolve(name)
    if (!Files.exists(dir)) Files.createDirectory(dir)
    LocalFileSystem.getInstance.refreshAndFindFileByNioFile(dir)
  }

  private def addSrcRoot(): Unit = {
    val srcRoot = getOrCreateChildDir("src")
    PsiTestUtil.addSourceRoot(module, srcRoot, false)
  }

  private def addOutRoot(): Unit = {
    val outRoot = getOrCreateChildDir("out")
    inWriteAction {
      CompilerProjectExtension.getInstance(project).setCompilerOutputUrl(outRoot.getUrl)
    }
  }

  private def prepareExternalCompilerModel(): Unit = {
    // Make sure that the SDK settings are persisted to disk.
    // Without them, external Make can fail with "No JDK in module".
    // Saving it to disk will allow the JPS process to read it from there.
    //
    // NOTE: a similar thing is done in [[com.intellij.testFramework.CompilerTester.runCompiler]],
    // which also persists compiler inputs before invoking JPS (though CompilerTester.runCompiler does more than that).
    // For our needs this manual save is enough.
    //
    // Note that this test fixture can be used where the CompilerTester might not be used directly, hence the manual save.
    //
    // This is wired into [[org.jetbrains.sbt.runner.SbtRunConfiguration_LightExecution_TestBase]] and currently matters for
    // [[org.jetbrains.sbt.runner.SbtRunConfiguration_BuildBeforeLaunchTest]], which runs a real Build/Make before-launch step.
    EdtTestUtil.runInEdtAndWait { () =>
      CompilerTestUtil.saveApplicationSettings()
    }

    // Roots and SDKs are changed during fixture setup;
    // clear cached JPS state so the next external build sees the updated model.
    BuildManager.getInstance.clearState(project)
  }
}
