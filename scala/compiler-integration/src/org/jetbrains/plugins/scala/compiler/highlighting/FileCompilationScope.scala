package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.jps.incremental.scala.remote.SourceScope

private final case class FileCompilationScope(
  virtualFile: VirtualFile,
  module: Module,
  sourceScope: SourceScope,
  document: Document,
  psiFile: PsiFile
)
