package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.plugins.scala.projectHighlighting.base.ProjectHighlightingTestUtils
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.settings.SbtProjectSettings

trait SbtExternalSystemImportingTestLike extends ScalaExternalSystemImportingTestBase {

  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def setUpFixtures(): Unit = {
    super.setUpFixtures()

    //need to do this before actual import is started in `setUp` method
    ProjectHighlightingTestUtils.dontPrintErrorsAndWarningsToConsole(this)
  }

  private lazy val currentExternalProjectSettings: SbtProjectSettings = {
    val settings = new SbtProjectSettings
    settings.jdk = getJdkConfiguredForTestCase.getName
    settings
  }

  override protected def getCurrentExternalProjectSettings: SbtProjectSettings = currentExternalProjectSettings
}