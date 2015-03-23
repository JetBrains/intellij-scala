package org.jetbrains.sbt
package project

import java.io.File

import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore._

/**
 * @author Pavel Fatin
 */
class SbtAutoImport extends ExternalSystemAutoImportAware {
  def getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project) = null

  def getRealAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project) = {
    val changed = new File(changedFileOrDirPath)
    val name = changed.getName

    val base = new File(project.getBasePath)
    val build = base / Sbt.ProjectDirectory

    (name.endsWith(s".${Sbt.FileExtension}") && isAncestor(base, changed, true) ||
      name.endsWith(".scala") && isAncestor(build, changed, true))
      .option(base.canonicalPath).orNull
  }
}
