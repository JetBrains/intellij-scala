package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.*

import java.nio.file.Path
import scala.jdk.CollectionConverters.*

trait AutoImportAwareness extends ExternalSystemAutoImportAware {
  override final def getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project): String =
    if (isProjectDefinitionFile(project, changedFileOrDirPath)) project.getBasePath
    else null

  /**
   * @note This method only works in the local filesystem. This method is not safe with eel paths, e.g., WSL.
   */
  override def getAffectedExternalProjectFiles(@NonNls projectPath: String, project: Project): java.util.List[java.io.File] = {
    val baseDir = Path.of(projectPath)
    val projectDir = baseDir / Sbt.ProjectDirectory

    val files: Seq[Path] =
      baseDir / Sbt.BuildFile +:
        projectDir / Sbt.PropertiesFile +:
        projectDir.ls(name => name.endsWith(Sbt.Extension) || name.endsWith(".scala"))

    files.map(_.toFile).asJava
  }

  private def isProjectDefinitionFile(project: Project, changedFileOrDirPath: String): Boolean = {
    val baseDir = Path.of(project.getBasePath)
    val projectDir = baseDir / Sbt.ProjectDirectory
    val file = Path.of(changedFileOrDirPath)

    val fileName = file.getFileName.toString

    fileName == Sbt.BuildFile && file.isIn(baseDir) ||
      fileName == Sbt.PropertiesFile && file.isIn(projectDir) ||
      fileName.endsWith(Sbt.Extension) && file.isIn(projectDir) ||
      fileName.endsWith(".scala") && file.isIn(projectDir)
  }

  extension (path: Path)
    private def isIn(directory: Path): Boolean =
      path.getParent == directory

    private def ls(p: String => Boolean): Seq[Path] =
      if path.isDirectory then
        path.children().collect:
          case f if p(f.getFileName.toString) => f
      else
        Seq.empty

  end extension
}
