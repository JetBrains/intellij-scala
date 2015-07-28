package org.jetbrains.sbt
package annotator

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.startup.StartupManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.project.module.SbtModuleType

import scala.collection.JavaConverters._


/**
 * @author Nikolay Obedin
 * @since 7/23/15.
 */

class SbtAnnotatorMock(sbtVersion: String) extends SbtAnnotator {
  override def getSbtVersion(element: PsiElement): String = sbtVersion
}

abstract class SbtAnnotatorTestBase extends AnnotatorTestBase with MockSbt {
  protected def annotator: Annotator

  override protected def setUp(): Unit = {
    super.setUp()
    addSbtAsModuleDependency(createBuildModule())
    inWriteAction(StartupManager.getInstance(getProject).asInstanceOf[StartupManagerImpl].startCacheUpdate())
  }

  override def loadTestFile(): SbtFileImpl = {
    val file = super.loadTestFile()
    file.putUserData(ModuleUtilCore.KEY_MODULE, getModule)
    file
  }

  override def getTestProjectJdk: Sdk =
    JavaSdk.getInstance().createJdk("java sdk", TestUtils.getMockJdk, false);

  protected def doTest(messages: Seq[Message]) {
    val mock = new AnnotatorHolderMock
    annotator.annotate(loadTestFile(), mock)
    UsefulTestCase.assertSameElements(mock.annotations.asJava, messages:_*)
  }

  private def createBuildModule(): Module = inWriteAction {
    val moduleName = getModule.getName + Sbt.BuildModuleSuffix + ".iml"
    val module = ModuleManager.getInstance(getProject).newModule(moduleName, SbtModuleType.instance.getId)
    ModuleRootModificationUtil.setModuleSdk(module, getTestProjectJdk)
    module
  }
}


class SbtAnnotatorTest012 extends SbtAnnotatorTestBase {
  override def annotator = new SbtAnnotatorMock("0.12.4")

  def testSbtAnnotator =
    doTest(Seq(
      Error("version := \"SNAPSHOT\"", SbtBundle("sbt.annotation.blankLineRequired", "0.12.4")),
      Error("lazy val foo = project.in(file(\"foo\"))", SbtBundle("sbt.annotation.sbtFileMustContainOnlyExpressions"))
    ))
}

class SbtAnnotatorTest013 extends SbtAnnotatorTestBase {
  override def annotator = new SbtAnnotatorMock("0.13.1")

  def testSbtAnnotator =
    doTest(Seq(
      Error("version := \"SNAPSHOT\"", SbtBundle("sbt.annotation.blankLineRequired", "0.13.1"))
    ))
}

class SbtAnnotatorTest0137 extends SbtAnnotatorTestBase {
  override def annotator = new SbtAnnotatorMock("0.13.7")

  def testSbtAnnotator =
    doTest(Seq.empty)
}

class SbtAnnotatorTestNullVersion extends SbtAnnotatorTestBase {
  override def annotator = new SbtAnnotatorMock(null)

  def testSbtAnnotator =
    doTest(Seq.empty)
}
