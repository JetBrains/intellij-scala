package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus

import java.nio.file.Path

/**
 * A compatibility trait which exists to make sure that the new [[java.nio.file.Path]] based API is
 * abstract and must be overridden and to provide a default implementation for the old
 * [[java.io.File]] based API which will be removed in the future.
 */
trait ExternalSystemAutoImportAwareCompat extends ExternalSystemAutoImportAware:
  def getAffectedExternalProjectFilePaths(projectPath: String, project: Project): java.util.List[Path]

  @deprecated(message = "Deprecated in the platform. Use getAffectedExternalProjectFilePaths. This method will be removed.", since = "2026.2")
  @Deprecated(since = "2026.2", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.3")
  //noinspection SSBasedInspection
  final override def getAffectedExternalProjectFiles(projectPath: String, project: Project): java.util.List[java.io.File] =
    ContainerUtil.map(getAffectedExternalProjectFilePaths(projectPath, project), (p: Path) => p.toFile)
