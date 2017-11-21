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

  @Nullable
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
  var enableDebugSbtShell: Boolean = true

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
    result.enableDebugSbtShell = enableDebugSbtShell
    result
  }

  override def setUseAutoImport(useAutoImport: Boolean): Unit =
    useOurOwnAutoImport = useAutoImport

  override def isUseAutoImport: Boolean =
    useOurOwnAutoImport
}

object SbtProjectSettings {
  def default: SbtProjectSettings =
    new SbtProjectSettings
}