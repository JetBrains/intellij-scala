package org.jetbrains.sbt
package project

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemConfigLocator
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 2/16/15.
 */
class SbtConfigLocator extends ExternalSystemConfigLocator {
  override def getTargetExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def findAll(externalProjectSettings: ExternalProjectSettings): util.List[VirtualFile] = {
    val modules = externalProjectSettings.getModules.asScala
    modules.foldLeft(List.empty[VirtualFile]) { (acc, path) =>
      val root = Option(LocalFileSystem.getInstance.refreshAndFindFileByIoFile(new File(path)))
      root.safeMap(adjust).map(acc :+ _).getOrElse(acc)
    }.asJava
  }

  override def adjust(configPath: VirtualFile): VirtualFile = {
    val buildSbt   = configPath.find("build.sbt")
    val buildScala = configPath.find("project").flatMap(_.find("Build.scala"))
    buildSbt.orElse(buildScala).orNull
  }
}
