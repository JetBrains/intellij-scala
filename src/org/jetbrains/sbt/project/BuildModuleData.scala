package org.jetbrains.sbt
package project

import java.io.File
import com.intellij.openapi.externalSystem.model.{Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.{ModuleData, AbstractProjectEntityData}
import com.intellij.openapi.module.StdModuleTypes

/**
 * @author Pavel Fatin
 */
class BuildModuleData(val owner: ProjectSystemId, val name: String, val path: String,
                      val sourceDirs: Seq[File], val excludedDirs: Seq[File], val classpath: Seq[File])
  extends ModuleData(owner, StdModuleTypes.JAVA.getId, name, path)

object BuildModuleData {
  val Key: Key[BuildModuleData] = new Key(classOf[BuildModuleData].getName)
}
