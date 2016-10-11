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

abstract class SbtAnnotatorTestBase extends AnnotatorTestBase with MockSbt {

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

  protected def runTest(sbtVersion: String, expectedMessages: Seq[Message]): Unit = {
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
    val file = loadTestFile()
    val mock = new AnnotatorHolderMock(file)
    val annotator = new SbtAnnotator
    annotator.annotate(file, mock)
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

class SbtAnnotatorTest_0_13_1 extends SbtAnnotatorTestBase {
  override def sbtVersion: String = "0.13.1"
  def test(): Unit = runTest(sbtVersion, Expectations.sbt0131)
}
class SbtAnnotatorTest_0_13_7 extends SbtAnnotatorTestBase {
  override def sbtVersion: String = "0.13.7"
  def test(): Unit = runTest(sbtVersion, Expectations.sbt0137)
}

class SbtAnnotatorTest_latest extends SbtAnnotatorTestBase {
  override def sbtVersion: String = Sbt.LatestVersion
  def test(): Unit = runTest(sbtVersion, Expectations.sbt0137)
}

/**
  * Expected error messages for specific sbt versions. Newer versions usually allow more syntactic constructs in the sbt files
  */
object Expectations {

  val sbtAll: Seq[Error] = Seq(
    Error("object Bar", SbtBundle("sbt.annotation.sbtFileMustContainOnlyExpressions"))
  )

  val sbt0137: Seq[Error] = sbtAll ++ Seq(
    Error("organization", SbtBundle("sbt.annotation.expressionMustConformSbt0136", "SettingKey[String]")),
    Error(""""some string"""", SbtBundle("sbt.annotation.expressionMustConformSbt0136", "String")),
    Error("null", SbtBundle("sbt.annotation.expectedExpressionTypeSbt0136")),
    Error("???", SbtBundle("sbt.annotation.expectedExpressionTypeSbt0136"))
  )

  val sbt013_1to5: Seq[Error] = sbtAll ++ Seq(
    Error("organization", SbtBundle("sbt.annotation.expressionMustConform", "SettingKey[String]")),
    Error(""""some string"""", SbtBundle("sbt.annotation.expressionMustConform", "String")),
    Error("null", SbtBundle("sbt.annotation.expectedExpressionType")),
    Error("???", SbtBundle("sbt.annotation.expectedExpressionType"))
  )

  val sbt0131: Seq[Error] = sbt013_1to5 ++ Seq(
    Error("""version := "SNAPSHOT"""", SbtBundle("sbt.annotation.blankLineRequired", "0.13.1"))
  )

}
