package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import org.jetbrains.annotations.Nullable

import scala.beans.BeanProperty

/**
 * @author Pavel Fatin
 */
class SbtProjectSettings extends ExternalProjectSettings {
  def jdkName: Option[String] = Option(jdk)

  def jdkName_=(name: Option[String]) = jdk = name.orNull

  @Nullable
  @BeanProperty
  var jdk: String = null

  @BeanProperty
  var resolveClassifiers: Boolean = false

  @BeanProperty
  var resolveSbtClassifiers: Boolean = false

  @Nullable
  @BeanProperty
  var sbtVersion: String = null

  @BeanProperty
  var useOurOwnAutoImport: Boolean = false

  override def clone() = {
    val result = new SbtProjectSettings()
    copyTo(result)
    result.jdk = jdk
    result.resolveClassifiers = resolveClassifiers
    result.resolveSbtClassifiers = resolveSbtClassifiers
    result.sbtVersion = sbtVersion
    result.useOurOwnAutoImport = useOurOwnAutoImport
    result
  }
}

object SbtProjectSettings {
  def default: SbtProjectSettings =
    new SbtProjectSettings
}