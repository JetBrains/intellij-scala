package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import com.intellij.serialization.PropertyMapping
import org.jetbrains.sbt.RichSeq

import java.util.{List => JList}

/**
 * Data storing external modules ids that own the given shared sources module.
 * This data is only put in shared sources modules.
 *
 * @param ownerModuleIds id of the modules that own shared sources module
 */
case class SharedSourcesOwnersData @PropertyMapping(Array("ownerModuleIds"))(
  ownerModuleIds: JList[String],
) extends AbstractExternalEntityData(SbtProjectSystem.Id) with Product

object SharedSourcesOwnersData {
  val Key: Key[SharedSourcesOwnersData] = new Key(classOf[SharedSourcesOwnersData].getName,  ProjectKeys.MODULE.getProcessingWeight + 2)

  def apply(owners: Seq[String]): SharedSourcesOwnersData =
    SharedSourcesOwnersData(owners.toJavaList)
}