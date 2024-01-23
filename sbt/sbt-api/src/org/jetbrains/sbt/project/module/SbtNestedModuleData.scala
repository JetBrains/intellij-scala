package org.jetbrains.sbt.project.module

import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.serialization.PropertyMapping
import org.jetbrains.sbt.project.SbtProjectSystem

final case class SbtNestedModuleData @PropertyMapping(Array(
  "id",
  "externalName",
  "moduleFileDirectoryPath",
  "externalConfigPath",
  "moduleTypeId"
))(
  id: String,
  externalName: String,
  moduleFileDirectoryPath: String,
  externalConfigPath: String,
  moduleTypeId: String
) extends ModuleData(
  id,
  SbtProjectSystem.Id,
  moduleTypeId,
  externalName,
  moduleFileDirectoryPath,
  externalConfigPath
) {
}
