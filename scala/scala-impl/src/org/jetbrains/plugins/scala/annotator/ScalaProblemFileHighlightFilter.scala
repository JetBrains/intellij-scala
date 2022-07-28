package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.annotator.ScalaProblemFileHighlightFilter._
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class ScalaProblemFileHighlightFilter(project: Project) extends Condition[VirtualFile] {
  /**
    * @see [[com.intellij.codeInsight.problems.DefaultProblemFileHighlightFilter]]
    */
  override def value(file: VirtualFile): Boolean =
    (isEnabled || ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) &&
      isScalaSourceFile(project, file) &&
      !CompilerManager.getInstance(project).isExcludedFromCompilation(file)

  private def isEnabled: Boolean =
    ScalaProjectSettings.getInstance(project).isProjectViewHighlighting
}

private object ScalaProblemFileHighlightFilter {
  /**
    * @see [[com.intellij.openapi.roots.FileIndexUtil]]
    */
  private def isScalaSourceFile(project: Project, file: VirtualFile): Boolean = {
    if (!file.isDirectory && file.getFileType == ScalaFileType.INSTANCE && !FileTypeManager.getInstance.isFileIgnored(file)) {
      val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
      fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) || fileIndex.isInLibrarySource(file)
    } else {
      false
    }
  }
}
