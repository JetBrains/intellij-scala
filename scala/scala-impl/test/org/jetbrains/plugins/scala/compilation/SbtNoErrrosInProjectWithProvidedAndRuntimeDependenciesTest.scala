package org.jetbrains.plugins.scala.compilation

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.CompilerTester
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, IdeaTestFixtureFactory}
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase.ListCompilerMessageExt
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.projectHighlighting.AllProjectHighlightingTest
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.jetbrains.sbt.project.{ImportingTestCase, ProjectStructureMatcher}
import org.junit.experimental.categories.Category

import java.io.File

@Category(Array(classOf[HighlightingTests]))
class SbtNoErrrosInProjectWithProvidedAndRuntimeDependenciesTest
  extends ImportingTestCase
    with AllProjectHighlightingTest {

  protected var codeInsightFixture: CodeInsightTestFixture = _

  override def testProjectDir: File = {
    val testDataPath = TestUtils.getTestDataPath + "/sbt/compilation/projects"
    new File(testDataPath, getTestName(true))
  }

  override def setUpFixtures(): Unit = {
    val factory = IdeaTestFixtureFactory.getFixtureFactory
    val projectFixture =  factory.createFixtureBuilder(getName).getFixture
    codeInsightFixture = factory.createCodeInsightFixture(projectFixture)
    codeInsightFixture.setUp()
    myTestFixture = codeInsightFixture
  }

  override protected def tearDownFixtures(): Unit = {
    codeInsightFixture.tearDown()
    codeInsightFixture = null
    myTestFixture = null
  }

  override def getProject: Project = myProject

  override def getProjectFixture: CodeInsightTestFixture = codeInsightFixture

  override protected val reporter: ProgressReporter =
    ProgressReporter.newInstance(getClass.getSimpleName, Map.empty)

  override protected def defaultAssertMatch: ProjectStructureMatcher.AttributeMatchType = ???

  def testProvidedAndRuntimeDependenciesShouldBeAddedToTestCompileScope(): Unit = {
    importProject()

    //ensure that there is no errors when using class from Runtime dependencies from tests
    //...
    //during code highlighting
    doAllProjectHighlightingTest()

    //during project compilation
    val compiler: CompilerTester = new CompilerTester(myTestFixture.getModule)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = IncrementalityType.IDEA

    CompilerTestUtil.withEnabledCompileServer(true).run {
      try {
        compiler.rebuild().assertNoProblems()
      } finally {
        compiler.tearDown()
        ScalaCompilerTestBase.stopAndWait()

        val table = ProjectJdkTable.getInstance
        inWriteAction {
          table.getAllJdks.foreach(table.removeJdk)
        }
      }
    }
  }
}
