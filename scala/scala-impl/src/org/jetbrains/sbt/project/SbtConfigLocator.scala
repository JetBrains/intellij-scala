package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemConfigLocator
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}

import java.io.File
import java.util
import scala.jdk.CollectionConverters._

class SbtConfigLocator extends ExternalSystemConfigLocator {
  override def getTargetExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def findAll(externalProjectSettings: ExternalProjectSettings): util.List[VirtualFile] = {
    val modules = externalProjectSettings.getModules.asScala
    modules.flatMap { path =>
      Option(LocalFileSystem.getInstance.refreshAndFindFileByIoFile(new File(path))).safeMap(adjust)
    }.toList.asJava
  }

  override def adjust(configPath: VirtualFile): VirtualFile = {
    // FIXME this is not a comprehensive sbt config location
    val buildSbt = configPath.find(Sbt.BuildFile)
    val buildScala = configPath.find(Sbt.ProjectDirectory).flatMap(_.find("Build.scala"))
    buildSbt.orElse(buildScala).orNull
  }
}
