package scala.meta

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_11}

import scala.meta.intellij.IDEAContext

abstract class TreeConverterTestBase extends ScalaLightCodeInsightFixtureTestAdapter with TreeConverterTestUtils {

  def fixture = myFixture

  override val context = new IDEAContext(fixture.getProject) {
    override def dumbMode: Boolean = true
    override def getCurrentProject: Project = myFixture.getProject
  }
}

abstract class TreeConverterTestBaseNoLibrary extends TreeConverterTestBase {
  override def loadScalaLibrary = false
}

abstract class TreeConverterTestBaseWithLibrary extends TreeConverterTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_11
}
