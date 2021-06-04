package org.jetbrains.plugins.scala.packagesearch.configuration

import com.intellij.openapi.components.{BaseState, PersistentStateComponent, State, Storage}
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import org.jetbrains.plugins.scala.packagesearch.PackageSearchSbtBundle
import org.jetbrains.sbt.language.utils.SbtCommon

@State(
  name = "PackageSearchSbtConfiguration",
  storages = Array(new Storage(PackageSearchGeneralConfiguration.StorageFileName))
)
class PackageSearchSbtConfiguration extends BaseState with PersistentStateComponent[PackageSearchSbtConfiguration] {
  override def getState: PackageSearchSbtConfiguration = this

  override def loadState(state: PackageSearchSbtConfiguration): Unit = this.copyFrom(state)

  @OptionTag("SBT_CONFIGURATIONS_DEFAULT")
  var defaultSbtScope: String = SbtCommon.defaultLibScope

  def determineDefaultSbtConfiguration: String = {
    if (defaultSbtScope != null && defaultSbtScope.nonEmpty) defaultSbtScope
    else SbtCommon.defaultLibScope
  }

  def getSbtConfigurations: Array[String] = SbtCommon.libScopes.split(',').map(_.trim).filter(_.nonEmpty)
}

object packageSearchSbtConfigurationForProject {
  def getService(project: Project): PackageSearchSbtConfiguration = project.getService(classOf[PackageSearchSbtConfiguration])
}