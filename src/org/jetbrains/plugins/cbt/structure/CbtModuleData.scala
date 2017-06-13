package org.jetbrains.plugins.cbt.structure

import java.io.File

import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import org.jetbrains.sbt.project.data.SbtEntityData

case class CbtModuleData(scalacClasspath: Seq[File])

object CbtModuleData {
  val Key: Key[CbtModuleData] =
    SbtEntityData.datakey(classOf[CbtModuleData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
}
