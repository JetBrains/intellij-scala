package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike.TestSbtProjectSettings
import org.jetbrains.sbt.project.settings.SbtProjectSettings

import java.nio.file.Path

/**
 * Common sbt-specific layer over [[ScalaExternalSystemImportingTestBase]].
 *
 * This trait binds the generic external-system test fixture to sbt:
 *  - selects `build.sbt` as the external-system config file
 *  - selects [[SbtProjectSystem.Id]] as the external-system id
 *  - creates and exposes the current [[SbtProjectSettings]] instance for the linked project
 *  - configures the linked project JDK from the test-case JDK
 *  - applies test-specific sbt project settings from [[getTestSbtProjectSettings]]
 *  - sets up Coursier/Ivy cache forwarding for sbt imports
 *  - suppresses duplicate sbt structure-dump error/warning console output
 *  - provides a helper for injecting values into copied test-data files
 *
 * Subclasses supply the remaining test workflow:
 *  - test-data path and fixture shape
 *  - the point where [[importProject]] is invoked
 *  - model, notification, highlighting, runtime, or other assertions around the imported project
 *
 * @todo make an abstract class instead, refactor the project-highlighting tests instead
 */
trait SbtExternalSystemImportingTestLike extends ScalaExternalSystemImportingTestBase {

  final override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  final override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()

    setupSbtProjectSettings()

    SbtCachesSetupUtil.setupCoursierAndIvyCache(getMyProject)
    SbtProjectImportTestUtils.suppressSbtStructureDumpErrorAndWarningConsoleOutput(this)
  }

  protected def getTestSbtProjectSettings: TestSbtProjectSettings =
    TestSbtProjectSettings.Default

  final override protected lazy val getCurrentExternalProjectSettings: SbtProjectSettings =
    new SbtProjectSettings

  private def setupSbtProjectSettings(): Unit = {
    val settings = getCurrentExternalProjectSettings
    settings.jdk = getJdkConfiguredForTestCase.getName

    val testSbtSettings = getTestSbtProjectSettings
    settings.separateProdAndTestSources = testSbtSettings.separateProdAndTestSources
    settings.useSbtShellForImport = testSbtSettings.useSbtShellForImport
  }

  final protected def injectVariable(file: Path, variableName: String, value: String): Unit =
    SbtProjectImportTestUtils.injectVariable(file, variableName, value)
}

object SbtExternalSystemImportingTestLike {
  final case class TestSbtProjectSettings(
    separateProdAndTestSources: Boolean,
    useSbtShellForImport: Boolean
  )

  object TestSbtProjectSettings {
    val Default: TestSbtProjectSettings = {
      val settings = new SbtProjectSettings
      TestSbtProjectSettings(
        separateProdAndTestSources = settings.separateProdAndTestSources,
        useSbtShellForImport = settings.useSbtShellForImport
      )
    }
  }
}
