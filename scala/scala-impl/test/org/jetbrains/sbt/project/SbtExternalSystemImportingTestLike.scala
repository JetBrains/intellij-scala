package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.projectHighlighting.base.ProjectHighlightingTestUtils
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings

trait SbtExternalSystemImportingTestLike extends ScalaExternalSystemImportingTestBase {

  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  protected def enableSeparateModulesForProdTest: Boolean = false

  override protected def setupProjectJdk(): Unit = {
    super.setupProjectJdk()
    getCurrentExternalProjectSettings.jdk = getJdkConfiguredForTestCase.getName
  }

  override def setUp(): Unit = {
    getCurrentExternalProjectSettings.separateProdAndTestSources = enableSeparateModulesForProdTest
    super.setUp()
  }

  override protected def setUpFixtures(): Unit = {
    super.setUpFixtures()

    //need to do this before actual import is started in `setUp` method
    ProjectHighlightingTestUtils.dontPrintErrorsAndWarningsToConsole(this)
  }

  private lazy val currentExternalProjectSettings: SbtProjectSettings =
    new SbtProjectSettings

  override protected def getCurrentExternalProjectSettings: SbtProjectSettings = currentExternalProjectSettings

  /**
   * It is necessary to explicitly set all project settings that are tested/required for test, because what is set in
   * #setUp method in each SbtProjectStructureImportingTest classes is not applied to the project settings of the linked project
   */
  protected def linkSbtProject(path: String, prodTestSourcesSeparated: Boolean, project: Project): Unit = {
    val settings = new SbtProjectSettings
    settings.jdk = getJdkConfiguredForTestCase.getName
    settings.setExternalProjectPath(path)
    settings.setSeparateProdAndTestSources(prodTestSourcesSeparated)
    SbtSettings.getInstance(project).linkProject(settings)
  }
}