package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import org.jetbrains.annotations.Nullable

import scala.beans.BeanProperty

/**
 * @author Pavel Fatin
 */
class SbtProjectSettings extends ExternalProjectSettings {

  super.setUseAutoImport(false)

  def jdkName: Option[String] = Option(jdk)

  def jdkName_=(name: Option[String]): Unit = jdk = name.orNull

  @Nullable
  @BeanProperty
  var jdk: String = null

  @BeanProperty
  var resolveClassifiers: Boolean = true

  @BeanProperty
  var resolveSbtClassifiers: Boolean = false

  @BeanProperty
  var resolveJavadocs: Boolean = false

  @BeanProperty
  var useSbtShell: Boolean = false

  @BeanProperty
  var useRemoteDebugSbtShell: Boolean = false

  @Nullable
  @BeanProperty
  var remoteDebugPort: String = null

  @Nullable
  @BeanProperty
  var sbtVersion: String = null

  @BeanProperty
  var useOurOwnAutoImport: Boolean = false

  override def clone(): SbtProjectSettings = {
    val result = new SbtProjectSettings()
    copyTo(result)
    result.jdk = jdk
    result.resolveClassifiers = resolveClassifiers
    result.resolveJavadocs = resolveJavadocs
    result.resolveSbtClassifiers = resolveSbtClassifiers
    result.sbtVersion = sbtVersion
    result.useSbtShell = useSbtShell
    result.useOurOwnAutoImport = useOurOwnAutoImport
    result.useRemoteDebugSbtShell = useRemoteDebugSbtShell
    result.remoteDebugPort = remoteDebugPort
    result
  }

  override def setUseAutoImport(useAutoImport: Boolean): Unit =
    useOurOwnAutoImport = useAutoImport

  override def isUseAutoImport: Boolean =
    useOurOwnAutoImport

  def extractRemoteDebugPort: Option[Int] =
    if (useRemoteDebugSbtShell) {
      try {
        Some(Integer.parseInt(remoteDebugPort))
      } catch {
        case e: NumberFormatException => None
      }
    } else None
}

object SbtProjectSettings {
  def default: SbtProjectSettings =
    new SbtProjectSettings
}