package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_10}
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

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(HeavyJDKLoader(), ScalaSDKLoader())
}

object IntroduceVariableTest {
  val DATA_PATH = "/introduceVariable/data"

  def suite = new IntroduceVariableTest
}
