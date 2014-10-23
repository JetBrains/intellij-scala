package org.jetbrains.sbt
package project.data

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys, ProjectSystemId}
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys.ParsedValue

/**
 * User: Dmitry.Naydanov
 * Date: 16.09.14.
 */
class Play2ProjectData(val owner: ProjectSystemId, val projectKeys: Map[String, Map[String, ParsedValue[_]]])
  extends AbstractExternalEntityData(owner)

object Play2ProjectData {
  val Key: Key[Play2ProjectData] = new Key(classOf[Play2ProjectData].getName,
    ProjectKeys.PROJECT.getProcessingWeight + 1)
}
