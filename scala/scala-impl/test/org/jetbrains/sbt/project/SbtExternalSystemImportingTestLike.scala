package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.projectHighlighting.base.ProjectHighlightingTestUtils
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
trait SbtExternalSystemImportingTestLike extends ScalaExternalSystemImportingTestBase {

  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  protected def enableSeparateModulesForProdTest: Boolean = true

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
}