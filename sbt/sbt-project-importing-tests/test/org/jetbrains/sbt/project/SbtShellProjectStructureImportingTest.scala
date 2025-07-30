package org.jetbrains.sbt.project

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.plugins.scala.extensions.PathExt

class SbtShellProjectStructureImportingTest extends SbtProjectStructureImportingTestBase_ProdTestSourcesSeparated {

  override def setUp(): Unit = {
    getCurrentExternalProjectSettings.useSbtShellForImport = true
    super.setUp()
  }

  override protected def setUpFixtures(): Unit = {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(getName, getTestProjectPath, useDirectoryBasedStorageFormat()).getFixture
    myTestFixture.setUp()
  }

  override protected def getProjectPath: String = getTestProjectPath.toCanonicalPath.toString
}
