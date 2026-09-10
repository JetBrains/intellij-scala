package org.jetbrains.plugins.scala.compiler

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.testFramework.HeavyPlatformTestCase.createChildDirectory
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.{CompilationTests_Zinc, ScalaVersion}
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.{Files, Path}
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/**
 * Tests the Zinc incremental compiler behaviour when a produced `.class` file is removed from the output
 * directory of a module behind the IDE's back (SCL-21674): the module owning the removed product must be
 * recompiled, so that the modules depending on it still find the class.
 *
 * Each JPS module build target (production and test scope of every module) has its own Zinc analysis
 * store, so the removed class file is looked for in a different store depending on the scope in which it
 * was produced. Hence one test per scope combination.
 *
 * @see [[RemovedClassFilesTest]] for the single module version of these tests
 */
@Category(Array(classOf[CompilationTests_Zinc]))
@RunWith(classOf[JUnit4])
class MultiModuleRemovedClassFilesTest extends ScalaCompilerTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override protected def incrementalityType: IncrementalityType = IncrementalityType.SBT

  /**
   * The module which `getModule` depends on. `Foo.scala` is compiled into its production output.
   */
  private var upstreamModule: Module = uninitialized

  /**
   * The test source root of `getModule`. The base class only sets up a production source root.
   */
  private var testSourceRootDir: VirtualFile = uninitialized

  override def setUp(): Unit = {
    super.setUp()

    upstreamModule = createUpstreamModule()
    ModuleRootModificationUtil.addDependency(getModule, upstreamModule)

    testSourceRootDir = createChildDirectory(getBaseDir, "testSrc")
    PsiTestUtil.addSourceRoot(getModule, testSourceRootDir, true)
  }

  @Test
  def removeDependencyClassFileUsedFromProductionSources(): Unit = {
    addFileToUpstreamModule("Foo.scala", "class Foo")
    addFileToProjectSources("Bar.scala", "class Bar extends Foo")

    runRemovedDependencyClassFileTest(
      barSourceRootDir = getSourceRootDir,
      findBarClassFile = () => findClassFile(getModule, "Bar", isTest = false)
    )
  }

  @Test
  def removeDependencyClassFileUsedFromTestSources(): Unit = {
    addFileToUpstreamModule("Foo.scala", "class Foo")
    VfsTestUtil.createFile(testSourceRootDir, "Bar.scala", "class Bar extends Foo")

    runRemovedDependencyClassFileTest(
      barSourceRootDir = testSourceRootDir,
      findBarClassFile = () => findClassFile(getModule, "Bar", isTest = true)
    )
  }

  /**
   * `Foo` is compiled into the production output of the upstream module, `Bar` extends it and is compiled
   * into the output of `getModule` (production or test scope, depending on where its source root is).
   *
   * Removing `Foo.class` and editing `Bar.scala` must recompile both: `Bar` because its source changed,
   * and `Foo` because its class file went missing from the output of the upstream module. Without the
   * latter, compiling `Bar` fails to find `Foo` on the classpath.
   */
  private def runRemovedDependencyClassFileTest(
    barSourceRootDir: VirtualFile,
    findBarClassFile: () => Path
  ): Unit = {
    assertNoErrorsOrWarnings(compiler.make().asScala.toSeq)

    val fooClassFile = findClassFile(upstreamModule, "Foo", isTest = false)
    val fooTimestamp = lastModified(fooClassFile)
    val barTimestamp = lastModified(findBarClassFile())

    removeFile(fooClassFile)
    saveText(barSourceRootDir, "Bar.scala", "class Bar extends Foo { def bar = 1 }")

    assertNoErrorsOrWarnings(compiler.make().asScala.toSeq)

    assertRecompiled("Foo", fooTimestamp, findClassFile(upstreamModule, "Foo", isTest = false))
    assertRecompiled("Bar", barTimestamp, findBarClassFile())
  }

  @Test
  def removeTestClassFile(): Unit = {
    VfsTestUtil.createFile(testSourceRootDir, "Baz.scala", "class Baz")
    VfsTestUtil.createFile(testSourceRootDir, "Qux.scala", "class Qux extends Baz")

    assertNoErrorsOrWarnings(compiler.make().asScala.toSeq)

    val bazClassFile = findClassFile(getModule, "Baz", isTest = true)
    val bazTimestamp = lastModified(bazClassFile)
    val quxTimestamp = lastModified(findClassFile(getModule, "Qux", isTest = true))

    removeFile(bazClassFile)
    saveText(testSourceRootDir, "Qux.scala", "class Qux extends Baz { def qux = 1 }")

    assertNoErrorsOrWarnings(compiler.make().asScala.toSeq)

    assertRecompiled("Baz", bazTimestamp, findClassFile(getModule, "Baz", isTest = true))
    assertRecompiled("Qux", quxTimestamp, findClassFile(getModule, "Qux", isTest = true))
  }

  /**
   * Creates a module with a production source root and the same libraries as `getModule`
   * (a Scala SDK and the test JDK). Its compiler output is inherited from the project, so the produced
   * class files end up in `out/production/upstream`.
   */
  private def createUpstreamModule(): Module = {
    val moduleName = "upstream"
    val moduleDirectory = createChildDirectory(getBaseDir, moduleName)
    val module = PsiTestUtil.addModule(
      getProject,
      JavaModuleType.getModuleType.asInstanceOf[ModuleType[? <: ModuleBuilder]],
      moduleName,
      moduleDirectory
    )
    setUpLibraries(module)
    module
  }

  private def addFileToUpstreamModule(fileName: String, text: String): Unit = {
    val moduleDirectory = getBaseDir.findChild("upstream")
    assertNotNull("Could not find the content root of the upstream module", moduleDirectory)
    VfsTestUtil.createFile(moduleDirectory, fileName, text)
  }

  private def findClassFile(module: Module, name: String, isTest: Boolean): Path = {
    val classFile = SbtProjectCompilationTestBase.findClassFile(name, module, isTest)
    assertNotNull(s"Could not find compiled class file $name", classFile)
    classFile
  }

  private def saveText(sourceRootDir: VirtualFile, fileName: String, text: String): Unit = {
    val file = sourceRootDir.findChild(fileName)
    assertNotNull(s"Could not find source file $fileName", file)
    inWriteAction {
      VfsUtil.saveText(file, text)
    }
  }

  private def lastModified(classFile: Path): Long =
    Files.getLastModifiedTime(classFile).toMillis

  private def assertRecompiled(name: String, timestampBefore: Long, classFile: Path): Unit =
    assertTrue(
      s"Expected $name to be recompiled, but its class file was not modified",
      lastModified(classFile) > timestampBefore
    )
}
