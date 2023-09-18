package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupManager}
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EditorTestUtil.CARET_TAG
import com.intellij.testFramework.fixtures.{JavaCodeInsightFixtureTestCase, JavaCodeInsightTestFixture}
import com.intellij.testFramework.{PsiTestUtil, UsefulTestCase}
import org.jetbrains.plugins.scala.CompletionTests
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.base.{HelperFixtureEditorOps, ScalaSdkOwner}
import org.jetbrains.plugins.scala.extensions.{StringExt, invokeAndWait}
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.DefaultInvocationCount
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.collection.mutable
import scala.jdk.CollectionConverters.{IterableHasAsScala, SeqHasAsJava, SetHasAsJava}
import scala.util.chaining.scalaUtilChainingOps

@Category(Array(classOf[CompletionTests]))
@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
final class ScalaMultiModuleDependenciesCompletionTest
  extends JavaCodeInsightFixtureTestCase
    with ScalaSdkOwner
    with HelperFixtureEditorOps {

  import ScalaMultiModuleDependenciesCompletionTest._

  private[this] val myLoaders = mutable.Set.empty[LibraryLoader]

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(ScalaSDKLoader())

  override protected def setUp(): Unit = {
    TestUtils.optimizeSearchingForIndexableFiles()
    super[JavaCodeInsightFixtureTestCase].setUp()

    val zio1Module = addModule(ZIO1_MODULE_NAME)
    val zio2Module = addModule(ZIO2_MODULE_NAME)

    setupLibrariesFor(getModule, zio1Module, zio2Module)
    setupLibrariesFor(zio1Module, zioTestLoader(ZIO1_VERSION))
    setupLibrariesFor(zio2Module, zioTestLoader(ZIO2_VERSION))
  }

  override protected def tearDown(): Unit = {
    disposeLibraries()
    super.tearDown()
  }

  private def doTest(moduleName: String): Unit = {
    val testData = MODULE_NAME_TO_TESTDATA(moduleName)

    val fileText = suiteFileText(testData.specClassName)
    val file = myFixture.addFileToProject(s"$moduleName/TestSpec.scala", fileText)
    val lookupStrings = activeLookupItems(file).map(_.getLookupString).toList

    UsefulTestCase.assertContainsElements(lookupStrings.asJava, testData.allMethods.asJava)
    UsefulTestCase.assertDoesntContain(lookupStrings.asJava, testData.unavailableMethods.asJava)
  }

  //start section: tests
  def testFirstModule(): Unit = doTest(ZIO1_MODULE_NAME)

  def testSecondModule(): Unit = doTest(ZIO2_MODULE_NAME)
  //end section: tests

  private def activeLookupItems(file: PsiFile): Iterable[LookupElement] = {
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile)

    val editor = myFixture.getEditor
    changePsiAt(editor.getCaretModel.getOffset)

    invokeAndWait {
      new CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true)
        .invokeCompletion(getProject, editor, DefaultInvocationCount)
    }

    LookupManager.getActiveLookup(editor) match {
      case impl: LookupImpl =>
        impl.getItems.asScala
      case _ =>
        throw new AssertionError("Lookups not found")
    }
  }

  private def setupLibrariesFor(modules: Module*): Unit = for {
    module <- modules
    loader <- librariesLoaders
  } setupLibrariesFor(module, loader)

  private def setupLibrariesFor(module: Module, loader: LibraryLoader): Unit =
    myLoaders += loader.tap(_.init(module, version))

  private def disposeLibraries(): Unit = {
    for {
      module <- getProject.modules
      loader <- myLoaders
    } loader.clean(module)

    myLoaders.clear()
  }

  private def addModule(name: String) =
    PsiTestUtil.addModule(
      getProject,
      JavaModuleType.getModuleType.asInstanceOf[ModuleType[_ <: ModuleBuilder]],
      name,
      myFixture.getTempDirFixture.findOrCreateDir(name)
    )

  private def zioTestLoader(zioVersion: String) = IvyManagedLoader(("dev.zio" %% "zio-test" % zioVersion).transitive())

  //start section: workaround methods (copied from ScalaLightCodeInsightFixtureTestCase)
  //Workarounds to make the method callable from traits (using cake pattern)
  //Also needed to workaround https://github.com/scala/bug/issues/3564
  override protected def getProject: Project = super[JavaCodeInsightFixtureTestCase].getProject

  //don't use getFixture, use `myFixture` directly
  protected def getFixture: JavaCodeInsightTestFixture = myFixture
  //end section: workaround methods
}

private object ScalaMultiModuleDependenciesCompletionTest {
  final case class TestData(specClassName: String, allMethods: Set[String], unavailableMethods: Set[String])

  private[this] val COMMON_ZIO_METHODS = Set("check", "checkAll", "checkN")
  private[this] val ZIO1_SPECIFIC_METHODS = Set("checkAllM", "checkAllMPar", "checkM", "checkNM")
  private[this] val ZIO2_SPECIFIC_METHODS = Set("checkAllPar")

  val ZIO1_MODULE_NAME = "zio1"
  val ZIO2_MODULE_NAME = "zio2"

  val ZIO1_VERSION = "1.0.17"
  val ZIO2_VERSION = "2.0.2"

  val MODULE_NAME_TO_TESTDATA: Map[String, TestData] = Map(
    ZIO1_MODULE_NAME -> TestData(
      specClassName = "DefaultRunnableSpec",
      allMethods = COMMON_ZIO_METHODS ++ ZIO1_SPECIFIC_METHODS,
      unavailableMethods = ZIO2_SPECIFIC_METHODS,
    ),
    ZIO2_MODULE_NAME -> TestData(
      specClassName = "ZIOSpecDefault",
      allMethods = COMMON_ZIO_METHODS ++ ZIO2_SPECIFIC_METHODS,
      unavailableMethods = ZIO1_SPECIFIC_METHODS,
    ),
  )

  def suiteFileText(specClassName: String): String =
    s"""
       |package com.example
       |
       |import zio.test.{$specClassName, assertTrue}
       |
       |object TestSpec extends $specClassName {
       |  override def spec =
       |    suite("")(
       |      test("") {
       |        ???
       |      },
       |      test("") {
       |        check$CARET_TAG
       |
       |        assertTrue(false)
       |      }
       |    )
       |}
       |""".stripMargin.withNormalizedSeparator.trim
}
