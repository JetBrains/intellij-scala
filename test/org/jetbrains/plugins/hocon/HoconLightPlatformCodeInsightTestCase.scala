package org.jetbrains.plugins.hocon

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.roots.{ContentEntry, ModuleRootManager}
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.{LightPlatformCodeInsightTestCase, LightPlatformTestCase, PsiTestUtil}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * Base class for multi-file HOCON tests. Implementation based on
 * [[org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter]]
 */
abstract class HoconLightPlatformCodeInsightTestCase extends LightPlatformCodeInsightTestCase {
  protected def rootPath: String

  protected def baseRootPath = TestUtils.getTestDataPath + "/hocon/"

  override def setUp() = {
    super.setUp()

    var contentEntry: ContentEntry = null

    VfsRootAccess.allowRootAccess(TestUtils.getTestDataPath)

    val rootManager: ModuleRootManager = ModuleRootManager.getInstance(LightPlatformTestCase.getModule)
    val rootModel = rootManager.getModifiableModel
    val testDataRoot: VirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
    assert(testDataRoot != null)
    contentEntry = rootModel.addContentEntry(testDataRoot)
    contentEntry.addSourceFolder(testDataRoot, false)

    inWriteAction {
      rootModel.commit()
      val project = LightPlatformTestCase.getProject
      val startupManager = StartupManager.getInstance(project).asInstanceOf[StartupManagerImpl]
      startupManager.startCacheUpdate()
    }
  }

  override def tearDown() = {
    val testDataRoot: VirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
    assert(testDataRoot != null)

    PsiTestUtil.removeContentEntry(LightPlatformTestCase.getModule, testDataRoot)

    super.tearDown()
  }
}
