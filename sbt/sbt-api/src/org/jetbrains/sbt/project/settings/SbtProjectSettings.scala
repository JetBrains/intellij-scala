package org.jetbrains.sbt.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nullable
import org.jetbrains.sbt.project.settings.SbtProjectSettings.canonical
import org.jetbrains.sbt.settings.SbtSettings

import scala.beans.BeanProperty

/**
 * Represents multiple kinds of SBT settings: (per single imported sbt project)
 *  - settings which are used during project import/reload
 *  - sbt shell settings
 *  - other project-level values imported from SBT
 *
 * Most of these settings are displayed in `Settings | Build, Execution, Deployment | Build Tools | sbt`<br>
 * in `sbt Projects` subsection
 *
 * @see [[org.jetbrains.sbt.project.settings.SbtProjectSettingsControl]]<br>
 *      [[org.jetbrains.sbt.project.settings.SbtExtraControls]]<br>
 *      (UI for current settings)
 * @see [[org.jetbrains.sbt.settings.SbtSettings]]
 */
//noinspection ConvertNullInitializerToUnderscore
class SbtProjectSettings extends ExternalProjectSettings {

  /**
   * Like "stub version", but for the converter algorithm.
   *
   * IDEA will automatically reload the project on opening if the "Reload project after changes in the build scripts" setting is enabled.
   * However, this happens only if the project files are updated, not if the converter algorithm is updated.
   * We store the actually used algorithm version and trigger a project reload if the version is updated.
   */
  @BeanProperty
  var converterVersion: Int = 0

  override def getExternalProjectPath: String =
    canonical(super.getExternalProjectPath)

  override def setExternalProjectPath(externalProjectPath: String): Unit =
    super.setExternalProjectPath(canonical(externalProjectPath))

  def jdkName: Option[String] = Option(jdk)

  //////////////////////////////////////////
  // SETTINGS SECTION START
  //////////////////////////////////////////

  /**
   * Represents Project JDK when in project wizard.<b>
   *
   * @note this setting is only used during initial project import via "Import Project From Existing Sources" action
   *       (`File | New | Project from Existing Sources...`)<br>
   * @note it's note the same as [[SbtSettings.customVMPath]], which specifies VM whichc is used to start SBT process
   * @see [[org.jetbrains.sbt.project.settings.SbtProjectSettingsControl.fillExtraControls]]
   */
  @Nullable
  var jdk: String = null

  //Settings used during SBT project import/reload
  @BeanProperty var resolveClassifiers: Boolean = true
  @BeanProperty var resolveSbtClassifiers: Boolean = false
  @BeanProperty var preferScala2 = true
  //See SCL-21694 for details
  //TODO if by ~2024.2 release we don't observe any major issues related to this new grouping logic we can remove the setting completely (with all the code using it)
  @BeanProperty var groupProjectsFromSameBuild = true
  //See SCL-21158 for details
  //This should be in sync with what is used as a default value in
  // org.jetbrains.jps.incremental.scala.model.impl.JpsSbtDependenciesEnumerationHandler.shouldProcessDependenciesRecursively
  @BeanProperty
  var insertProjectTransitiveDependencies: Boolean = true
  @BeanProperty
  var useSeparateCompilerOutputPaths: Boolean = false

  //SBT shell settings
  @BeanProperty var useSbtShellForImport: Boolean = false
  @BeanProperty var useSbtShellForBuild: Boolean = false
  @BeanProperty var enableDebugSbtShell: Boolean = false

  //Other project-level values imported from SBT
  /**
   * This setting is displayed in `Project Structure | Modules` in `*-build` modules in `sbt` tab<br>
   * (see [[org.jetbrains.sbt.project.module.SbtModuleSettingsEditor]]))
   */
  @Nullable
  @BeanProperty var sbtVersion: String = _

  //////////////////////////////////////////
  // SETTINGS SECTION END
  //////////////////////////////////////////

  def buildWithShell: Boolean = useSbtShellForBuild

  def importWithShell: Boolean = useSbtShellForImport

  override def clone(): SbtProjectSettings = {
    val result = new SbtProjectSettings()
    copyTo(result)
    result.converterVersion = converterVersion
    result.jdk = jdk
    result.resolveClassifiers = resolveClassifiers
    result.resolveSbtClassifiers = resolveSbtClassifiers
    result.sbtVersion = sbtVersion
    result.useSbtShellForImport = useSbtShellForImport
    result.useSbtShellForBuild = useSbtShellForBuild
    result.enableDebugSbtShell = enableDebugSbtShell
    result.preferScala2 = preferScala2
    result.groupProjectsFromSameBuild = groupProjectsFromSameBuild
    result.insertProjectTransitiveDependencies = insertProjectTransitiveDependencies
    result.useSeparateCompilerOutputPaths = useSeparateCompilerOutputPaths
    result
  }
}

object SbtProjectSettings {
  // Increment if the converter algorithm is updated to trigger a reloading of previously opened projects.
  val ConverterVersion = 1

  def default: SbtProjectSettings = {
    val settings = new SbtProjectSettings()
    settings.converterVersion = ConverterVersion
    settings
  }

  def forProject(project: Project): Option[SbtProjectSettings] = {
    val settings = SbtSettings.getInstance(project)
    Option(settings.getLinkedProjectSettings(project.getBasePath))
  }

  private def canonical(path: String) =
    Option(path).map(ExternalSystemApiUtil.toCanonicalPath).orNull
}