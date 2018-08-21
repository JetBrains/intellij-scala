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

/**
 * @author Pavel Fatin
 */
class SbtProjectSettings extends ExternalProjectSettings {

  super.setUseAutoImport(false)

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
  @deprecated(message = "use separate import/build settings", "2018.2")
  var useSbtShell: Boolean = false

  @BeanProperty
  var useSbtShellForImport: Boolean = false

  @BeanProperty
  var useSbtShellForBuild: Boolean = false

  @BeanProperty
  var enableDebugSbtShell: Boolean = true

  @BeanProperty
  var allowSbtVersionOverride = true

  @Nullable
  @BeanProperty
  var sbtVersion: String = null

  def buildWithShell: Boolean = useSbtShellForBuild || useSbtShell

  def importWithShell: Boolean = useSbtShellForImport || useSbtShell

  override def clone(): SbtProjectSettings = {
    val result = new SbtProjectSettings()
    copyTo(result)
    result.jdk = jdk
    result.resolveClassifiers = resolveClassifiers
    result.resolveJavadocs = resolveJavadocs
    result.resolveSbtClassifiers = resolveSbtClassifiers
    result.sbtVersion = sbtVersion
    result.useSbtShell = useSbtShell
    result.useSbtShellForImport = useSbtShellForImport
    result.useSbtShellForBuild = useSbtShellForBuild
    result.enableDebugSbtShell = enableDebugSbtShell
    result.allowSbtVersionOverride = allowSbtVersionOverride
    result
  }
}

object SbtProjectSettings {

  def default: SbtProjectSettings =
    new SbtProjectSettings

  def forProject(project: Project): SbtProjectSettings = {
    val settings = SbtSettings.getInstance(project)
    settings.getLinkedProjectSettings(project.getBasePath)
  }

  private def canonical(path: String) =
    path.toOption.map(ExternalSystemApiUtil.toCanonicalPath).orNull
}