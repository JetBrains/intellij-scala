package org.jetbrains.sbt.project.template

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings

@ApiStatus.Experimental
abstract class SbtModuleBuilderBase extends ModuleBuilderBase[SbtProjectSettings](
  SbtProjectSystem.Id,
  SbtProjectSettings.default
) {
  override protected def externalSystemConfigFile: String = Sbt.BuildFile
}
