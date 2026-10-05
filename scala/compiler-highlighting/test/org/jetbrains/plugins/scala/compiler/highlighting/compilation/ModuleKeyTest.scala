package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.PsiManager
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.compiler.CompilationUnitId
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteAction}
import org.jetbrains.plugins.scala.runner.ScalaFixtureTestCaseWithSourceFolder
import org.jetbrains.sbt.project.settings.DisplayModuleName
import org.junit.Assert.*

import java.nio.file.{Files, Path}

/**
 * Tests for [[ModuleKey]] resolution.
 *
 * The base class runs on JUnit 3 conventions: methods are picked up because they are named `test*`,
 * so no `@Test` annotations are used here (they would suggest a JUnit 4 runner, which would never
 * invoke `setUp`).
 *
 * Note on `findRepresentativeModuleForSharedSourceModuleOrSelf`: every `ModuleKey.of` overload routes
 * through it, but it only does something for modules whose type id is `SHARED_SOURCES_MODULE` and which
 * carry a shared-sources-owners workspace entity. Neither can be produced faithfully in a fixture test,
 * so that branch is covered by the sbt import tests instead; here it is always identity.
 */
class ModuleKeyTest extends ScalaFixtureTestCaseWithSourceFolder {

  /** Test source root of [[myModule]], created in addition to the `src` root added by the base class. */
  private var testRoot: VirtualFile = scala.compiletime.uninitialized

  override protected def setUp(): Unit = {
    super.setUp()
    testRoot = inWriteAction(getBaseDir.createChildDirectory(this, "testSrc"))
    PsiTestUtil.addSourceRoot(myModule, testRoot, JavaSourceRootType.TEST_SOURCE)
  }

  /** Temp directory outside the project, deleted in [[tearDown]]. */
  private var outsideTempDir: Path = scala.compiletime.uninitialized

  override def tearDown(): Unit = try {
    if (outsideTempDir != null) {
      NioFiles.deleteRecursively(outsideTempDir)
      outsideTempDir = null
    }
  } finally {
    super.tearDown()
  }

  //
  // ModuleKey.of(Project, VirtualFile)
  //

  def testOfVirtualFile_productionScope(): Unit = {
    val file = addFileToProjectSources("ProductionFile.scala", "class ProductionFile")

    assertEquals(
      "a file in a production source root must resolve to its module in the production scope",
      Some(ModuleKey(myModule, SourceScope.Production)),
      ModuleKey.of(getProject, file)
    )
  }

  def testOfVirtualFile_testScope(): Unit = {
    val file = addFileToTestSources("TestFile.scala", "class TestFile")

    assertEquals(
      "a file in a test source root must resolve to its module in the test scope",
      Some(ModuleKey(myModule, SourceScope.Test)),
      ModuleKey.of(getProject, file)
    )
  }

  def testOfVirtualFile_inContentRootButNotInSourceRoot(): Unit = {
    val (otherModule, otherModuleRoot) = createJavaModule("moduleWithoutSourceRoots")
    // The content root itself is not a source root, so the file is inside the module but outside any scope.
    val file = VfsTestUtil.createFile(otherModuleRoot, "Loose.scala", "class Loose")

    assertEquals(
      "a file under a content root but outside every source root still belongs to the module",
      Some(ModuleKey(otherModule, SourceScope.Production)),
      ModuleKey.of(getProject, file)
    )
  }

  def testOfVirtualFile_directory(): Unit = {
    assertEquals(
      "a directory resolves the same way a file does",
      Some(ModuleKey(myModule, SourceScope.Production)),
      ModuleKey.of(getProject, getSourceRootDir)
    )
  }

  def testOfVirtualFile_outsideAnyModule_returnsNone(): Unit = {
    // A real temp directory, not `getBaseDir.getParent`: the latter is the shared test temp root and
    // leaks files between runs.
    outsideTempDir = Files.createTempDirectory("moduleKeyTest-outside")
    val outsideRoot = LocalFileSystem.getInstance.refreshAndFindFileByNioFile(outsideTempDir)
    assertNotNull("failed to refresh the temp directory into the VFS", outsideRoot)
    val file = VfsTestUtil.createFile(outsideRoot, "Outside.scala", "class Outside")

    assertEquals(
      "a file outside every content root must not resolve to any module",
      None,
      ModuleKey.of(getProject, file)
    )
  }


  //
  // ModuleKey.of(FileCompilationScope)
  //

  def testOfFileCompilationScope_production(): Unit = {
    val file = addFileToProjectSources("ProductionFile.scala", "class ProductionFile")
    val scope = fileCompilationScope(file, myModule, SourceScope.Production)

    assertEquals(
      ModuleKey(myModule, SourceScope.Production),
      ModuleKey.of(scope)
    )
  }

  def testOfFileCompilationScope_test(): Unit = {
    val file = addFileToTestSources("TestFile.scala", "class TestFile")
    val scope = fileCompilationScope(file, myModule, SourceScope.Test)

    assertEquals(
      "the scope of an already resolved compilation scope must be preserved",
      ModuleKey(myModule, SourceScope.Test),
      ModuleKey.of(scope)
    )
  }

  /**
   * Builds a scope the way the highlighting triggers do.
   *
   * `ModuleKey.of(FileCompilationScope)` reads only `module` and `sourceScope`, but the document and the
   * PSI file are resolved from the real file anyway so the fixture breaks if the scope shape changes.
   */
  private def fileCompilationScope(file: VirtualFile, module: Module, sourceScope: SourceScope): FileCompilationScope =
    inReadAction {
      val psiFile = PsiManager.getInstance(getProject).findFile(file)
      assertNotNull(s"no PSI file for ${file.getName}", psiFile)
      val document = FileDocumentManager.getInstance.getDocument(file)
      assertNotNull(s"no document for ${file.getName}", document)
      FileCompilationScope(file, module, sourceScope, document, psiFile)
    }


  //
  // ModuleKey.of(Project, CompilationUnitId)
  //

  def testOfCompilationUnitId_byIntellijModuleName(): Unit = {
    // Give the module a display name that differs from its IntelliJ name, otherwise this test would
    // resolve through the display-name branch and never reach `findModuleByName`.
    DisplayModuleName.getInstance(myModule).setName("some-unrelated-display-name")
    val unitId = CompilationUnitId(moduleId = myModule.getName, testScope = false)

    assertEquals(
      "a unit id carrying the IntelliJ module name must resolve through the findModuleByName fallback",
      Some(ModuleKey(myModule, SourceScope.Production)),
      ModuleKey.of(getProject, unitId)
    )
  }

  def testOfCompilationUnitId_testScope(): Unit = {
    val unitId = CompilationUnitId(moduleId = myModule.getName, testScope = true)

    assertEquals(
      "testScope = true must map to SourceScope.Test",
      Some(ModuleKey(myModule, SourceScope.Test)),
      ModuleKey.of(getProject, unitId)
    )
  }

  def testOfCompilationUnitId_bySbtDisplayModuleName(): Unit = {
    val (secondModule, _) = createJavaModule("secondModule")
    val sbtDisplayName = "sbt-display-name-for-module"
    DisplayModuleName.getInstance(secondModule).setName(sbtDisplayName)

    val unitId = CompilationUnitId(moduleId = sbtDisplayName, testScope = false)

    assertEquals(
      "a build naming a module by its sbt display name must resolve to that module",
      Some(ModuleKey(secondModule, SourceScope.Production)),
      ModuleKey.of(getProject, unitId)
    )
  }

  def testOfCompilationUnitId_displayNameWinsOverIntellijName(): Unit = {
    // `alpha` is displayed as `beta`, while a different module is actually *named* `beta`.
    val (alpha, _) = createJavaModule("alpha")
    val (beta, _) = createJavaModule("beta")
    DisplayModuleName.getInstance(alpha).setName("beta")
    // Pin beta's display name too, so the lookup below cannot match it by display name whatever the default is.
    DisplayModuleName.getInstance(beta).setName("beta-display-name")

    val unitId = CompilationUnitId(moduleId = "beta", testScope = false)

    assertEquals(
      "the display-name lookup must take precedence over the IntelliJ-name lookup",
      Some(ModuleKey(alpha, SourceScope.Production)),
      ModuleKey.of(getProject, unitId)
    )
  }

  def testOfCompilationUnitId_unknownModule_returnsNone(): Unit = {
    val unitId = CompilationUnitId(moduleId = "nonExistentModule", testScope = false)

    assertEquals(
      "a module id that matches neither naming scheme must not resolve",
      None,
      ModuleKey.of(getProject, unitId)
    )
  }

  private def addFileToTestSources(relativePath: String, text: String): VirtualFile =
    VfsTestUtil.createFile(testRoot, relativePath, StringUtil.convertLineSeparators(text))

  /**
   * Creates an additional module with `moduleRoot` as its content root.
   *
   * No explicit disposal: the fixture creates a fresh project per test, so the module dies with it.
   */
  private def createJavaModule(name: String): (Module, VirtualFile) = {
    val moduleRoot = inWriteAction(getBaseDir.createChildDirectory(this, name))
    val module = PsiTestUtil.addModule(
      getProject,
      JavaModuleType.getModuleType.asInstanceOf[ModuleType[? <: ModuleBuilder]],
      name,
      moduleRoot
    )
    (module, moduleRoot)
  }
}
