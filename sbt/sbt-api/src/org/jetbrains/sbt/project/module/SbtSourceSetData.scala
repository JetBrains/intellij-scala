package org.jetbrains.sbt.project.module

import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.serialization.PropertyMapping
import org.jetbrains.sbt.project.SbtProjectSystem

/**
 * This data class was created to represent main and test modules.
 * Main and test modules might be located both inside ModuleData modules (cause root project inside each build also contains main and test sources) and SbtNestedModuleData modules.
 * The reason for the creation of this data class is the same as it was for SbtNestedModuleData - adjusting proper module names.
 * See [[org.jetbrains.sbt.project.module.SbtNestedModuleData]] for more details
 *
 * @param id unique project id
 * @param externalName module external name
 * @param moduleFileDirectoryPath path to the directory in which .iml file for module is stored
 * @param externalConfigPath module path
 * @param moduleTypeId module type id
 */
final case class SbtSourceSetData @PropertyMapping(Array(
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
) { }

object SbtSourceSetData {
  val Key: Key[SbtSourceSetData] =
    new Key(classOf[SbtSourceSetData].getName, ProjectKeys.MODULE.getProcessingWeight + 2)
}
