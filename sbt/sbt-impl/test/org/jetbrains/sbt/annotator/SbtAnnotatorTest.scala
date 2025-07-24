package org.jetbrains.sbt.annotator

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.{ModifiableRootModel, ModuleRootModificationUtil}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtilCore}
import com.intellij.psi.PsiManager
import com.intellij.testFramework.{HeavyPlatformTestCase, UsefulTestCase}
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, SmartJDKLoader}
import org.jetbrains.plugins.scala.util.TestUtils.getTestDataPath
import org.jetbrains.plugins.scala.{SlowTests2, SlowTests}
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.{MockSbtBase, MockSbtBuildModule, MockSbt_0_13, MockSbt_1_0, MockSbt_2, SbtBundle, SbtVersion}
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.experimental.categories.Category

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

@Category(Array(classOf[SlowTests]))
abstract class SbtAnnotatorTestBase extends HeavyPlatformTestCase
  with MockSbtBase
  with MockSbtBuildModule {

  protected def testdataPath: String = s"$getTestDataPath/annotator/Sbt"

  protected def loadTestFile(): SbtFileImpl = {
    val filePath = s"$testdataPath/SbtAnnotator.sbt"
    val file = LocalFileSystem.getInstance
      .findFileByPath(FileUtil.toSystemIndependentName(filePath))
    assertNotNull(filePath, file)
    val sbtFile = PsiManager.getInstance(getProject).findFile(file).asInstanceOf[SbtFileImpl]
    sbtFile.putUserData(ModuleUtilCore.KEY_MODULE, getModule)
    sbtFile
  }

  override def librariesLoaders: Seq[LibraryLoader] =
    HeavyJDKLoader() +: super.librariesLoaders

  override protected def setUp(): Unit = {
    super.setUp()
    setupSbtBuildModule(getModule, Some(getTestProjectJdk))
    addTestFileToModuleSources()
    setUpProjectSettings()
  }

  override protected def getTestProjectJdk: Sdk = SmartJDKLoader.getOrCreateJDK()

  protected def runTest(sbtVersion: SbtVersion, expectedMessages: Seq[Message]): Unit = {
    setSbtVersion(sbtVersion)
    val actualMessages = annotate().asJava
    UsefulTestCase.assertSameElements(actualMessages, expectedMessages: _*)
  }

  protected def setSbtVersion(sbtVersion: SbtVersion): Unit = {
    val projectSettings = SbtSettings.getInstance(getProject).getLinkedProjectSettings(getProject.getBasePath)
    assert(projectSettings != null)
    projectSettings.setSbtVersion(sbtVersion.minor)
  }

  private def annotate(): Seq[Message] = {
    val file = loadTestFile()
    val mock = new AnnotatorHolderMock(file)
    val annotator = new SbtAnnotator
    annotator.annotate(file)(mock)
    mock.annotations
  }

  private def setUpProjectSettings(): Unit = {
    val projectSettings = SbtProjectSettings.default
    projectSettings.setExternalProjectPath(getProject.getBasePath)
    projectSettings.setModules(java.util.Collections.singleton(getModule.getModuleFilePath))
    SbtSettings.getInstance(getProject).linkProject(projectSettings)
    getModule.setOption("external.root.project.path", getProject.getBasePath): @nowarn("cat=deprecation")
  }

  private def addTestFileToModuleSources(): Unit = {
    ModuleRootModificationUtil.updateModel(getModule, (model: ModifiableRootModel) => {
      val testdataUrl = VfsUtilCore.pathToUrl(testdataPath)
      model.addContentEntry(testdataUrl).addSourceFolder(testdataUrl, false)
    })
  }
}

@Category(Array(classOf[SlowTests2]))
@Ignore
class SbtAnnotatorTest_0_13_1 extends SbtAnnotatorTestBase with MockSbt_0_13 {
  override implicit val sbtVersion: SbtVersion = SbtVersion("0.13.1")

  def test(): Unit = runTest(sbtVersion, Expectations.sbt_0_13(sbtVersion))
}

@Category(Array(classOf[SlowTests2]))
@Ignore
class SbtAnnotatorTest_0_13_7 extends SbtAnnotatorTestBase with MockSbt_0_13 {
  override implicit val sbtVersion: SbtVersion = SbtVersion("0.13.7")

  def test(): Unit = runTest(sbtVersion, Expectations.sbt_0_13_7)
}

@Category(Array(classOf[SlowTests2]))
class SbtAnnotatorTest_1 extends SbtAnnotatorTestBase with MockSbt_1_0 {
  def test(): Unit = runTest(sbtVersion, Expectations.sbt_1_0)
}

@Category(Array(classOf[SlowTests2]))
class SbtAnnotatorTest_2 extends SbtAnnotatorTestBase with MockSbt_2 {
  def test(): Unit = runTest(sbtVersion, Expectations.sbt_2)
}

/**
  * Expected error messages for specific sbt versions. Newer versions usually allow more syntactic constructs in the sbt files
  */
object Expectations {
  import Message._

  val sbtAll: Seq[Error] = Seq(
    Error("object Bar", SbtBundle.message("sbt.annotation.sbtFileMustContainOnlyExpressions"))
  )

  def sbt012_013(sbtVersion: SbtVersion): Seq[Error] = sbtAll ++ Seq(
    Error("organization", SbtBundle.message("sbt.annotation.expressionMustConform", "SettingKey[String]")),
    Error(""""some string"""", SbtBundle.message("sbt.annotation.expressionMustConform", "String")),
    Error("null", SbtBundle.message("sbt.annotation.expectedExpressionType")),
    Error("""version := "SNAPSHOT"""", SbtBundle.message("sbt.annotation.blankLineRequired", sbtVersion.minor))
  )

  def sbt_0_12: Seq[Error] = sbt012_013(SbtVersion("0.12.4")) ++ Seq(
    Error(
      """lazy val foo = project.in(file("foo")).enablePlugins(sbt.plugins.JvmPlugin)""",
      SbtBundle.message("sbt.annotation.sbtFileMustContainOnlyExpressions"))
  )

  def sbt_0_13(sbtVersion: SbtVersion): Seq[Error] = sbt012_013(sbtVersion) ++ Seq(
    Error("???", SbtBundle.message("sbt.annotation.expectedExpressionType"))
  )

  val sbt_0_13_7: Seq[Error] = sbtAll ++ Seq(
    Error("organization", SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", "SettingKey[String]")),
    Error(""""some string"""", SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", "String")),
    Error("null", SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136")),
    Error("???", SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136"))
  )

  val sbt_1_0: Seq[Error] = sbt_0_13_7

  // TODO: we need to review SBT 2.0 new rules and adopt SbtAnnotator.scala along with the expected data
  val sbt_2: Seq[Error] = sbtAll ++ Seq(
    Error("organization", SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", "SettingKey[String]")),
    Error(""""some string"""", SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", "\"some string\"")),
    Error("null", SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136")),
    Error("???", SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136"))
  )
}
