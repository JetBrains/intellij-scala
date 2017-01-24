package org.jetbrains.sbt.project.data

import java.net.URI

import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData

/**
  * Created by jast on 2016-12-12.
  */
case class SbtModuleData(owner: ProjectSystemId, id: String, buildURI: URI)
  extends AbstractExternalEntityData(owner)

object SbtModuleData {
  val Key: Key[SbtModuleData] =
    new Key(classOf[SbtModuleData].getName,
            ProjectKeys.MODULE.getProcessingWeight + 1)
}
