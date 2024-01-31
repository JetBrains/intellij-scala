package org.jetbrains.plugins.scala.refactoring.move

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.util.TestUtils

import java.io.File
import java.nio.file.Path
import java.util

abstract class ScalaMoveTestBase extends ScalaLightCodeInsightFixtureTestCase {
  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken.DoNotShare

  protected def getTestDataRoot: String = TestUtils.getTestDataPath + "/refactoring/move/"

  protected def configureModuleSources(module: Module, rootDir: VirtualFile): Unit =
    PsiTestUtil.addSourceContentToRoots(module, rootDir)

  private def root: String = getTestDataRoot + getTestName(true)

  private def findAndRefreshVFile(path: String) = {
    val vFile = LocalFileSystem.getInstance.findFileByPath(path.replace(File.separatorChar, '/'))
    VfsUtil.markDirtyAndRefresh(/*async = */ false, /*recursive =*/ true, /*reloadChildren =*/ true, vFile)
    vFile
  }

  private var rootDirBefore: VirtualFile = _
  private var rootDirAfter: VirtualFile = _

  protected def getRootBefore: VirtualFile = rootDirBefore

  protected def getRootAfter: VirtualFile = rootDirAfter

  override protected def afterSetUpProject(project: Project, module: Module): Unit = {
    super.afterSetUpProject(project, module)
    val rootBefore = root + "/before"
    val rootAfter = root + "/after"
    findAndRefreshVFile(rootBefore)

    // remove existing content entries (default source folders),
    // otherwise default package (empty one, "") will be detected in this content entry during move refactoring
    inWriteAction {
      ModuleRootModificationUtil.modifyModel(module, model => {
        val contentEntries = model.getContentEntries
        contentEntries.foreach(model.removeContentEntry)
        true
      })
    }

    rootDirBefore = PsiTestUtil.createTestProjectStructure(project, module, rootBefore, new util.HashSet[Path](), false)
    configureModuleSources(module, rootDirBefore)
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    rootDirAfter = findAndRefreshVFile(rootAfter)
  }
}
