package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, ModuleType}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFileUtil
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaSDKLoader
import com.intellij.openapi.module.Module
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class Scala2Scala3InteropUnapplyTest extends JavaCodeInsightFixtureTestCase {
  val scalaModuleSdkLoader = ScalaSDKLoader()

  val scala3ModuleName = "scala3Module"
  val scala3ModuleSourceDir = s"$scala3ModuleName/src"
  var scala3Module: Module = _

  override def setUp(): Unit = {
    super.setUp()

    scalaModuleSdkLoader.init(getModule, ScalaVersion.Latest.Scala_2_13)

    scala3Module =
      PsiTestUtil.addModule(
        getProject,
        JavaModuleType.getModuleType.asInstanceOf[ModuleType[_ <: ModuleBuilder]],
        scala3ModuleName,
        myFixture.getTempDirFixture.findOrCreateDir(scala3ModuleName)
      )

    PsiTestUtil.addSourceRoot(scala3Module, myFixture.getTempDirFixture.findOrCreateDir(scala3ModuleSourceDir))
    scalaModuleSdkLoader.init(scala3Module, ScalaVersion.Latest.Scala_3_LTS)
    ModuleRootModificationUtil.addDependency(getModule, scala3Module)
  }

  override def tearDown(): Unit = {
    scalaModuleSdkLoader.clean(scala3Module)
    scalaModuleSdkLoader.clean(getModule)
    super.tearDown()
  }

  private def doTest(scala3Code: String, scala2Code: String): Unit = {
    myFixture.addFileToProject(s"$scala3ModuleSourceDir/Test.scala", scala3Code)
    myFixture.configureByText(ScalaFileType.INSTANCE, scala2Code)
    myFixture.testHighlighting(false, false, false, myFixture.getFile.getVirtualFile)
  }

  def test_unapply(): Unit = doTest(
    """
      |case class TestCaseClass(value: Int)
      |
      |""".stripMargin,
    """
      |object Blub {
      |  TestCaseClass(1) match {
      |    case TestCaseClass(i) =>
      |      val x: Int = i
      |  }
      |}
      |""".stripMargin
  )

  def test_generic_unapply(): Unit = doTest(
    """
      |case class TestCaseClass[T](value: T)
      |
      |""".stripMargin,
    """
      |object Blub {
      |  TestCaseClass(1) match {
      |    case TestCaseClass(i) =>
      |      val x: Int = i
      |  }
      |}
      |""".stripMargin
  )

  def test_unapplySeq(): Unit = doTest(
    """
      |case class TestCaseClass(value: Boolean, values: Int*)
      |""".stripMargin,
    """
      |object Blub {
      |  TestCaseClass(true, Seq(1)) match {
      |    case TestCaseClass(a, b) =>
      |      val x: Boolean = a
      |      val y: Int = b
      |  }
      |}
      |""".stripMargin
  )

  def test_generic_unapplySeq(): Unit = doTest(
    """
      |case class TestCaseClass[A, B](value: A, values: B*)
      |
      |""".stripMargin,
    """
      |object Blub {
      |  TestCaseClass(true, 1) match {
      |    case TestCaseClass(a, b) =>
      |      val x: Boolean = a
      |      val y: Int = b
      |  }
      |}
      |""".stripMargin
  )
}

