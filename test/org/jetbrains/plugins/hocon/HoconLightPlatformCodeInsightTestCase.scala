package org.jetbrains.plugins.hocon

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.{ContentEntry, ModifiableRootModel, ModuleRootManager}
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.{LightPlatformCodeInsightTestCase, LightPlatformTestCase, PsiTestUtil}
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * Base class for multi-file HOCON tests. Implementation based on
 * [[org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter]]
 */
abstract class HoconLightPlatformCodeInsightTestCase extends LightPlatformCodeInsightTestCase {
  protected def rootPath: String = null

  protected def baseRootPath = TestUtils.getTestDataPath + "/hocon/"

  override def setUp() = {
    super.setUp()

    var contentEntry: ContentEntry = null

    VfsRootAccess.allowRootAccess(TestUtils.getTestDataPath)

    val rootManager: ModuleRootManager = ModuleRootManager.getInstance(LightPlatformTestCase.getModule)
    if (rootPath != null) {
      val rootModel = rootManager.getModifiableModel
      val testDataRoot: VirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
      assert(testDataRoot != null)
      contentEntry = rootModel.addContentEntry(testDataRoot)
      contentEntry.addSourceFolder(testDataRoot, false)

      if (rootModel != null) {
        val finalRootModel: ModifiableRootModel = rootModel
        ApplicationManager.getApplication.runWriteAction(new Runnable {
          def run() {
            finalRootModel.commit()
            val project = LightPlatformTestCase.getProject
            val startupManager = StartupManager.getInstance(project).asInstanceOf[StartupManagerImpl]
            startupManager.startCacheUpdate()
          }
        })
      }
    }
  }

  override def tearDown() = {
    if (rootPath != null) {
      val testDataRoot: VirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
      assert(testDataRoot != null)

      PsiTestUtil.removeContentEntry(LightPlatformTestCase.getModule, testDataRoot)
    }

    super.tearDown()
  }
}
