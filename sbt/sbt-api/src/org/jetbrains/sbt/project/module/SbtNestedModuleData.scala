package org.jetbrains.sbt.project.module

import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.serialization.PropertyMapping
import org.jetbrains.sbt.project.SbtProjectSystem


/**
 * All subprojects of individual builds (except root projects) should be encapsulated in this data class.
 * By creating this class for subprojects of individual builds, it is possible to process them after the ModuleData classes (which are created for root projects within each build)
 * and adjust their name according to the root project name. <br>
 * Adjust the project name during the import is not enough, because IDEA is capable of renaming modules and in a situation where
 * the name of the root project would be changed, the subprojects would be left with incorrect names
 * (by incorrect I mean names which would not allow them to be displayed as a proper tree in <code>Project Structure</code>).
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
  val Key: Key[SbtNestedModuleData] =
    new Key(classOf[SbtNestedModuleData].getName, ProjectKeys.MODULE.getProcessingWeight + 1)
}
