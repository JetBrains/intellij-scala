package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.sbt.project.settings.SbtProjectSettings.canonical
import org.jetbrains.sbt.settings.SbtSettings

import scala.beans.BeanProperty

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

  def jdkName: Option[String] = Option(jdk)

  override def getExternalProjectPath: String =
    canonical(super.getExternalProjectPath)

  override def setExternalProjectPath(externalProjectPath: String): Unit =
    super.setExternalProjectPath(canonical(externalProjectPath))

  @Nullable
  var jdk: String = null

  @BeanProperty
  var resolveClassifiers: Boolean = true

  @BeanProperty
  var resolveSbtClassifiers: Boolean = false

  @BeanProperty
  var resolveJavadocs: Boolean = false

  @BeanProperty
  var useSbtShellForImport: Boolean = false

  @BeanProperty
  var useSbtShellForBuild: Boolean = false

  @BeanProperty
  var enableDebugSbtShell: Boolean = false

  @BeanProperty
  var allowSbtVersionOverride = false

  @BeanProperty
  var preferScala2 = true

  @Nullable
  @BeanProperty
  var sbtVersion: String = _

  def buildWithShell: Boolean = useSbtShellForBuild

  def importWithShell: Boolean = useSbtShellForImport

  override def clone(): SbtProjectSettings = {
    val result = new SbtProjectSettings()
    copyTo(result)
    result.converterVersion = converterVersion
    result.jdk = jdk
    result.resolveClassifiers = resolveClassifiers
    result.resolveJavadocs = resolveJavadocs
    result.resolveSbtClassifiers = resolveSbtClassifiers
    result.sbtVersion = sbtVersion
    result.useSbtShellForImport = useSbtShellForImport
    result.useSbtShellForBuild = useSbtShellForBuild
    result.enableDebugSbtShell = enableDebugSbtShell
    result.allowSbtVersionOverride = allowSbtVersionOverride
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
    path.toOption.map(ExternalSystemApiUtil.toCanonicalPath).orNull
}