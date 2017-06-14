package org.jetbrains.plugins.cbt.structure

import com.intellij.openapi.externalSystem.model.Key
import org.jetbrains.sbt.project.data.SbtEntityData

case class CbtProjectData()

object CbtProjectData {
  val Key: Key[CbtProjectData] = SbtEntityData.datakey(classOf[CbtProjectData])
}
