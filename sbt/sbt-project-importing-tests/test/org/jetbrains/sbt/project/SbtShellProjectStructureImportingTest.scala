package org.jetbrains.sbt.project

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.project.ProjectExt

class SbtShellProjectStructureImportingTest extends SbtProjectStructureImportingTestBase_ProdTestSourcesSeparated {

  override def setUp(): Unit = {
    getCurrentExternalProjectSettings.useSbtShellForImport = true
    super.setUp()
    inWriteAction(ProjectRootManager.getInstance(getProject).setProjectSdk(getJdkConfiguredForTestCase))
  }

  override protected def setUpFixtures(): Unit = {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(getName, getTestProjectPath, useDirectoryBasedStorageFormat()).getFixture
    myTestFixture.setUp()
  }

  override protected def getProjectPath: String = getTestProjectPath.toCanonicalPath.toString
}
