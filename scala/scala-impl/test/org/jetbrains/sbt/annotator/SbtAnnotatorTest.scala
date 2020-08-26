package org.jetbrains.sbt
package annotator

import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.{ModifiableRootModel, ModuleRootModificationUtil}
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.annotator.{Error, _}
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, SmartJDKLoader}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

abstract class SbtAnnotatorTestBase extends org.jetbrains.sbt.annotator.AnnotatorTestBase with MockSbtBase {

  implicit protected lazy val module: Module = inWriteAction {
    val moduleName = getModule.getName + Sbt.BuildModuleSuffix + ".iml"
    val module = ModuleManager.getInstance(getProject).newModule(moduleName, SbtModuleType.instance.getId)
    ModuleRootModificationUtil.setModuleSdk(module, getTestProjectJdk)
    module
  }

  override def librariesLoaders: Seq[LibraryLoader] =
    HeavyJDKLoader() +: super.librariesLoaders

  override protected def setUp(): Unit = {
    super.setUp()
    setUpLibraries(module)
    addTestFileToModuleSources()
    setUpProjectSettings()
  }

  override def tearDown(): Unit = {
    disposeLibraries(module)
    super.tearDown()
  }

  override def loadTestFile() = {
    val result = super.loadTestFile()
    result.putUserData(ModuleUtilCore.KEY_MODULE, getModule)
    result
  }

  override def getTestName(lowercaseFirstLetter: Boolean) = "SbtAnnotator"

  override def getTestProjectJdk: Sdk = SmartJDKLoader.getOrCreateJDK()

  protected def runTest(sbtVersion: Version, expectedMessages: Seq[Message]): Unit = {
    setSbtVersion(sbtVersion)
    val actualMessages = annotate().asJava
    UsefulTestCase.assertSameElements(actualMessages, expectedMessages: _*)
  }

  protected def setSbtVersion(sbtVersion: Version): Unit = {
    val projectSettings = SbtSettings.getInstance(getProject).getLinkedProjectSettings(getProject.getBasePath)
    assert(projectSettings != null)
    projectSettings.setSbtVersion(sbtVersion.presentation)
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
    getModule.setOption("external.root.project.path", getProject.getBasePath) // TODO get rid of the deprecated method call
  }

  private def addTestFileToModuleSources(): Unit = {
    ModuleRootModificationUtil.updateModel(getModule, (model: ModifiableRootModel) => {
      val testdataUrl = VfsUtilCore.pathToUrl(testdataPath)
      model.addContentEntry(testdataUrl).addSourceFolder(testdataUrl, false)
    })
  }
}

@Category(Array(classOf[SlowTests]))
class SbtAnnotatorTest_0_12_4 extends SbtAnnotatorTestBase with MockSbt_0_12 {
  override implicit val sbtVersion: Version = Version("0.12.4")

  def test(): Unit = runTest(sbtVersion, Expectations.sbt_0_12)
}

@Category(Array(classOf[SlowTests]))
class SbtAnnotatorTest_0_13_1 extends SbtAnnotatorTestBase with MockSbt_0_13 {
  override implicit val sbtVersion: Version = Version("0.13.1")

  def test(): Unit = runTest(sbtVersion, Expectations.sbt_0_13(sbtVersion))
}

@Category(Array(classOf[SlowTests]))
class SbtAnnotatorTest_0_13_7 extends SbtAnnotatorTestBase with MockSbt_0_13 {
  override implicit val sbtVersion: Version = Version("0.13.7")

  def test(): Unit = runTest(sbtVersion, Expectations.sbt_0_13_7)
}

@Category(Array(classOf[SlowTests]))
class SbtAnnotatorTest_latest extends SbtAnnotatorTestBase with MockSbt_1_0 {
  override implicit val sbtVersion: Version = Sbt.LatestVersion

  def test(): Unit = runTest(sbtVersion, Expectations.sbt_1_0)
}

/**
  * Expected error messages for specific sbt versions. Newer versions usually allow more syntactic constructs in the sbt files
  */
object Expectations {
  val sbtAll: Seq[Error] = Seq(
    Error("object Bar", SbtBundle.message("sbt.annotation.sbtFileMustContainOnlyExpressions"))
  )


  def sbt012_013(sbtVersion: Version): Seq[Error] = sbtAll ++ Seq(
    Error("organization", SbtBundle.message("sbt.annotation.expressionMustConform", "SettingKey[String]")),
    Error(""""some string"""", SbtBundle.message("sbt.annotation.expressionMustConform", "String")),
    Error("null", SbtBundle.message("sbt.annotation.expectedExpressionType")),
    Error("""version := "SNAPSHOT"""", SbtBundle.message("sbt.annotation.blankLineRequired", sbtVersion.presentation))
  )

  def sbt_0_12: Seq[Error] = sbt012_013(Version("0.12.4")) ++ Seq(
    Error(
      """lazy val foo = project.in(file("foo")).enablePlugins(sbt.plugins.JvmPlugin)""",
      SbtBundle.message("sbt.annotation.sbtFileMustContainOnlyExpressions"))
  )

  def sbt_0_13(sbtVersion: Version): Seq[Error] = sbt012_013(sbtVersion) ++ Seq(
    Error("???", SbtBundle.message("sbt.annotation.expectedExpressionType"))
  )

  val sbt_0_13_7: Seq[Error] = sbtAll ++ Seq(
    Error("organization", SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", "SettingKey[String]")),
    Error(""""some string"""", SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", "String")),
    Error("null", SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136")),
    Error("???", SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136"))
  )

  val sbt_1_0: Seq[Error] = sbt_0_13_7
}
