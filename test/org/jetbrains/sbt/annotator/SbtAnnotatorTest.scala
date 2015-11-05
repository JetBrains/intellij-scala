package org.jetbrains.sbt
package annotator

import java.io.File

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.roots.{ModifiableRootModel, ModuleRootModificationUtil}
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtilCore}
import com.intellij.psi.PsiManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSystemSettings

import scala.collection.JavaConverters._


/**
 * @author Nikolay Obedin
 * @since 7/23/15.
 */

class SbtAnnotatorTest extends AnnotatorTestBase with MockSbt {

  def test_0_12_4: Unit = runTest("0.12.4", Expectations.sbt012)

  def test_0_13_1: Unit = runTest("0.13.1", Expectations.sbt013)

  def test_0_13_7: Unit = runTest("0.13.7", Expectations.sbt0137)

  def testNullVersion: Unit = runTest(null, Expectations.sbt0137)

  override protected def setUp(): Unit = {
    super.setUp()
    addSbtAsModuleDependency(createBuildModule())
    addTestFileToModuleSources()
    setUpProjectSettings()
    inWriteAction(StartupManager.getInstance(getProject).asInstanceOf[StartupManagerImpl].startCacheUpdate())
  }

  override def loadTestFile(): SbtFileImpl = {
    val fileName = "SbtAnnotator.sbt"
    val filePath = testdataPath + fileName
    val vfile = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    val psifile = PsiManager.getInstance(getProject).findFile(vfile)
    psifile.putUserData(ModuleUtilCore.KEY_MODULE, getModule)
    psifile.asInstanceOf[SbtFileImpl]
  }

  override def getTestProjectJdk: Sdk =
    JavaSdk.getInstance().createJdk("java sdk", TestUtils.getDefaultJdk, false)

  private def runTest(sbtVersion: String, expectedMessages: Seq[Message]): Unit = {
    setSbtVersion(sbtVersion)
    val actualMessages = annotate().asJava
    UsefulTestCase.assertSameElements(actualMessages, expectedMessages:_*)
  }

  private def setSbtVersion(sbtVersion: String): Unit = {
    val projectSettings = SbtSystemSettings.getInstance(getProject).getLinkedProjectSettings(getProject.getBasePath)
    assert(projectSettings != null)
    projectSettings.setSbtVersion(sbtVersion)
  }

  private def annotate(): Seq[Message] = {
    val mock = new AnnotatorHolderMock
    val annotator = new SbtAnnotator
    annotator.annotate(loadTestFile(), mock)
    mock.annotations
  }

  private def createBuildModule(): Module = inWriteAction {
    val moduleName = getModule.getName + Sbt.BuildModuleSuffix + ".iml"
    val module = ModuleManager.getInstance(getProject).newModule(moduleName, SbtModuleType.instance.getId)
    ModuleRootModificationUtil.setModuleSdk(module, getTestProjectJdk)
    module
  }

  private def setUpProjectSettings(): Unit = {
    val projectSettings = SbtProjectSettings.default
    projectSettings.setExternalProjectPath(getProject.getBasePath)
    projectSettings.setModules(java.util.Collections.singleton(getModule.getModuleFilePath))
    SbtSystemSettings.getInstance(getProject).linkProject(projectSettings)
    getModule.setOption(ExternalSystemConstants.ROOT_PROJECT_PATH_KEY, getProject.getBasePath)
  }

  private def addTestFileToModuleSources(): Unit = {
    ModuleRootModificationUtil.updateModel(getModule, new Consumer[ModifiableRootModel] {
      override def consume(model: ModifiableRootModel): Unit = {
        val testdataUrl = VfsUtilCore.pathToUrl(testdataPath)
        model.addContentEntry(testdataUrl).addSourceFolder(testdataUrl, false)
      }
    })
    preventLeakageOfVfsPointers()
  }
}

object Expectations {
  val sbt0137 = Seq(
    Error("object Bar", SbtBundle("sbt.annotation.sbtFileMustContainOnlyExpressions")),
    Error("null", SbtBundle("sbt.annotation.expectedExpressionType")),
    Error("???", SbtBundle("sbt.annotation.expectedExpressionType")),
    Error("organization", SbtBundle("sbt.annotation.expressionMustConform", "SettingKey[String]")),
    Error("\"some string\"", SbtBundle("sbt.annotation.expressionMustConform", "String"))
  )

  val sbt013 = sbt0137 :+
    Error("version := \"SNAPSHOT\"", SbtBundle("sbt.annotation.blankLineRequired", "0.13.1"))

  val sbt012 = sbt0137 ++ Seq(
    Error("version := \"SNAPSHOT\"", SbtBundle("sbt.annotation.blankLineRequired", "0.12.4")),
    Error("lazy val foo = project.in(file(\"foo\")).enablePlugins(sbt.plugins.JvmPlugin)",
      SbtBundle("sbt.annotation.sbtFileMustContainOnlyExpressions"))
  )
}