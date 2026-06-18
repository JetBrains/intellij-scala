package org.jetbrains.sbt.project

import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.util.RevertableChange
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings

import java.nio.file.{Files, Path}

object SbtProjectImportTestUtils {

  /**
   * Replaces all occurrences of `variableName` in `file` with `value`.
   *
   * Example:
   * {{{
   *   // project/build.properties (before):
   *   // sbt.version=$SBT_VERSION$
   *
   *   SbtProjectImportTestUtils.injectVariable(
   *     file = projectRoot.resolve("project/build.properties"),
   *     variableName = "$SBT_VERSION$",
   *     value = "1.11.7"
   *   )
   *
   *   // project/build.properties (after):
   *   // sbt.version=1.11.7
   * }}}
   */
  def injectVariable(file: Path, variableName: String, value: String): Unit = {
    val fileContent = Files.readString(file)
    val updatedContent = fileContent.replace(variableName, value)
    Files.writeString(file, updatedContent)
  }

  def suppressSbtStructureDumpErrorAndWarningConsoleOutput(testCase: UsefulTestCase): Unit = {
    // See org.jetbrains.sbt.project.structure.SbtStructureDump.dontPrintErrorsAndWarningsToConsoleDuringTests.
    // The sbt process output is already printed by the external-system import test infrastructure.
    RevertableChange
      .withModifiedSystemProperty("sbt.structure.dump.dontPrintErrorsAndWarningsToConsoleDuringTests", "true")
      .applyChange(testCase)
  }

  /**
   * It is necessary to explicitly set all project settings that are tested/required for test, because what is set in
   * #setUp method in each SbtProjectStructureImportingTest classes is not applied to the project settings of the linked project
   */
  def linkSbtProjectWithNewSettingsToProject(
    project: Project,
    externalProjectPath: String,
    prodTestSourcesSeparated: Boolean,
    jdkName: String
  ): Unit = {
    val settings = createSbtProjectSettingsForLinkedProject(
      externalProjectPath = externalProjectPath,
      prodTestSourcesSeparated = prodTestSourcesSeparated,
      jdkName = jdkName
    )

    SbtSettings.getInstance(project).linkProject(settings)
  }

  private def createSbtProjectSettingsForLinkedProject(
    externalProjectPath: String,
    prodTestSourcesSeparated: Boolean,
    jdkName: String
  ): SbtProjectSettings = {
    val settings = new SbtProjectSettings
    settings.jdk = jdkName
    settings.setExternalProjectPath(externalProjectPath)
    settings.setSeparateProdAndTestSources(prodTestSourcesSeparated)
    settings
  }
}
