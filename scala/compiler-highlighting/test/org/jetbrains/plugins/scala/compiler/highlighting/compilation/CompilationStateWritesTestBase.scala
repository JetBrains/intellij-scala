package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.plugins.scala.compiler.highlighting.services.CompilationLogicService
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.runner.ScalaFixtureTestCaseWithSourceFolder
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}

import scala.compiletime.uninitialized

/**
 * A source root (`src`), a resource root (`resources`) and a directory in no content root at all
 * (`outside`), with assertions phrased as what a write to [[CompilationLogicService]] should have changed.
 *
 * `outside` sits beside the project directory rather than inside it: the fixture registers the project
 * directory itself as a source root, so a child of it would still be in source content.
 */
abstract class CompilationStateWritesTestBase extends ScalaFixtureTestCaseWithSourceFolder {

  private var resourceRoot: VirtualFile = uninitialized
  private var outsideDir: VirtualFile = uninitialized

  override protected def setUp(): Unit = {
    super.setUp()
    resourceRoot = VfsTestUtil.createDir(getBaseDir, "resources")
    outsideDir = VfsTestUtil.createDir(getBaseDir.getParent, "outside")
    PsiTestUtil.addSourceRoot(myModule, resourceRoot, JavaResourceRootType.RESOURCE)
    assertRootsAreAsExpected()
  }

  private def assertRootsAreAsExpected(): Unit = {
    val fileIndex = ProjectRootManager.getInstance(getProject).getFileIndex
    assertTrue("src must be a source root", fileIndex.isInSourceContent(getSourceRootDir))
    assertTrue("resources must be in source content", fileIndex.isInSourceContent(resourceRoot))
    assertFalse("outside must be in no source content", fileIndex.isInSourceContent(outsideDir))
  }

  protected def logic: CompilationLogicService = CompilationLogicService(getProject)

  protected def state: CompilationState = logic.snapshot

  protected def inSources(relativePath: String, text: String = ""): VirtualFile =
    addFileToProjectSources(relativePath, text)

  protected def inResources(relativePath: String, text: String = ""): VirtualFile =
    VfsTestUtil.createFile(resourceRoot, relativePath, text)

  protected def outsideRoots(relativePath: String, text: String = ""): VirtualFile =
    VfsTestUtil.createFile(outsideDir, relativePath, text)

  protected def createSourceDir(name: String): VirtualFile =
    VfsTestUtil.createDir(getSourceRootDir, name)

  protected def delete(file: VirtualFile): Unit = inWriteAction(file.delete(this))

  protected def rename(file: VirtualFile, newName: String): Unit =
    inWriteAction(file.rename(this, newName))

  protected def move(file: VirtualFile, target: VirtualFile): Unit =
    inWriteAction(file.move(this, target))

  /** A write that does not go through the document manager, so it is not `isFromSave`. */
  protected def writeExternally(file: VirtualFile, text: String): Unit =
    inWriteAction(file.setBinaryContent(text.getBytes(file.getCharset)))

  /** Puts `file`'s target into `compiledAt`, so that losing it is observable. */
  protected def markTargetBuilt(file: VirtualFile): Unit = {
    val token = logic.compilationStarted(Set(file))
    ModuleKey.of(getProject, file).foreach(target =>
      logic.compilationSucceeded(token, Set(target), Set(file))
    )
  }

  protected def assertRecordsModified(files: VirtualFile*)(body: => Unit): Unit = {
    val before = state
    body
    val after = state
    assertEquals("modified files", before.modifiedFiles ++ files, after.modifiedFiles)
    assertTrue("the change clock must advance", after.changesEpoch > before.changesEpoch)
  }

  protected def assertRecordsOnlyClock(body: => Unit): Unit = {
    val before = state
    body
    val after = state
    assertEquals("modified files must not grow", before.modifiedFiles, after.modifiedFiles)
    assertTrue("the change clock must advance", after.changesEpoch > before.changesEpoch)
  }

  protected def assertRecordsNothing(body: => Unit): Unit = {
    val before = state
    body
    assertEquals("nothing should have been recorded", before, state)
  }

  protected def assertInvalidatesEverything(body: => Unit): Unit = {
    val before = state
    body
    val after = state
    assertTrue("compiledAt must be cleared", after.compiledAt.isEmpty)
    assertTrue("the change clock must advance", after.changesEpoch > before.changesEpoch)
  }
}
