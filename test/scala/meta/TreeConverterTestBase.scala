package scala.meta

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}

import scala.meta.semantic.IDEAContext

abstract class TreeConverterTestBase extends ScalaLightCodeInsightFixtureTestAdapter with TreeConverterTestUtils {

  def fixture = myFixture

  val semanticContext = new IDEAContext(fixture.getProject) {
    override def dumbMode: Boolean = true

    override def getCurrentProject: Project = myFixture.getProject
  }
//  def testOk() = () // to get rid of no tests found spam in IDEA junit runner
}

abstract class TreeConverterTestBaseNoLibrary extends TreeConverterTestBase {
  override def loadScalaLibrary = false

//  override def testOk() = () // to get rid of no tests found spam in IDEA junit runner
}

abstract class TreeConverterTestBaseWithLibrary extends TreeConverterTestBase {
  override implicit val version: ScalaVersion = Scala_2_11

//  override def testOk() = () // to get rid of no tests found spam in IDEA junit runner
}
