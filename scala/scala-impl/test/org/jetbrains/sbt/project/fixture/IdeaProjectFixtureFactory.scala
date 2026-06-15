package org.jetbrains.sbt.project.fixture

import com.intellij.openapi.Disposable
import com.intellij.testFramework.fixtures.{HeavyIdeaTestFixturePathProvider, IdeaProjectTestFixture, IdeaTestFixtureFactory}

import java.nio.file.Path

object IdeaProjectFixtureFactory {

  def createProjectFixture(
    testName: String,
    testProjectPath: => Path,
    useTestProjectAsIdeaProjectRoot: Boolean,
  ): IdeaProjectTestFixture = {
    val isDirectoryBasedProject = useTestProjectAsIdeaProjectRoot

    val fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory
    val fixtureBuilder =
      if (useTestProjectAsIdeaProjectRoot) {
        // Use the test project dir name as the project name.
        val name = testProjectPath.getFileName.toString
        // The fixture framework appends the fixture name to this provider path.
        // (see com.intellij.testFramework.fixtures.impl.HeavyIdeaTestFixtureImpl.generateProjectPath)
        val projectPathParent = testProjectPath.getParent
        val pathProvider: HeavyIdeaTestFixturePathProvider = (_: String, _: Disposable) => projectPathParent
        fixtureFactory.createFixtureBuilder(name, pathProvider, isDirectoryBasedProject)
      } else {
        // Use the test case name as the project name.
        // The path-provider overload rejects null; use the legacy overload to let the fixture framework choose a temp path.
        fixtureFactory.createFixtureBuilder(testName, isDirectoryBasedProject)
      }

    fixtureBuilder.getFixture
  }
}
