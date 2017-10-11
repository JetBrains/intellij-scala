package org.jetbrains.plugins.cbt.structure

import java.io.File

import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.data.SbtEntityData


case class CbtModuleExtData(scalaVersion: Version,
                            scalacClasspath: Seq[File],
                            scalacOptions: Seq[String])

object CbtModuleExtData {
  val Key: Key[CbtModuleExtData] =
    SbtEntityData.datakey(classOf[CbtModuleExtData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
}
