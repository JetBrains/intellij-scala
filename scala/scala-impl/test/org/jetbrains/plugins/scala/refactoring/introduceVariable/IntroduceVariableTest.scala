package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.openapi.module.{Module, ModuleManager}
import org.jetbrains.plugins.scala.base.libraryLoaders.{JdkLoader, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaSdkOwner, ScalaVersion, Scala_2_10}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.runner.RunWith
import org.junit.runners.AllTests

/**
  * Nikolay.Tropin
  * 25-Sep-17
  */
@RunWith(classOf[AllTests])
class IntroduceVariableTest extends AbstractIntroduceVariableTestBase(TestUtils.getTestDataPath + IntroduceVariableTest.DATA_PATH)
  with ScalaSdkOwner {

  override implicit val version: ScalaVersion = Scala_2_10

  override def project = getProject

  override implicit def module: Module = ModuleManager.getInstance(project).getModules()(0)

  override protected def librariesLoaders = Seq(JdkLoader(), ScalaLibraryLoader())
}

object IntroduceVariableTest {
  val DATA_PATH = "/introduceVariable/data"

  def suite = new IntroduceVariableTest
}
