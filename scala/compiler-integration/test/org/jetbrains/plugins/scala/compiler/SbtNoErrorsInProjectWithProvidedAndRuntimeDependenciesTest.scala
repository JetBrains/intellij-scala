package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.CompilerTester
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, IdeaTestFixtureFactory}
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase.ListCompilerMessageExt
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.{CompilerTestUtil, TestUtils}
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
final class SbtNoErrorsInProjectWithProvidedAndRuntimeDependenciesTest
  extends SbtExternalSystemImportingTestLike
    with AllProjectHighlightingTest {

  protected var codeInsightFixture: CodeInsightTestFixture = _

  override protected def getTestProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/compilation/projects/${getTestName(true)}"

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

  override protected val reporter: HighlightingProgressReporter =
    HighlightingProgressReporter.newInstance(getClass.getSimpleName, Map.empty)

  def testProvidedAndRuntimeDependenciesShouldBeAddedToTestCompileScope(): Unit = {
    importProject(false)

    //ensure that there is no errors when using class from Runtime dependencies from tests
    //during code highlighting
    doAllProjectHighlightingTest()

    //... and during project compilation
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
