package scala.meta

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

import scala.meta.intellij.IDEAContext

abstract class TreeConverterTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with TreeConverterTestUtils {

  private var _context = new IDEAContext(fixture.getProject) {
    override def dumbMode: Boolean = true
    override def getCurrentProject: Project = myFixture.getProject
  }

  override def context = _context

  override def fixture = myFixture

  //ideally IDEAContext caches should be cleared on project dispose, but this functionality is deprecated and will be removed anyway
  override protected def tearDown(): Unit = {
    super.tearDown()
    _context = null
  }
}

abstract class TreeConverterTestBaseNoLibrary extends TreeConverterTestBase {
  override def loadScalaLibrary = false
}

abstract class TreeConverterTestBaseWithLibrary extends TreeConverterTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}
