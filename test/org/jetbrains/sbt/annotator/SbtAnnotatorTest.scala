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
  def sbtVersion: String

  override protected def setUp(): Unit = {
    super.setUp()
    addSbtAsModuleDependency(createBuildModule())
    addTestFileToModuleSources()
    setUpSbtVersion()
    inWriteAction(StartupManager.getInstance(getProject).asInstanceOf[StartupManagerImpl].startCacheUpdate())
  }

  override def loadTestFile(): SbtFileImpl = {
    val fileName = getTestName(false) + ".sbt"
    val filePath = testdataPath + fileName
    val vfile = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    val psifile = PsiManager.getInstance(getProject).findFile(vfile)
    psifile.putUserData(ModuleUtilCore.KEY_MODULE, getModule)
    psifile.asInstanceOf[SbtFileImpl]
  }

  override def getTestProjectJdk: Sdk =
    JavaSdk.getInstance().createJdk("java sdk", TestUtils.getMockJdk, false)

  protected def doTest(messages: Seq[Message]) {
    val mock = new AnnotatorHolderMock
    val annotator = new SbtAnnotator
    annotator.annotate(loadTestFile(), mock)
    UsefulTestCase.assertSameElements(mock.annotations.asJava, messages:_*)
  }

  private def createBuildModule(): Module = inWriteAction {
    val moduleName = getModule.getName + Sbt.BuildModuleSuffix + ".iml"
    val module = ModuleManager.getInstance(getProject).newModule(moduleName, SbtModuleType.instance.getId)
    ModuleRootModificationUtil.setModuleSdk(module, getTestProjectJdk)
    module
  }

  private def setUpSbtVersion(): Unit = {
    val projectSettings = SbtProjectSettings.default
    projectSettings.sbtVersion = sbtVersion
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

class SbtAnnotatorTest012 extends SbtAnnotatorTestBase {
  override def sbtVersion = "0.12.4"
  def testSbtAnnotator = doTest(Expectations.sbt012)
}

class SbtAnnotatorTest013 extends SbtAnnotatorTestBase {
  override def sbtVersion = "0.13.1"
  def testSbtAnnotator = doTest(Expectations.sbt013)
}

class SbtAnnotatorTest0137 extends SbtAnnotatorTestBase {
  override def sbtVersion = "0.13.7"
  def testSbtAnnotator = doTest(Expectations.sbt0137)
}

class SbtAnnotatorTestNullVersion extends SbtAnnotatorTestBase {
  override def sbtVersion = null
  def testSbtAnnotator = doTest(Expectations.sbt0137)
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