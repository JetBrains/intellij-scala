package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{JavaProjectRootsUtil, ProjectRootManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiJavaFile}
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope.sourceScopeOf
import org.jetbrains.plugins.scala.extensions.{PsiFileExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ScalaHighlightingMode}

private[highlighting] object TriggerUtil {

  def isHighlightingEnabled: Boolean =
    !PowerSaveMode.isEnabled && ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED

  def isHighlightingEnabledFor(psiFile: PsiFile, virtualFile: VirtualFile, project: Project): Boolean = inReadAction {
    ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile) &&
      virtualFile.isInLocalFileSystem &&
      (psiFile match {
        case _ if psiFile.isScalaWorksheet => true
        case _: ScalaFile | _: PsiJavaFile if !JavaProjectRootsUtil.isOutsideJavaSourceRoot(psiFile) => true
        case _ => false
      }) &&
      ScalaHighlightingMode.shouldHighlightBasedOnFileLevel(psiFile, project)
  }

  def moduleFor(project: Project, virtualFile: VirtualFile): Module =
    inReadAction(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(virtualFile))

  def fileCompilationScope(project: Project, virtualFile: VirtualFile, module: Module,
                                   document: Document, psiFile: PsiFile): FileCompilationScope =
    FileCompilationScope(virtualFile, module, sourceScopeOf(project, virtualFile), document, psiFile)
}