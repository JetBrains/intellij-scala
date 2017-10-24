package org.jetbrains.plugins.cbt.project.settings

import java.util

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.project.Project

import scala.beans.BeanProperty

class CbtProjectSettings extends ExternalProjectSettings {
  super.setUseAutoImport(true)

  @BeanProperty
  var isCbt = false

  @BeanProperty
  var useCbtForInternalTasks = true

  @BeanProperty
  var useDirect: Boolean = false

  @BeanProperty
  var extraModules: java.util.List[String] = new util.ArrayList[String]()

  override def clone(): CbtProjectSettings = {
    val result = new CbtProjectSettings()
    copyTo(result)
    result.isCbt = isCbt
    result.extraModules = extraModules
    result.useCbtForInternalTasks = useCbtForInternalTasks
    result.useDirect = useDirect
    result
  }
}

object CbtProjectSettings {
  def getInstance(project: Project, path: String): CbtProjectSettings = {
    val sysetemSettings = CbtSystemSettings.instance(project)
    Option(sysetemSettings.getLinkedProjectSettings(path)).getOrElse(CbtProjectSettings.default)
  }

  def default: CbtProjectSettings = new CbtProjectSettings
}

