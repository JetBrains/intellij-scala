package org.jetbrains.plugins.scala.compiler.highlighting.core

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.jps.incremental.scala.remote.SourceScope

final case class FileCompilationScope(
  virtualFile: VirtualFile,
  module: Module,
  sourceScope: SourceScope,
  document: Document,
  psiFile: PsiFile
)

object FileCompilationScope {

  /** The source scope `virtualFile` belongs to in `project`. */
  def sourceScopeOf(project: Project, virtualFile: VirtualFile): SourceScope =
    if (TestSourcesFilter.isTestSources(virtualFile, project)) SourceScope.Test
    else SourceScope.Production
}
