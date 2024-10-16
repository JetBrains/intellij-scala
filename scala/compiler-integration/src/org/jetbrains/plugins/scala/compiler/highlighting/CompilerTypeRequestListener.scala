package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.compiler.highlighting.CompilationRequest.DocumentRequest
import org.jetbrains.plugins.scala.lang.psi.impl.CompilerType
import org.jetbrains.plugins.scala.project.ProjectPsiFileExt

class CompilerTypeRequestListener(project: Project) extends CompilerType.Listener {
  def onCompilerTypeRequest(e: PsiElement): Unit = {
    if (project.isDisposed) return

    val psiFile = e.getContainingFile
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
    val module = psiFile.module.getOrElse(throw new IllegalArgumentException("The containing file must belong to a module"))
    val virtualFile = psiFile.getVirtualFile
    val scope = if (ProjectFileIndex.getInstance(project).isInSource(virtualFile)) SourceScope.Production else SourceScope.Test

    val request = DocumentRequest(FileCompilationScope(virtualFile, module, scope, document, psiFile), "compiler type request", System.nanoTime())
    CompilerHighlightingService.get(project).executeDocumentCompilationRequest(request)
  }
}
