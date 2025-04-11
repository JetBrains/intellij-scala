package org.jetbrains.sbt.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nullable
import org.jetbrains.sbt.project.settings.SbtProjectSettings.canonical
import org.jetbrains.sbt.settings.SbtSettings

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.CollectionHasAsScala

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
  @BeanProperty
  var useSeparateCompilerOutputPaths: Boolean = false

  /**
   * Represents whether [[SbtProjectSettings.separateProdAndTestSources]] setting was explicitly configured either through
   * user interaction or system configuration (e.g., during New Project Wizard initialization)
   */
  @BeanProperty
  var separateProdAndTestSourcesIsExplicit: Boolean = false

  /**
   * IMPORTANT: Don't change the default value directly. If there is a need to manipulate its value to modify:
   *  - [[SbtProjectSettings.DefaultSeparateProdAndTestSources]]
   *  - [[org.jetbrains.sbt.project.SbtProjectManagerListener.execute]]
   * */
  @BeanProperty
  var separateProdAndTestSources: Boolean = true

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
    result.useSeparateCompilerOutputPaths = useSeparateCompilerOutputPaths
    result.separateProdAndTestSources = separateProdAndTestSources
    result.separateProdAndTestSourcesIsExplicit = separateProdAndTestSourcesIsExplicit
    result
  }
}

object SbtProjectSettings {
  /**
   * The default value for separate main and test modules setting.
   * This constant allows the default value of [[SbtProjectSettings.separateProdAndTestSources]] to be adjusted programmatically in a more controlled manner.
   *
   * This value is effectively used for:
   *  - projects where [[SbtProjectSettings.separateProdAndTestSources]] was not explicit
   * (see [[org.jetbrains.sbt.project.SbtProjectManagerListener.execute]])
   *  - new projects, except those created via New Project Wizards where the setting is always enabled
   */
  val DefaultSeparateProdAndTestSources = true
  // Increment if the converter algorithm is updated to trigger a reloading of previously opened projects.
  val ConverterVersion = 2

  def default: SbtProjectSettings = {
    val settings = new SbtProjectSettings()
    settings.separateProdAndTestSources = DefaultSeparateProdAndTestSources
    settings.converterVersion = ConverterVersion
    settings
  }

  /**
   * Create a [[SbtProjectSettings]] used in the NPWs
   */
  def defaultForNewProjectWizard: SbtProjectSettings = {
    val settings = new SbtProjectSettings()
    settings.converterVersion = ConverterVersion
    // Prevent the algorithm in org.jetbrains.sbt.project.SbtProjectManagerListener.execute
    // from overriding the explicitly set value
    settings.separateProdAndTestSourcesIsExplicit = true
    settings.separateProdAndTestSources = true
    settings
  }

  def forProject(project: Project): Option[SbtProjectSettings] = {
    val settings = SbtSettings.getInstance(project)
    Option(project.getBasePath)
      .flatMap(path => Option(settings.getLinkedProjectSettings(path)))
  }

  def `for`(project: Project, externalRootPath: String): Option[SbtProjectSettings] = {
    val settings = SbtSettings.getInstance(project)
    Option(settings.getLinkedProjectSettings(externalRootPath))
  }

  def allForProject(project: Project): Seq[SbtProjectSettings] = {
    val settings = SbtSettings.getInstance(project)
    settings.getLinkedProjectsSettings.asScala.toSeq
  }

  private def canonical(path: String) =
    Option(path).map(ExternalSystemApiUtil.toCanonicalPath).orNull
}