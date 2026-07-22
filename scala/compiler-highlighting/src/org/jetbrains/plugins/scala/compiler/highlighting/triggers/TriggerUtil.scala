package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.compiler.server.BuildManager
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{JavaProjectRootsUtil, ProjectRootManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiJavaFile}
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope.sourceScopeOf
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{CompilationRequest, DocumentRequest, IncrementalRequest, WorksheetRequest}
import org.jetbrains.plugins.scala.compiler.highlighting.services.{CompilerHighlightingService, SaveService}
import org.jetbrains.plugins.scala.extensions.{PsiFileExt, inReadAction, invokeLater}
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

  def doTriggerIncrementalCompilation(project: Project, debugReason: String, virtualFile: VirtualFile,
                                      document: Document, psiFile: PsiFile, request: RequestId): Boolean = {
    val module = moduleFor(project, virtualFile)
    if (module eq null) return false

    if (CompilerWorkspaceConfiguration.getInstance(project).MAKE_PROJECT_ON_SAVE) {
      invokeLater {
        SaveService(project).saveDocuments(request)
        BuildManager.getInstance().scheduleAutoMake()
      }
    } else {
      val scope = fileCompilationScope(project, virtualFile, module, document, psiFile)
      CompilerHighlightingService.get(project).requestCompilation(
        IncrementalRequest(Map(virtualFile -> scope),
          debugReason,
          CompilationRequest.compilationDeadline(project), request, project
        )
      )
    }
    true
  }

  def doTriggerDocumentCompilation(project: Project, virtualFile: VirtualFile, document: Document,
                                   psiFile: PsiFile, debugReason: String, requestId: RequestId): Boolean = {
    val module = moduleFor(project, virtualFile)
    if (module eq null) return false

    val scope = fileCompilationScope(project, virtualFile, module, document, psiFile)
    CompilerHighlightingService.get(project).requestCompilation(
      DocumentRequest(scope, debugReason, CompilationRequest.compilationDeadline(project), requestId, project)
    )
    true
  }

  def doTriggerWorksheetCompilation(project: Project, virtualFile: VirtualFile, psiFile: ScalaFile,
                                    document: Document, isFirstTime: Boolean, debugReason: String, request: RequestId): Boolean = {
    CompilerHighlightingService.get(project).requestCompilation(new WorksheetRequest(
      psiFile, virtualFile, document, isFirstTime, debugReason, CompilationRequest.compilationDeadline(project), request, project
    ))
    true
  }

  private def moduleFor(project: Project, virtualFile: VirtualFile): Module =
    inReadAction(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(virtualFile))

  private def fileCompilationScope(project: Project, virtualFile: VirtualFile, module: Module,
                                   document: Document, psiFile: PsiFile): FileCompilationScope =
    FileCompilationScope(virtualFile, module, sourceScopeOf(project, virtualFile), document, psiFile)
}