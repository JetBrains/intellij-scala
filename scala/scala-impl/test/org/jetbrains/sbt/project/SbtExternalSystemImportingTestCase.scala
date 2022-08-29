package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.structure.SbtStructureDump
import org.jetbrains.sbt.settings.SbtSettings

abstract class SbtExternalSystemImportingTestCase extends ExternalSystemImportingTestCase {

  private var myProjectJdk: Sdk = _

  def getJdkConfiguredForTestCase: Sdk = myProjectJdk

  protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_11

  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def getTestsTempDir: String = "" // Use default temp directory

  override def setUp(): Unit = {
    super.setUp()

    myProjectJdk = SmartJDKLoader.getOrCreateJDK(projectJdkLanguageLevel)
    //output from sbt process is already printed (presumably somewhere from ExternalSystemImportingTestCase or internals)
    SbtStructureDump.printErrorsAndWarningsToConsoleDuringTests = false
  }

  override def tearDown(): Unit = {
    SbtStructureDump.printErrorsAndWarningsToConsoleDuringTests = true

    //jdk might be null if it was some exception in super.setup()
    if (myProjectJdk != null) {
      inWriteAction {
        val jdkTable = ProjectJdkTable.getInstance()
        jdkTable.removeJdk(myProjectJdk)
      }
    }

    super.tearDown()
  }

  private lazy val currentExternalProjectSettings: SbtProjectSettings = {
    val settings = new SbtProjectSettings
    settings.jdk = myProjectJdk.getName
    settings
  }

  override protected def getCurrentExternalProjectSettings: SbtProjectSettings = {
    currentExternalProjectSettings
  }

  protected final def getCurrentSbtProjectSettings: SbtProjectSettings = currentExternalProjectSettings

  protected def setSbtSettingsCustomSdk(sdk: Sdk): Unit = {
    val settings = SbtSettings.getInstance(myProject)
    settings.setCustomVMPath(sdk.getHomePath)
    settings.setCustomVMEnabled(true)
  }
}