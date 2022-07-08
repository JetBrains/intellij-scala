package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls
import org.jetbrains.sbt._
import org.jetbrains.sbt.project.AutoImportAwareness._

import java.io.File
import java.util
import scala.jdk.CollectionConverters._

trait AutoImportAwareness extends ExternalSystemAutoImportAware {
  override final def getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project): String =
    if (isProjectDefinitionFile(project, new File(changedFileOrDirPath))) project.getBasePath
    else null

  override def getAffectedExternalProjectFiles(@NonNls projectPath: String, project: Project): util.List[File] = {
    val baseDir = new File(projectPath)
    val projectDir = baseDir / Sbt.ProjectDirectory

    val files =
      baseDir / Sbt.BuildFile +:
        projectDir / Sbt.PropertiesFile +:
        projectDir.ls(name => name.endsWith(Sbt.Extension) || name.endsWith(".scala"))

    files.asJava
  }
}

private object AutoImportAwareness {
  def isProjectDefinitionFile(project: Project, file: File): Boolean = {
    val baseDir = new File(project.getBasePath)
    val projectDir = baseDir / Sbt.ProjectDirectory

    val fileName = file.getName

    fileName == Sbt.BuildFile && file.isIn(baseDir) ||
      fileName == Sbt.PropertiesFile && file.isIn(projectDir) ||
      fileName.endsWith(Sbt.Extension) && file.isIn(projectDir) ||
      fileName.endsWith(".scala") && file.isIn(projectDir)
  }
}
