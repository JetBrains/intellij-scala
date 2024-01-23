package org.jetbrains.sbt.project.module

import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.serialization.PropertyMapping
import org.jetbrains.sbt.project.SbtProjectSystem


/**
 * This data is needed for sbt projects in which sbt build references other builds.
 * In such case all subprojects of individual builds (except root projects) should be encapsulated in this data class.
 * By creating this class for subprojects of individual builds, it is possible to process them after the ModuleData classes (which are created for root projects within each build)
 * and give them a correct prefix according to the root module name.
 *
 * @param id unique project id
 * @param externalName module external name
 * @param moduleFileDirectoryPath path to the directory in which .iml file for module is stored
 * @param externalConfigPath module path
 * @param moduleTypeId module type id
 */
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
) { }

object SbtNestedModuleData {
  val key: Key[SbtNestedModuleData] =
    new Key(classOf[SbtNestedModuleData].getName, ProjectKeys.MODULE.getProcessingWeight + 1)
}
