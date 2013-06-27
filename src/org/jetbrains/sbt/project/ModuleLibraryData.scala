package org.jetbrains.sbt
package project

import java.io.File
import com.intellij.openapi.externalSystem.model.{Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.AbstractProjectEntityData
import com.intellij.openapi.roots.DependencyScope

/**
 * @author Pavel Fatin
 */
class ModuleLibraryData(val owner: ProjectSystemId, val name: String, val classes: Seq[File], val scope: DependencyScope)
  extends AbstractProjectEntityData(owner)

object ModuleLibraryData {
  val Key: Key[ModuleLibraryData] = new Key(classOf[ModuleLibraryData].getName)
}
